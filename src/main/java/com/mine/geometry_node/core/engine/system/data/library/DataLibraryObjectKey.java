package com.mine.geometry_node.core.engine.system.data.library;

import java.util.Objects;
import java.util.UUID;

/** Stable identity used by remote mutations; UUIDs are unique across folders and entries. */
public record DataLibraryObjectKey(UUID id) {
    public DataLibraryObjectKey {
        Objects.requireNonNull(id, "id");
    }
}
