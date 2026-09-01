package com.mine.geometry_node.core.engine.system.data.library;

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

    public void put(PortType type, DataLibraryEntry entry) {
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        entries.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(entry.id(), entry);
    }

    public boolean remove(PortType type, UUID id) {
        LinkedHashMap<UUID, DataLibraryEntry> values = entries.get(type);
        if (values == null || values.remove(id) == null) return false;
        if (values.isEmpty()) entries.remove(type);
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
        entries.forEach((type, values) -> values.values().forEach(entry -> copy.put(type, entry)));
        return copy;
    }

    public int size() {
        return entries.values().stream().mapToInt(Map::size).sum();
    }
}
