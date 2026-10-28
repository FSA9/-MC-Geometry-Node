package com.mine.geometry_node.core.engine.system.marker.model;

import java.util.Objects;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import net.minecraft.world.phys.Vec3;

public record MarkerRequest(
        MarkerAddress address,
        String typeId,
        SpatialAnchor anchor,
        Vec3 worldOffset,
        Vec3 localOffset,
        String text,
        boolean showDistance,
        int durationTicks
) {
    public MarkerRequest {
        address = Objects.requireNonNull(address, "address");
        anchor = Objects.requireNonNull(anchor, "anchor");
        worldOffset = Objects.requireNonNull(worldOffset, "worldOffset");
        localOffset = Objects.requireNonNull(localOffset, "localOffset");
        if (!Double.isFinite(worldOffset.x) || !Double.isFinite(worldOffset.y) || !Double.isFinite(worldOffset.z)
                || !Double.isFinite(localOffset.x) || !Double.isFinite(localOffset.y) || !Double.isFinite(localOffset.z))
            throw new IllegalArgumentException("Nonfinite marker offset");
        durationTicks = Math.max(0, durationTicks);
    }
}
