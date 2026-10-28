package com.mine.geometry_node.core.engine.system.marker.model;

import com.mine.geometry_node.core.engine.system.marker.type.MarkerType;
import com.mine.geometry_node.core.engine.metadata.state.OwnerMetadata;

import java.util.Objects;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Authoritative persisted marker state, including captured positions and independent world/local offsets.
 */
public record MarkerInstance(
        MarkerAddress address,
        String typeId,
        SpatialAnchor anchor,
        Vec3 worldOffset,
        Vec3 localOffset,
        String text,
        boolean showDistance,
        long createdGameTime,
        long expiresAtGameTime,
        OwnerMetadata metadata,
        UUID incarnation,
        long metadataRevision
) {
    public static final long NEVER_EXPIRES = -1L;
    public static final int MAX_TEXT_LENGTH = 2048;

    public MarkerInstance(MarkerAddress address, String typeId, SpatialAnchor anchor, String text,
                          boolean showDistance, long createdGameTime, long expiresAtGameTime) {
        this(address, typeId, anchor, Vec3.ZERO, Vec3.ZERO, text, showDistance, createdGameTime, expiresAtGameTime,
                OwnerMetadata.EMPTY, UUID.randomUUID(), 0);
    }

    public MarkerInstance(MarkerAddress address, String typeId, SpatialAnchor anchor, Vec3 worldOffset, Vec3 localOffset, String text,
                          boolean showDistance, long createdGameTime, long expiresAtGameTime) {
        this(address, typeId, anchor, worldOffset, localOffset, text, showDistance, createdGameTime, expiresAtGameTime,
                OwnerMetadata.EMPTY, UUID.randomUUID(), 0);
    }

    public MarkerInstance {
        address = Objects.requireNonNull(address, "address");
        typeId = MarkerType.normalizeId(typeId);
        anchor = Objects.requireNonNull(anchor, "anchor");
        if (anchor.dimension() == null) throw new IllegalArgumentException("Persisted marker anchors require a concrete dimension");
        worldOffset = finite(worldOffset);
        localOffset = finite(localOffset);
        metadata = Objects.requireNonNull(metadata, "metadata");
        incarnation = Objects.requireNonNull(incarnation, "incarnation");
        if (metadataRevision < 0) throw new IllegalArgumentException("Negative metadata revision");
        text = text == null ? "" : text;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        createdGameTime = Math.max(0L, createdGameTime);
        if (expiresAtGameTime != NEVER_EXPIRES && expiresAtGameTime < createdGameTime) {
            expiresAtGameTime = createdGameTime;
        }
    }

    private static Vec3 finite(Vec3 offset) {
        Objects.requireNonNull(offset, "offset");
        if (!Double.isFinite(offset.x) || !Double.isFinite(offset.y) || !Double.isFinite(offset.z))
            throw new IllegalArgumentException("Nonfinite marker offset");
        return offset;
    }

    public boolean isExpired(long gameTime) {
        return expiresAtGameTime != NEVER_EXPIRES && gameTime >= expiresAtGameTime;
    }

    public MarkerInstance withAnchor(SpatialAnchor newAnchor) {
        return new MarkerInstance(address, typeId, newAnchor, worldOffset, localOffset, text, showDistance, createdGameTime, expiresAtGameTime,
                metadata, incarnation, metadataRevision);
    }

    public MarkerInstance withMetadata(OwnerMetadata value, UUID identity, long revision) {
        return new MarkerInstance(address, typeId, anchor, worldOffset, localOffset, text, showDistance, createdGameTime, expiresAtGameTime,
                value, identity, revision);
    }
}
