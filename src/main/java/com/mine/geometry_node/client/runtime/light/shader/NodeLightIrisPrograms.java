package com.mine.geometry_node.client.runtime.light.shader;

import com.google.common.collect.ImmutableSet;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Caches linked composite programs; Iris still builds all uniforms, samplers and images. */
public final class NodeLightIrisPrograms {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ThreadLocal<Path> PENDING = new ThreadLocal<>();
    private static Constructor<?> constructor;
    private static Field programField;
    private static String implementation;
    private static boolean warned;

    private NodeLightIrisPrograms() {}

    public static void reset() {
        PENDING.remove();
    }

    public static Object load(String name, String vertex, String geometry, String fragment,
                              ImmutableSet<Integer> reservedUnits) {
        if (fragment == null || !fragment.contains("uniform int nodeLightCount;")) return null;
        int program = 0;
        try {
            resolveBuilder();
            Path path = NodeLightProgramCache.path("iris-composite-v1", implementation, name, vertex, geometry, fragment);
            if (path == null) {
                NodeLightShaderBuilds.cache("UNAVAILABLE");
                return null;
            }
            PENDING.set(path);
            program = NodeLightProgramCache.load(path);
            if (program == 0) {
                NodeLightShaderBuilds.cache(NodeLightProgramCache.loadStatus());
                NodeLightShaderBuilds.cacheFailure(NodeLightProgramCache.loadFailure());
                return null;
            }
            Object builder = constructor.newInstance(name, program, reservedUnits);
            PENDING.remove();
            NodeLightShaderBuilds.cache("HIT");
            return builder;
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException
                 | java.security.NoSuchAlgorithmException error) {
            if (program != 0) GL20C.glDeleteProgram(program);
            PENDING.remove();
            NodeLightShaderBuilds.cache("FALLBACK");
            if (!warned) {
                warned = true;
                LOGGER.warn("Iris light program cache unavailable; using normal shader compilation", error);
            }
            return null;
        }
    }

    public static int prepareLink(int program) {
        if (PENDING.get() != null)
            GL41C.glProgramParameteri(program, GL41C.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11C.GL_TRUE);
        return program;
    }

    public static void save(Object builder) {
        Path path = PENDING.get();
        PENDING.remove();
        if (path == null || builder == null) return;
        try {
            NodeLightProgramCache.save(path, programField.getInt(builder));
        } catch (ReflectiveOperationException | RuntimeException error) {
            NodeLightShaderBuilds.cache("SAVE_FAILED");
        }
    }

    private static void resolveBuilder() throws ReflectiveOperationException, java.io.IOException,
            java.security.NoSuchAlgorithmException {
        if (implementation != null) return;
        Class<?> builder = Class.forName("net.irisshaders.iris.gl.program.ProgramBuilder");
        constructor = builder.getDeclaredConstructor(String.class, int.class, ImmutableSet.class);
        constructor.setAccessible(true);
        programField = builder.getDeclaredField("program");
        programField.setAccessible(true);
        // Sources alone do not describe link-time attribute bindings or Iris workarounds.
        var hash = MessageDigest.getInstance("SHA-256");
        for (String resource : new String[]{"program/ProgramBuilder", "shader/ProgramCreator",
                "shader/GlShader", "shader/ShaderWorkarounds"}) {
            try (var stream = builder.getResourceAsStream("/net/irisshaders/iris/gl/" + resource + ".class")) {
                if (stream == null) throw new java.io.IOException("Missing Iris binary cache fingerprint");
                hash.update(stream.readAllBytes());
            }
        }
        implementation = HexFormat.of().formatHex(hash.digest());
    }
}
