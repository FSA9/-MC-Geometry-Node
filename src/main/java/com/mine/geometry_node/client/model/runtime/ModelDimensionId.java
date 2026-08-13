package com.mine.geometry_node.client.model.runtime;

public record ModelDimensionId(String value) {
    public ModelDimensionId {
        value = value == null ? "" : value.trim();
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("dimension must be a namespaced identifier");
        }
    }
    @Override public String toString() { return value; }
}
