package com.mine.geometry_node.client.model.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Bounded native variants shared by the fallback collector and the P4 resident GPU backend. */
final class ModelStaticPipelines {
    static final int VERTEX_SIZE_ESTIMATE = 64;
    static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0).add("EmissionU", VertexFormatElement.UV1)
            .add("EmissionV", VertexFormatElement.UV2).add("Normal", VertexFormatElement.NORMAL).padding(1)
            .add("PackedLight", VertexFormatElement.LINE_WIDTH).build();
    private static final RenderPipeline[][] PIPELINES = new RenderPipeline[2][2];
    private static final RenderPipeline[][] RESIDENT_PIPELINES = new RenderPipeline[2][2];
    static {
        for (int blend = 0; blend < 2; blend++) for (int twoSided = 0; twoSided < 2; twoSided++) {
            PIPELINES[blend][twoSided] = build(blend, twoSided, "model_static", "model_static");
            RESIDENT_PIPELINES[blend][twoSided] = build(blend, twoSided, "model_static_resident", "model_static_resident");
        }
    }
    private static RenderPipeline build(int blend, int twoSided, String shader, String location) {
        var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(id("pipeline/" + location + "_" + blend + "_" + twoSided))
                .withVertexShader(id("core/" + shader)).withFragmentShader(id("core/model_static"))
                .withVertexFormat(FORMAT, VertexFormat.Mode.TRIANGLES)
                .withSampler("BaseTexture").withSampler("EmissionTexture").withSampler("MaterialData").withSampler("Sampler2")
                .withCull(twoSided == 0).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, blend == 0));
        if (blend == 1) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        return builder.build();
    }
    static Identifier id(String path) { return Identifier.fromNamespaceAndPath("geometry_node", path); }
    static RenderPipeline get(boolean blend, boolean doubleSided) { return PIPELINES[blend ? 1 : 0][doubleSided ? 1 : 0]; }
    static RenderPipeline pipeline(boolean blend, boolean doubleSided) { return RESIDENT_PIPELINES[blend ? 1 : 0][doubleSided ? 1 : 0]; }
    private ModelStaticPipelines() {}
}
