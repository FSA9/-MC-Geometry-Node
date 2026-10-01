package com.mine.geometry_node.client.model.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Bounded native variants shared by the fallback collector, P4 resident GPU backend and P5 Iris bridge. */
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
                .withSampler("BaseTexture").withSampler("EmissionTexture").withSampler("MaterialData")
                .withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2")
                .withCull(twoSided == 0).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, blend == 0));
        if (blend == 1) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        return builder.build();
    }
    static Identifier id(String path) { return Identifier.fromNamespaceAndPath("geometry_node", path); }
    static RenderPipeline get(boolean blend, boolean doubleSided) { return PIPELINES[blend ? 1 : 0][doubleSided ? 1 : 0]; }
    static RenderPipeline pipeline(boolean blend, boolean doubleSided) { return RESIDENT_PIPELINES[blend ? 1 : 0][doubleSided ? 1 : 0]; }

    /** Main-pass pipelines whose layout is stable enough for Iris' public adapter. */
    static RenderPipeline[] irisMainPipelines() {
        return new RenderPipeline[]{
                IRIS_PIPELINES[0][0], IRIS_PIPELINES[0][1]
        };
    }

    /** Translucent model pipelines are mapped separately to ENTITIES_TRANSLUCENT. */
    static RenderPipeline[] irisTranslucentPipelines() {
        return new RenderPipeline[]{
                IRIS_PIPELINES[1][0], IRIS_PIPELINES[1][1]
        };
    }

    /** Shadow callback uses the entity-layout opaque variants; MASK shares their alpha-test path. */
    static RenderPipeline[] irisShadowPipelines() {
        return new RenderPipeline[]{IRIS_PIPELINES[0][0], IRIS_PIPELINES[0][1]};
    }

    // Only entity-layout pipelines may be assigned to Iris. The private static format
    // repurposes UV1/UV2 and cannot be consumed by shaderpack entity programs.
    private static final RenderPipeline[][] IRIS_PIPELINES = new RenderPipeline[2][2];
    static {
        for (int blend = 0; blend < 2; blend++) for (int sides = 0; sides < 2; sides++) {
            var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(id("pipeline/model_iris_" + blend + "_" + sides))
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
                    .withSampler("Sampler1")
                    // Without this define API v4 selects ENTITIES_ALPHA, whose vertex
                    // alpha is a discard threshold (an opaque white vertex discards all pixels).
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withCull(sides == 0)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, blend == 0));
            if (blend == 1) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
            IRIS_PIPELINES[blend][sides] = builder.build();
        }
    }
    static RenderPipeline iris(boolean blend, boolean doubleSided) {
        return IRIS_PIPELINES[blend ? 1 : 0][doubleSided ? 1 : 0];
    }

    private static final RenderPipeline[][] IRIS_EMISSIVE = new RenderPipeline[2][2];
    static {
        for (int mode = 0; mode < 2; mode++) for (int sides = 0; sides < 2; sides++) {
            var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(id("pipeline/model_iris_emissive_" + mode + "_" + sides))
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
                    .withSampler("Sampler1").withShaderDefine("ALPHA_CUTOUT", 0.1f).withCull(sides == 0)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, mode == 0));
            if (mode != 0) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
            IRIS_EMISSIVE[mode][sides] = builder.build();
        }
    }
    static RenderPipeline irisUnlit(boolean blend, boolean doubleSided) {
        return IRIS_EMISSIVE[blend ? 1 : 0][doubleSided ? 1 : 0];
    }
    static RenderPipeline[] irisEmissivePipelines() {
        return java.util.Arrays.stream(IRIS_EMISSIVE).flatMap(java.util.Arrays::stream).toArray(RenderPipeline[]::new);
    }

    private ModelStaticPipelines() {}
}
