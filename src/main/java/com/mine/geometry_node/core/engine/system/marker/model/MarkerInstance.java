package com.mine.geometry_node.core.engine.system.marker.model;

import com.mine.geometry_node.core.engine.system.marker.MarkerType;

import java.util.Objects;

/**
 * Authoritative persisted marker state. Entity positions are runtime state and are not persisted.
 */
public record MarkerInstance(
        MarkerAddress address,
        String typeId,
        MarkerAnchor anchor,
        String text,
        boolean showDistance,
        long createdGameTime,
        long expiresAtGameTime
) {
    public static final long NEVER_EXPIRES = -1L;
    public static final int MAX_TEXT_LENGTH = 2048;

    public MarkerInstance {
        address = Objects.requireNonNull(address, "address");
        typeId = MarkerType.normalizeId(typeId);
        anchor = Objects.requireNonNull(anchor, "anchor");
        text = text == null ? "" : text;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        createdGameTime = Math.max(0L, createdGameTime);
        if (expiresAtGameTime != NEVER_EXPIRES && expiresAtGameTime < createdGameTime) {
            expiresAtGameTime = createdGameTime;
        }
    }

    public boolean isExpired(long gameTime) {
        return expiresAtGameTime != NEVER_EXPIRES && gameTime >= expiresAtGameTime;
    }

    public MarkerInstance withAnchor(MarkerAnchor newAnchor) {
        return new MarkerInstance(address, typeId, newAnchor, text, showDistance, createdGameTime, expiresAtGameTime);
    }
}
