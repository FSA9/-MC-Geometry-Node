package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import java.util.Objects;
import java.util.UUID;

/** A value stored in the virtual Data Library tree. UUID is its stable runtime identity. */
public record DataLibraryEntry(
        UUID id,
        @Nullable UUID parentId,
        PortType type,
        String key,
        @Nullable Object value,
        List<String> tags,
        Map<String, Object> data
) {
    public DataLibraryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        key = DataLibraryDocument.requireName(key, "Entry key");
        tags = DataLibraryTags.normalize(tags);
        data = DataDictionary.snapshot(Objects.requireNonNull(data, "data"), GraphMetadataValuePolicy.of(null));
    }

    public DataLibraryEntry(UUID id, @Nullable UUID parentId, PortType type, String key,
                            @Nullable Object value) {
        this(id, parentId, type, key, value, List.of(), Map.of());
    }

    public DataLibraryEntry(UUID id, @Nullable UUID parentId, PortType type, String key,
                            @Nullable Object value, List<String> tags) {
        this(id, parentId, type, key, value, tags, Map.of());
    }

    /** Detaches mutable graph leaves as well as dictionary containers. */
    @Override public Map<String, Object> data() {
        return DataDictionary.snapshot(data, GraphMetadataValuePolicy.of(null));
    }
}
