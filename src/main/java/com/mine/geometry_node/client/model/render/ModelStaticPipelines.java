package com.mine.geometry_node.client.model.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Four bounded native variants. This layout is deliberately distinct from Iris' entity layout. */
final class ModelStaticPipelines {
    // UV1/UV2 carry the two IEEE-754 emissive UV components as lossless pairs of shorts.
    // LINE_WIDTH carries the numeric packed block/sky light. Only this shader interprets these fields.
    static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0).add("EmissionU", VertexFormatElement.UV1)
            .add("EmissionV", VertexFormatElement.UV2).add("Normal", VertexFormatElement.NORMAL).padding(1)
            .add("PackedLight", VertexFormatElement.LINE_WIDTH).build();
    private static final RenderPipeline[][] PIPELINES = new RenderPipeline[2][2];
    static {
        for (int blend=0;blend<2;blend++) for(int twoSided=0;twoSided<2;twoSided++) {
            var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                    .withLocation(id("pipeline/model_static_"+blend+"_"+twoSided))
                    .withVertexShader(id("core/model_static")).withFragmentShader(id("core/model_static"))
                    .withVertexFormat(FORMAT, VertexFormat.Mode.TRIANGLES)
                    .withSampler("BaseTexture").withSampler("EmissionTexture").withSampler("MaterialData").withSampler("Sampler2")
                    .withCull(twoSided==0).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,blend==0));
            if(blend==1)builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
            PIPELINES[blend][twoSided]=builder.build();
        }
    }
    static Identifier id(String path){return Identifier.fromNamespaceAndPath("geometry_node",path);}
    static RenderPipeline get(boolean blend,boolean doubleSided){return PIPELINES[blend?1:0][doubleSided?1:0];}
    private ModelStaticPipelines(){}
}
