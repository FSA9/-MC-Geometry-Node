package com.mine.geometry_node.client.model.compat;

import com.mine.geometry_node.GeometryNode;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.systems.RenderPassBackend;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** MC 26.1 validation runs before Iris ExtendedShader's optional overlay/lightmap fallbacks. */
public final class IrisSamplerFallbacks {
    private static final String EXTENDED_SHADER = "net.irisshaders.iris.pipeline.programs.ExtendedShader";
    private static boolean reportedFailure;
    private record Access(Field pipeline, Method overlay, Method lightmap) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            Class<?> shader = type;
            while (shader != null && !EXTENDED_SHADER.equals(shader.getName())) shader = shader.getSuperclass();
            if (shader == null) return null;
            try {
                Field pipeline = shader.getDeclaredField("pipeline");
                if (!pipeline.trySetAccessible()) throw new IllegalAccessException("Iris shader pipeline is inaccessible");
                return new Access(pipeline, pipeline.getType().getMethod("getBiggerWhitePixel"),
                        pipeline.getType().getMethod("getWhitePixel"));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                reportFailure(failure);
                return null;
            }
        }
    };

    /** Reuses Iris-owned textures and samplers; never allocates GPU resources inside a RenderPass. */
    public static void bindMissing(GlProgram program, Map<String, ?> bound, RenderPassBackend pass) {
        if (program == null || (bound.containsKey("Sampler1") && bound.containsKey("Sampler2"))) return;
        Access access = ACCESS.get(program.getClass());
        if (access == null) return; // Vanilla and absent/unsupported Iris keep their original validation.
        try {
            var uniforms = program.getUniforms();
            bindMissing(name -> uniforms.get(name) instanceof Uniform.Sampler, bound, name -> {
                try {
                    Object pipeline = access.pipeline().get(program);
                    Method getter = "Sampler1".equals(name) ? access.overlay() : access.lightmap();
                    return (AbstractTexture) getter.invoke(pipeline);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot obtain Iris sampler fallback " + name, failure);
                }
            }, (name, texture) -> pass.bindTexture(name, texture.getTextureView(), texture.getSampler()));
        } catch (RuntimeException | LinkageError failure) {
            // Do not hide a missing/closed sampler: the host validation remains authoritative.
            reportFailure(failure);
        }
    }

    /** Only the two fallbacks explicitly owned by ExtendedShader are eligible. */
    static <T> void bindMissing(Predicate<String> required, Map<String, ?> bound,
                               Function<String, T> fallback, BiConsumer<String, T> bind) {
        if (!bound.containsKey("Sampler1") && required.test("Sampler1")) bind.accept("Sampler1", fallback.apply("Sampler1"));
        if (!bound.containsKey("Sampler2") && required.test("Sampler2")) bind.accept("Sampler2", fallback.apply("Sampler2"));
    }

    private static void reportFailure(Throwable failure) {
        if (!reportedFailure) {
            reportedFailure = true;
            GeometryNode.LOGGER.warn("Iris optional sampler compatibility unavailable; keeping Minecraft sampler validation", failure);
        }
    }

    private IrisSamplerFallbacks() {}
}
