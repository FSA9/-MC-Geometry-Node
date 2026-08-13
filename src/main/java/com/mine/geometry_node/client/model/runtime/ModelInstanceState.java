package com.mine.geometry_node.client.model.runtime;

public record ModelInstanceState(ModelDimensionId dimension, ModelInstancePlacement placement, boolean visible,
                                 double maxDistance, long expiresAtNanos, ModelInstanceNodeState nodeState) {
    public ModelInstanceState {
        if (dimension == null) throw new IllegalArgumentException("dimension must not be null");
        if (placement == null) throw new IllegalArgumentException("placement must not be null");
        if (!Double.isFinite(maxDistance) || maxDistance < 0) throw new IllegalArgumentException("maxDistance must be finite and non-negative");
        if (expiresAtNanos < 0) throw new IllegalArgumentException("expiresAtNanos must not be negative");
        nodeState = nodeState == null ? ModelInstanceNodeState.IDENTITY : nodeState;
    }

    public boolean expired(long nowNanos) { return expiresAtNanos > 0 && nowNanos >= expiresAtNanos; }

    public static ModelInstanceState preview(ModelDimensionId dimension, ModelInstancePlacement placement) {
        return new ModelInstanceState(dimension, placement, true, 0, 0, ModelInstanceNodeState.IDENTITY);
    }
}
