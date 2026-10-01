package com.mine.geometry_node.client.model.render;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
/** P0 uses native entity attributes and a dedicated triangle pipeline; no global vanilla mutation. */
public final class ModelProbePipelines {
    public static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("geometry_node","textures/model/probe.png");
    public static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("geometry_node","pipeline/model_probe"))
            .withShaderDefine("NO_OVERLAY").withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.TRIANGLES).build();
    public static final RenderType TYPE=RenderType.create("geometry_node_model_probe",
            RenderSetup.builder(PIPELINE).withTexture("Sampler0",TEXTURE).withTexture("Sampler1",TEXTURE).useLightmap().bufferSize(4096).createRenderSetup());
    private ModelProbePipelines() {}
}
