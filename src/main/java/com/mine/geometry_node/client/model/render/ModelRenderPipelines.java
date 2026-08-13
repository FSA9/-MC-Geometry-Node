package com.mine.geometry_node.client.model.render;

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

public final class ModelRenderPipelines {
    private static final Map<ModelPipelineKey, RenderPipeline> PIPELINES = new HashMap<>();

    private ModelRenderPipelines() {}

    public static void register(RegisterRenderPipelinesEvent event) {
        for (int bits = 0; bits < 16; bits++) {
            ModelVertexLayout layout = layout(bits);
            for (ModelAlphaMode alphaMode : ModelAlphaMode.values()) {
                for (boolean textured : List.of(false, true)) {
                    if (textured && (bits & 2) == 0) continue;
                    for (boolean emissiveTextured : List.of(false, true)) {
                    if (emissiveTextured && (bits & 2) == 0) continue;
                    for (boolean doubleSided : List.of(false, true)) {
                        for (boolean mirrored : List.of(false, true)) {
                            for (boolean translucent : List.of(false, true)) {
                                if (alphaMode == ModelAlphaMode.BLEND && !translucent) continue;
                                boolean skinned = (bits & 8) != 0;
                                ModelPipelineKey key = new ModelPipelineKey(layout, alphaMode, textured,
                                        emissiveTextured, doubleSided, mirrored, translucent, skinned);
                                RenderPipeline pipeline = create(key, bits);
                                PIPELINES.put(key, pipeline);
                                event.registerPipeline(pipeline);
                        }
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

    private static RenderPipeline create(ModelPipelineKey key, int bits) {
        String suffix = Integer.toString(bits) + "_" + key.alphaMode().name().toLowerCase(Locale.ROOT)
                + (key.baseColorTextured() ? "_texture" : "_plain")
                + (key.emissiveTextured() ? "_emissive_texture" : "_emissive_factor")
                + (key.doubleSided() ? "_double" : "_cull");
        suffix += key.mirrored() ? "_mirrored" : "_normal";
        suffix += key.translucent() ? "_blend_queue" : "_depth_queue";
        suffix += key.skinned() ? "_skinned" : "_rigid";
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "static_model/" + suffix))
                .withVertexShader(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "core/static_model"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(GeometryNode.MODID, "core/static_model"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, !key.translucent()))
                .withCull(!key.doubleSided() && !key.mirrored())
                .withVertexFormat(MinecraftModelVertexFormats.create(key.layout()), VertexFormat.Mode.TRIANGLES);
        if (key.skinned()) builder.withShaderDefine("HAS_SKIN").withUniform("SkinPalette", UniformType.UNIFORM_BUFFER);
        if (key.translucent()) builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        if ((bits & 1) != 0) builder.withShaderDefine("HAS_NORMAL");
        if ((bits & 2) != 0) builder.withShaderDefine("HAS_UV");
        if ((bits & 4) != 0) builder.withShaderDefine("HAS_COLOR");
        if (key.baseColorTextured()) builder.withShaderDefine("HAS_TEXTURE").withSampler("Sampler0");
        if (key.emissiveTextured()) builder.withShaderDefine("HAS_EMISSIVE_TEXTURE").withSampler("Sampler1");
        if (key.alphaMode() == ModelAlphaMode.OPAQUE) builder.withShaderDefine("ALPHA_OPAQUE");
        if (key.alphaMode() == ModelAlphaMode.MASK) builder.withShaderDefine("ALPHA_MASK");
        if (key.doubleSided()) builder.withShaderDefine("DOUBLE_SIDED");
        if (key.mirrored()) builder.withShaderDefine("MIRRORED");
        if (key.mirrored() && !key.doubleSided()) builder.withShaderDefine("MIRRORED_SINGLE_SIDED");
        return builder.build();
    }

    private static ModelVertexLayout layout(int bits) {
        List<ModelVertexLayoutElement> elements = new ArrayList<>();
        elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, 3, false));
        if ((bits & 1) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.NORMAL, ModelComponentType.INT8, 3, true));
        if ((bits & 2) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.TEXCOORD_0, ModelComponentType.FLOAT32, 2, false));
        if ((bits & 4) != 0) elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.COLOR_0, ModelComponentType.UINT8, 4, true));
        if ((bits & 8) != 0) {
            elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.JOINTS_0, ModelComponentType.FLOAT32, 4, false));
            elements.add(new ModelVertexLayoutElement(ModelAttributeSemantic.WEIGHTS_0, ModelComponentType.FLOAT32, 4, false));
        }
        return new ModelVertexLayout(elements);
    }
}
