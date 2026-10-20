package com.mine.geometry_node.client.runtime.render.debug;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Dedicated overlays: no scene depth writes or shadow registration. */
final class GeometryDebugPipelines {
    static final RenderPipeline FACE = irisOverlay("face", RenderPipelines.DEBUG_QUADS, "BASIC");
    static final RenderPipeline LINE = irisOverlay("line", RenderPipelines.LINES_TRANSLUCENT, "LINES");
    // Iris BASIC has no point-width attribute or gl_PointSize output. Leave this
    // pipeline unassigned so Iris retains the original debug_point shader.
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

    private GeometryDebugPipelines() {}
}
