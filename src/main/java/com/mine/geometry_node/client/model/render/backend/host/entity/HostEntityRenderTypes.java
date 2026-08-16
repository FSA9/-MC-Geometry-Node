package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.HashMap;
import java.util.Map;

/** Host entity render states whose behavior is not provided by a stock RenderType. */
public final class HostEntityRenderTypes {
    private static final RenderPipeline TRANSLUCENT_NO_DEPTH_WRITE = RenderPipelines.ENTITY_TRANSLUCENT.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(
                    GeometryNode.MODID, "model/native/host/blend_no_depth_write"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static final Map<Identifier, RenderType> TRANSLUCENT = new HashMap<>();

    private HostEntityRenderTypes() {}

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(TRANSLUCENT_NO_DEPTH_WRITE);
    }

    public static synchronized RenderType translucent(Identifier texture) {
        return TRANSLUCENT.computeIfAbsent(texture, key -> RenderType.create(
                "geometry_node_entity_translucent_no_depth_write",
                RenderSetup.builder(TRANSLUCENT_NO_DEPTH_WRITE)
                        .withTexture("Sampler0", key)
                        .useLightmap()
                        .useOverlay()
                        .affectsCrumbling()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

}
