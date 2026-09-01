package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an entry. UUIDs are scoped by their PortType group. */
public record DataLibraryEntryKey(PortType type, UUID id) {
    public DataLibraryEntryKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }
}
