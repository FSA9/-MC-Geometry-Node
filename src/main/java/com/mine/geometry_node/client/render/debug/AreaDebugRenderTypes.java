package com.mine.geometry_node.client.render.debug;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

final class AreaDebugRenderTypes {
    static final RenderType AREA_FACE = RenderType.create(
            "geometry_node_area_debug_face",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .bufferSize(1536)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    static final RenderType AREA_LINE = RenderType.create(
            "geometry_node_area_debug_line",
            RenderSetup.builder(RenderPipelines.LINES_TRANSLUCENT)
                    .bufferSize(1536)
                    .createRenderSetup()
    );

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

    private AreaDebugRenderTypes() {
    }
}
