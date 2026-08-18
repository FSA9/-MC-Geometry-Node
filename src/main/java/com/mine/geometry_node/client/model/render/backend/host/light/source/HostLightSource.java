package com.mine.geometry_node.client.model.render.backend.host.light.source;

import java.util.Objects;

/** Immutable world-space source content. RGB is linear and intentionally has no renderer binding yet. */
public record HostLightSource(HostLightSourceId id,
                              long revision,
                              double worldX,
                              double worldY,
                              double worldZ,
                              float linearRed,
                              float linearGreen,
                              float linearBlue,
                              float intensity,
                              float radius) {
    public HostLightSource {
        Objects.requireNonNull(id, "id");
        if (revision < 0) throw new IllegalArgumentException("source revision must not be negative");
        requireFinite(worldX, "worldX");
        requireFinite(worldY, "worldY");
        requireFinite(worldZ, "worldZ");
        requireNonNegative(linearRed, "linearRed");
        requireNonNegative(linearGreen, "linearGreen");
        requireNonNegative(linearBlue, "linearBlue");
        requireNonNegative(intensity, "intensity");
        requireNonNegative(radius, "radius");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void requireNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
