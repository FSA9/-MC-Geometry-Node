package com.mine.geometry_node.client.runtime.marker;

import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;

public record MarkerRenderContext(
        MarkerPayload marker,
        int screenX,
        int screenY,
        int color,
        boolean screenEdge,
        EdgeDirection edgeDirection,
        String displayText
) {
    public enum EdgeDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}
