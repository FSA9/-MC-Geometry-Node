package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A value stored in the virtual Data Library tree. UUID is its stable runtime identity. */
public record DataLibraryEntry(
        UUID id,
        @Nullable UUID parentId,
        PortType type,
        String key,
        @Nullable Object value,
        List<String> tags
) {
    public DataLibraryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        key = DataLibraryDocument.requireName(key, "Entry key");
        tags = DataLibraryTags.normalize(tags);
    }

    public DataLibraryEntry(UUID id, @Nullable UUID parentId, PortType type, String key,
                            @Nullable Object value) {
        this(id, parentId, type, key, value, List.of());
    }

}
