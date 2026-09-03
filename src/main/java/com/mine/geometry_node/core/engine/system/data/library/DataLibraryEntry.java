package com.mine.geometry_node.core.engine.system.data.library;

import java.util.Objects;
import java.util.UUID;

/** A typed Data Library value. UUID is internal identity; key is the user-facing identity. */
public record DataLibraryEntry(UUID id, String key, Object value) {
    public DataLibraryEntry {
        Objects.requireNonNull(id, "id");
        key = key == null ? "" : key;
    }
}
