package com.mine.geometry_node.core.engine.system.marker.model;

import java.util.Objects;

public record MarkerRequest(
        MarkerAddress address,
        String typeId,
        MarkerAnchor anchor,
        String text,
        boolean showDistance,
        int durationTicks
) {
    public MarkerRequest {
        address = Objects.requireNonNull(address, "address");
        anchor = Objects.requireNonNull(anchor, "anchor");
        durationTicks = Math.max(0, durationTicks);
    }
}
