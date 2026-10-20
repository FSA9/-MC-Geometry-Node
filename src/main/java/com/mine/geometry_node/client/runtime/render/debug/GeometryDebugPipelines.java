package com.mine.geometry_node.client.runtime.render.debug;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

/** Dedicated overlays: no scene depth writes or shadow registration. */
final class GeometryDebugPipelines {
    static final RenderPipeline FACE = irisOverlay("face", RenderPipelines.DEBUG_QUADS, "BASIC");
    static final RenderPipeline LINE = irisOverlay("line", RenderPipelines.LINES_TRANSLUCENT, "LINES");
    // Iris BASIC has no point-width attribute or gl_PointSize output. Leave this
    // pipeline unassigned and bypass Iris only while submitting this batch.
    static final RenderPipeline POINT = overlay("point", RenderPipelines.DEBUG_POINTS);

    private static RenderPipeline overlay(String name, RenderPipeline source) {
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("geometry_node", "pipeline/debug_" + name))
                .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withColorTargetState(source.getColorTargetState()).withCull(false)
                // Hidden collision surfaces must not blend through every wall of a model.
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false));
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        return builder.build();
    }

    private static RenderPipeline irisOverlay(String name, RenderPipeline source, String irisRole) {
        RenderPipeline pipeline = overlay(name, source);
        try {
            Class<?> apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> roleType = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object api = apiType.getMethod("getInstance").invoke(null);
            apiType.getMethod("assignPipeline", RenderPipeline.class, roleType)
                    .invoke(api, pipeline, roleType.getField(irisRole).get(null));
        } catch (ClassNotFoundException ignored) {
            // Optional renderer; vanilla uses the original debug shaders.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register debug pipeline with Iris", e);
        }
        return pipeline;
    }

    static void withVanillaPointShader(Runnable draw) {
        withVanillaPointShader(IrisPointShader.BYPASS, draw);
    }

    static void withVanillaPointShader(Field bypass, Runnable draw) {
        if (bypass == null) {
            draw.run();
            return;
        }
        try {
            boolean previous = bypass.getBoolean(null);
            bypass.setBoolean(null, true);
            try {
                draw.run();
            } finally {
                bypass.setBoolean(null, previous);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot select the vanilla debug point shader", e);
        }
    }

    private static final class IrisPointShader {
        private static final Field BYPASS = findBypass();

        private static Field findBypass() {
            try {
                return Class.forName("net.irisshaders.iris.vertices.ImmediateState")
                        .getField("bypass");
            } catch (ClassNotFoundException ignored) {
                return null;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Cannot retain the vanilla debug point shader with this Iris version", e);
            }
        }
    }

    private GeometryDebugPipelines() {}
}
