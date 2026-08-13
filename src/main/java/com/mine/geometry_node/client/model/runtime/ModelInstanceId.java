package com.mine.geometry_node.client.model.runtime;

public record ModelInstanceId(String value) implements Comparable<ModelInstanceId> {
    public ModelInstanceId {
        value = value == null ? "" : value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("model instance id must not be empty");
    }
    @Override public int compareTo(ModelInstanceId other) { return value.compareTo(other.value); }
}
