package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelVertexFormats;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.*;

public final class StandaloneRenderPipelines {
    private static final Map<ModelPipelineKey, RenderPipeline> PIPELINES = new HashMap<>();

    private StandaloneRenderPipelines() {}

    public static void register(RegisterRenderPipelinesEvent event) {
        for (int bits = 0; bits < 16; bits++) {
            for (int uvCount = 0; uvCount <= 5; uvCount++) {
                ModelVertexLayout layout = layout(bits, uvCount);
                for (ModelAlphaMode alphaMode : ModelAlphaMode.values()) {
                    for (boolean doubleSided : List.of(false, true)) {
                        for (boolean mirrored : List.of(false, true)) {
                            for (boolean translucent : List.of(false, true)) {
                                if (alphaMode == ModelAlphaMode.BLEND && !translucent) continue;
                                boolean skinned = (bits & 4) != 0;
                                ModelPipelineKey key = new ModelPipelineKey(layout, alphaMode,
                                        doubleSided, mirrored, translucent, skinned);
                                RenderPipeline pipeline = create(key, bits, uvCount);
                                PIPELINES.put(key, pipeline);
                                event.registerPipeline(pipeline);
                            }
                        }
                    }
                }
            }
        }
    }

    public static RenderPipeline get(ModelPipelineKey key) {
        RenderPipeline pipeline = PIPELINES.get(key);
        if (pipeline == null) throw new IllegalStateException("model render pipeline was not registered: " + key);
        return pipeline;
    }

    private static RenderPipeline create(ModelPipelineKey key, int bits, int uvCount) {
        String suffix = bits + "_uv" + uvCount + "_" + key.alphaMode().name().toLowerCase(Locale.ROOT)
                + (key.doubleSided() ? "_double" : "_cull");
        suffix += key.mirrored() ? "_mirrored" : "_normal";
        suffix += key.translucent() ? "_blend_queue" : "_depth_queue";
        suffix += key.skinned() ? "_skinned" : "_rigid";
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "model/native/standalone/" + suffix))
                .withVertexShader(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "model/native/standalone/static_model"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "model/native/standalone/static_model"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("ModelMaterial", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, !key.translucent()))
                .withCull(!key.doubleSided() && !key.mirrored())
                .withVertexFormat(MinecraftModelVertexFormats.create(key.layout()), VertexFormat.Mode.TRIANGLES);
        if (key.skinned()) builder.withShaderDefine("HAS_SKIN").withUniform("SkinPalette", UniformType.UNIFORM_BUFFER);
        if (key.translucent()) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        if ((bits & 1) != 0) builder.withShaderDefine("HAS_NORMAL");
        if (uvCount > 0) builder.withShaderDefine("HAS_UV");
        for (int uv = 1; uv < uvCount; uv++) builder.withShaderDefine("HAS_UV" + uv);
        if ((bits & 2) != 0) builder.withShaderDefine("HAS_COLOR");
        if ((bits & 8) != 0) builder.withShaderDefine("HAS_TANGENT");
        builder.withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2")
                .withSampler("Sampler3").withSampler("Sampler4");
        if (key.alphaMode() == ModelAlphaMode.OPAQUE) builder.withShaderDefine("ALPHA_OPAQUE");
        if (key.alphaMode() == ModelAlphaMode.MASK) builder.withShaderDefine("ALPHA_MASK");
        if (key.doubleSided()) builder.withShaderDefine("DOUBLE_SIDED");
        if (key.mirrored()) builder.withShaderDefine("MIRRORED");
        if (key.mirrored() && !key.doubleSided()) builder.withShaderDefine("MIRRORED_SINGLE_SIDED");
        return builder.build();
    }

    private static ModelVertexLayout layout(int bits, int uvCount) {
        List<ModelVertexLayoutElement> elements = new ArrayList<>();
        elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, 3, false));
        if ((bits & 1) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.NORMAL, ModelComponentType.INT8, 3, true));
        for (int uv = 0; uv < uvCount; uv++) elements.add(new ModelVertexLayoutElement(
                ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, uv), ModelComponentType.FLOAT32, 2, false));
        if ((bits & 2) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.COLOR_0, ModelComponentType.UINT8, 4, true));
        if ((bits & 8) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.TANGENT, ModelComponentType.INT8, 4, true));
        if ((bits & 4) != 0) {
            elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.JOINTS_0, ModelComponentType.FLOAT32, 4, false));
            elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.WEIGHTS_0, ModelComponentType.FLOAT32, 4, false));
        }
        return new ModelVertexLayout(elements);
    }
}
