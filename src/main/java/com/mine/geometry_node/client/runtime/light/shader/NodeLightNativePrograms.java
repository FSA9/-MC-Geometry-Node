package com.mine.geometry_node.client.runtime.light.shader;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Exact linked-program reuse for native receivers, with per-pipeline startup timings. */
public final class NodeLightNativePrograms {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ThreadLocal<Build> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Path> LINK = new ThreadLocal<>();
    private static final Map<String, Map<String, Object>> LAST = new LinkedHashMap<>();
    private static Constructor<GlProgram> constructor;
    private static String implementation;
    private static boolean warned;

    private static final class Build {
        final String name;
        final long start = System.nanoTime();
        long vertex, fragment, link, keyAndLoad, actualLink, binaryExtraction;
        boolean lights;
        String cache = "NOT_APPLICABLE";
        String cacheFailure = "";
        Build(String name) { this.name = name; }
    }

    private NodeLightNativePrograms() {}

    public static GlProgram pipeline(String name, Supplier<GlProgram> compile) {
        Build previous = CURRENT.get(), build = new Build(name);
        CURRENT.set(build);
        try {
            return compile.get();
        } finally {
            CURRENT.set(previous);
            double total = (System.nanoTime() - build.start) / 1_000_000.0;
            synchronized (LAST) {
                LAST.remove(name);
                LAST.put(name, Map.of("totalMs", total, "vertexMs", build.vertex / 1_000_000.0,
                        "fragmentMs", build.fragment / 1_000_000.0, "linkOrLoadMs", build.link / 1_000_000.0,
                        "nodeLights", build.lights, "binaryCache", build.cache,
                        "keyAndLoadMs", build.keyAndLoad / 1_000_000.0, "linkMs", build.actualLink / 1_000_000.0,
                        "binaryExtractionMs", build.binaryExtraction / 1_000_000.0, "cacheFailure", build.cacheFailure));
                while (LAST.size() > 128) LAST.remove(LAST.keySet().iterator().next());
            }
            if (total >= 250 || build.cache.equals("HIT")) LOGGER.info(
                    "Native shader build {}: total={} ms, vertex={} ms, fragment={} ms, link/load={} ms, key/load={} ms, link={} ms, binary extraction={} ms, nodeLights={}, binaryCache={}, cacheFailure={}",
                    name, Math.round(total), build.vertex / 1_000_000, build.fragment / 1_000_000,
                    build.link / 1_000_000, build.keyAndLoad / 1_000_000, build.actualLink / 1_000_000,
                    build.binaryExtraction / 1_000_000, build.lights, build.cache, build.cacheFailure);
        }
    }

    public static void shader(ShaderType type, long nanos) {
        Build build = CURRENT.get();
        if (build == null) return;
        if (type == ShaderType.VERTEX) build.vertex += nanos;
        else build.fragment += nanos;
    }

    public static GlProgram link(GlShaderModule vertex, GlShaderModule fragment, VertexFormat format,
                                 String name, Supplier<GlProgram> compile) {
        long start = System.nanoTime();
        Path previous = LINK.get(), path = null;
        Build build = CURRENT.get();
        int loaded = 0;
        try {
            long lookupStart = System.nanoTime();
            try {
                String fs = GL20C.glGetShaderSource(fragment.getShaderId());
                if (fs.contains("uniform int nodeLightCount;")) {
                    if (build != null) build.lights = true;
                    try {
                        resolveConstructor();
                        path = NodeLightProgramCache.path("native-receiver-v1", implementation,
                                format.getElementAttributeNames().toString(),
                                GL20C.glGetShaderSource(vertex.getShaderId()), fs);
                        loaded = NodeLightProgramCache.load(path);
                        if (build != null) {
                            build.cache = NodeLightProgramCache.loadStatus();
                            build.cacheFailure = NodeLightProgramCache.loadFailure();
                        }
                        if (loaded != 0) return constructor.newInstance(loaded, name);
                    } catch (ReflectiveOperationException | RuntimeException | java.io.IOException
                             | java.security.NoSuchAlgorithmException error) {
                        if (loaded != 0) GL20C.glDeleteProgram(loaded);
                        path = null;
                        if (build != null) {
                            build.cache = "FALLBACK";
                            build.cacheFailure = error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());
                        }
                        if (!warned) {
                            warned = true;
                            LOGGER.warn("Native light program cache unavailable; using normal linking", error);
                        }
                    }
                }
            } finally {
                if (build != null) build.keyAndLoad += System.nanoTime() - lookupStart;
            }
            LINK.set(path);
            GlProgram program;
            long linkStart = System.nanoTime();
            try { program = compile.get(); }
            finally { if (build != null) build.actualLink += System.nanoTime() - linkStart; }
            if (path != null && program != GlProgram.INVALID_PROGRAM) {
                long extractionStart = System.nanoTime();
                try { NodeLightProgramCache.save(path, program.getProgramId()); }
                catch (RuntimeException error) { if (build != null) build.cache = "SAVE_FAILED"; }
                finally { if (build != null) build.binaryExtraction += System.nanoTime() - extractionStart; }
            }
            return program;
        } finally {
            LINK.set(previous);
            if (build != null) build.link += System.nanoTime() - start;
        }
    }

    public static int prepareLink(int program) {
        if (LINK.get() != null)
            GL41C.glProgramParameteri(program, GL41C.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11C.GL_TRUE);
        return program;
    }

    private static void resolveConstructor() throws ReflectiveOperationException, java.io.IOException,
            java.security.NoSuchAlgorithmException {
        if (implementation != null) return;
        constructor = GlProgram.class.getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        try (var stream = GlProgram.class.getResourceAsStream("GlProgram.class")) {
            if (stream == null) throw new java.io.IOException("Missing native program fingerprint");
            implementation = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    public static Map<String, Object> report() {
        synchronized (LAST) { return new LinkedHashMap<>(LAST); }
    }
}
