package com.mine.geometry_node.core.engine.system.scene;

import java.util.Objects;
import java.util.UUID;

/** A null replacement deletes; -1 is a never-used ID, otherwise the exact server version is required. */
public record SceneChange(UUID id, long expectedRevision, SceneObject replacement) {
    public SceneChange {
        Objects.requireNonNull(id);
        if (expectedRevision < -1) throw new IllegalArgumentException("Invalid expected revision");
        if (replacement != null && !id.equals(replacement.id())) throw new IllegalArgumentException("Object ID mismatch");
    }
}
