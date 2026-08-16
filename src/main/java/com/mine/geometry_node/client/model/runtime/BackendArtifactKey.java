package com.mine.geometry_node.client.model.runtime;

import java.util.Objects;

/** Identity key that binds one backend artifact slot to its value type. */
public final class BackendArtifactKey<T> {
    private final String name;

    public BackendArtifactKey(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override public String toString() { return name; }
}
