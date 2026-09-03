package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory representation of one complete Data Library file. */
public final class DataLibraryDocument {
    private final EnumMap<PortType, LinkedHashMap<UUID, DataLibraryEntry>> entries =
            new EnumMap<>(PortType.class);
    private final EnumMap<PortType, LinkedHashMap<String, UUID>> idsByKey =
            new EnumMap<>(PortType.class);

    public Map<UUID, DataLibraryEntry> entries(PortType type) {
        Map<UUID, DataLibraryEntry> values = entries.get(type);
        return values != null ? Collections.unmodifiableMap(values) : Map.of();
    }

    public Map<PortType, Map<UUID, DataLibraryEntry>> entriesByType() {
        EnumMap<PortType, Map<UUID, DataLibraryEntry>> result = new EnumMap<>(PortType.class);
        entries.forEach((type, values) -> result.put(type, Collections.unmodifiableMap(values)));
        return Collections.unmodifiableMap(result);
    }

    public Optional<DataLibraryEntry> find(PortType type, UUID id) {
        Map<UUID, DataLibraryEntry> values = entries.get(type);
        return Optional.ofNullable(values != null ? values.get(id) : null);
    }

    public Optional<DataLibraryEntry> find(DataLibraryEntryKey key) {
        return find(key.type(), key.id());
    }

    public Optional<DataLibraryEntry> findByKey(PortType type, String key) {
        String normalized = key == null ? "" : key;
        Map<String, UUID> typeIndex = idsByKey.get(type);
        UUID id = typeIndex != null ? typeIndex.get(normalized) : null;
        return id != null ? find(type, id) : Optional.empty();
    }

    public void put(PortType type, DataLibraryEntry entry) {
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        LinkedHashMap<UUID, DataLibraryEntry> values =
                entries.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
        LinkedHashMap<String, UUID> typeIndex =
                idsByKey.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
        UUID keyOwner = typeIndex.get(entry.key());
        if (keyOwner != null && !keyOwner.equals(entry.id())) {
            throw new IllegalArgumentException("Duplicate Data Library key: " + entry.key());
        }
        DataLibraryEntry previous = values.put(entry.id(), entry);
        if (previous != null && !previous.key().equals(entry.key())) {
            typeIndex.remove(previous.key(), entry.id());
        }
        typeIndex.put(entry.key(), entry.id());
    }

    public boolean remove(PortType type, UUID id) {
        LinkedHashMap<UUID, DataLibraryEntry> values = entries.get(type);
        DataLibraryEntry removed;
        if (values == null || (removed = values.remove(id)) == null) return false;
        LinkedHashMap<String, UUID> typeIndex = idsByKey.get(type);
        if (typeIndex != null) typeIndex.remove(removed.key(), id);
        if (values.isEmpty()) {
            entries.remove(type);
            idsByKey.remove(type);
        }
        return true;
    }

    public boolean remove(DataLibraryEntryKey key) {
        return remove(key.type(), key.id());
    }

    public int removeAll(Iterable<DataLibraryEntryKey> keys) {
        int removed = 0;
        for (DataLibraryEntryKey key : keys) {
            if (remove(key)) removed++;
        }
        return removed;
    }

    public DataLibraryDocument copy() {
        DataLibraryDocument copy = new DataLibraryDocument();
        entries.forEach((type, values) -> values.values().forEach(entry -> copy.put(type,
                new DataLibraryEntry(entry.id(), entry.key(), GraphValueSnapshot.snapshot(entry.value())))));
        return copy;
    }

    public int size() {
        return entries.values().stream().mapToInt(Map::size).sum();
    }
}
