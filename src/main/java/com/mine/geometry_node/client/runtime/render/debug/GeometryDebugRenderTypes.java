package com.mine.geometry_node.client.runtime.render.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

final class GeometryDebugRenderTypes {
    static final RenderType GEOMETRY_FACE = RenderType.create(
            "geometry_node_geometry_debug_face",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .bufferSize(262144)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    static final RenderType GEOMETRY_LINE = RenderType.create(
            "geometry_node_geometry_debug_line",
            RenderSetup.builder(RenderPipelines.LINES_TRANSLUCENT)
                    .bufferSize(262144)
                    .createRenderSetup()
    );

    static final RenderType GEOMETRY_POINT = RenderType.create(
            "geometry_node_geometry_debug_point",
            RenderSetup.builder(RenderPipelines.DEBUG_POINTS)
                    .bufferSize(65536)
                    .createRenderSetup()
    );

    static final RenderType SCHEMATIC_PROJECTION_FACE = RenderType.create(
            "geometry_node_schematic_projection_face",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .bufferSize(1048576)
                    .createRenderSetup()
    );

    static final RenderType SCHEMATIC_PROJECTION_LINE = RenderType.create(
            "geometry_node_schematic_projection_line",
            RenderSetup.builder(RenderPipelines.LINES_TRANSLUCENT)
                    .bufferSize(524288)
                    .createRenderSetup()
    );

    static final RenderType SCHEMATIC_PROJECTION_TRANSLUCENT_BLOCK = RenderType.create(
            "geometry_node_schematic_projection_translucent_block",
            RenderSetup.builder(RenderPipelines.TRANSLUCENT_BLOCK)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
                            () -> RenderSystem.getSamplerCache().getSampler(
                                    AddressMode.CLAMP_TO_EDGE,
                                    AddressMode.CLAMP_TO_EDGE,
                                    FilterMode.LINEAR,
                                    FilterMode.NEAREST,
                                    true
                            ))
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .bufferSize(4194304)
                    .createRenderSetup()
    );

    private GeometryDebugRenderTypes() {
    }
}
