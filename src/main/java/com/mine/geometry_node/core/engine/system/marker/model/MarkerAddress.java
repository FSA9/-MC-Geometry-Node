package com.mine.geometry_node.core.engine.system.marker.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable marker identity. Personal keys are isolated per viewer; public keys share one namespace.
 */
public record MarkerAddress(MarkerAudience audience, @Nullable UUID viewerId, String key) {
    public static final int MAX_KEY_LENGTH = 256;

    public MarkerAddress {
        audience = Objects.requireNonNull(audience, "audience");
        key = key == null ? "" : key.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("marker input must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("marker input is too long");
        }
        if (audience == MarkerAudience.SELF && viewerId == null) {
            throw new IllegalArgumentException("personal marker requires a viewer");
        }
        if (audience == MarkerAudience.ALL) {
            viewerId = null;
        }
    }

    public static MarkerAddress self(UUID viewerId, String key) {
        return new MarkerAddress(MarkerAudience.SELF, Objects.requireNonNull(viewerId, "viewerId"), key);
    }

    public static MarkerAddress all(String key) {
        return new MarkerAddress(MarkerAudience.ALL, null, key);
    }
}
