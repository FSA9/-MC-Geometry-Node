package com.mine.geometry_node.core.engine.system.data.library;

import java.util.Objects;
import java.util.UUID;

public record DataLibraryEntry(UUID id, String name, Object value) {
    public DataLibraryEntry {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
    }
}
