package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Shared CRUD, limit, revision, snapshot and NBT logic for one persistent scope bucket. */
final class PersistentScopedStateBucket {
    private final Map<String, PersistentScopedStateEntry> entries = new LinkedHashMap<>();
    private long revision;

    @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries,
                                   String location, Runnable corruptionListener) {
        PersistentScopedStateEntry stored = entries.get(name);
        if (stored == null) return null;
        try {
            return stored.read(registries, location);
        } catch (RuntimeException exception) {
            if (entries.remove(name, stored)) {
                revision++;
                reportCorruptEntry(location, exception);
                corruptionListener.run();
            }
            return null;
        }
    }

    boolean put(String name, Object value, int maxEntries,
                HolderLookup.Provider registries, String locationPrefix,
                Runnable limitNotifier, Runnable corruptionListener) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(limitNotifier, "limitNotifier");
        Objects.requireNonNull(corruptionListener, "corruptionListener");
        PersistentScopedStateEntry previous = entries.get(name);
        if (previous == null && entries.size() >= maxEntries) {
            removeCorruptEntries(registries, locationPrefix, corruptionListener);
        }
        if (previous == null && entries.size() >= maxEntries) {
            limitNotifier.run();
            throw new ScopedStateAccessException(
                    "Scoped-state namespace entry limit exceeded: " + maxEntries);
        }

        GraphValueSnapshot.FrozenValue frozen = GraphValueSnapshot.freeze(value);
        if (previous != null && previous.equivalentTo(
                frozen, registries, locationPrefix + name)) {
            return false;
        }

        Tag encoded = ScopedStateValueCodec.encode(
                frozen.value(), registries, locationPrefix + name);
        PersistentScopedStateEntry stored = PersistentScopedStateEntry.written(encoded, frozen);
        entries.put(name, stored);
        revision++;
        if (previous == null && entries.size() == maxEntries) limitNotifier.run();
        return true;
    }

    boolean remove(String name) {
        if (entries.remove(name) == null) return false;
        revision++;
        return true;
    }

    boolean hasRecord(String name) {
        return entries.containsKey(name);
    }

    long revision() {
        return revision;
    }

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    Map<String, ScopedStateEntry> entries(HolderLookup.Provider registries,
                                           String locationPrefix, int limit,
                                           Runnable corruptionListener) {
        if (limit <= 0 || entries.isEmpty()) return Map.of();
        Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, PersistentScopedStateEntry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PersistentScopedStateEntry> item = iterator.next();
            String name = item.getKey();
            String location = locationPrefix + name;
            try {
                result.put(name, item.getValue().read(registries, location));
            } catch (RuntimeException exception) {
                iterator.remove();
                revision++;
                reportCorruptEntry(location, exception);
                corruptionListener.run();
            }
            if (result.size() >= limit) break;
        }
        return Map.copyOf(result);
    }

    ListTag saveEntries() {
        ListTag serialized = new ListTag();
        for (Map.Entry<String, PersistentScopedStateEntry> item : entries.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", item.getKey());
            tag.put("Value", item.getValue().encodedCopy());
            serialized.add(tag);
        }
        return serialized;
    }

    boolean loadEntries(Iterable<? extends Tag> serialized, int hardLimit,
                        String locationPrefix) {
        boolean discarded = false;
        for (Tag raw : serialized) {
            if (!(raw instanceof CompoundTag tag)) {
                discarded = true;
                GeometryNode.LOGGER.warn("Removed malformed persisted scoped-state entry from {}",
                        locationPrefix);
                continue;
            }
            Tag encoded = tag.get("Value");
            if (encoded != null) {
                String name = tag.getStringOr("Name", "");
                if (entries.size() < hardLimit || entries.containsKey(name)) {
                    entries.put(name, PersistentScopedStateEntry.loaded(encoded));
                } else {
                    discarded = true;
                    GeometryNode.LOGGER.warn(
                            "Removed persisted scoped-state entry exceeding the hard limit: {}{}",
                            locationPrefix, name);
                }
            } else {
                discarded = true;
                GeometryNode.LOGGER.warn("Removed persisted scoped-state entry without a value: {}{}",
                        locationPrefix, tag.getStringOr("Name", ""));
            }
        }
        return discarded;
    }

    void removeCorruptEntries(HolderLookup.Provider registries,
                              String locationPrefix, Runnable corruptionListener) {
        Iterator<Map.Entry<String, PersistentScopedStateEntry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PersistentScopedStateEntry> item = iterator.next();
            String location = locationPrefix + item.getKey();
            try {
                item.getValue().validate(registries, location);
            } catch (RuntimeException exception) {
                iterator.remove();
                revision++;
                reportCorruptEntry(location, exception);
                corruptionListener.run();
            }
        }
    }

    private static void reportCorruptEntry(String location, RuntimeException exception) {
        GeometryNode.LOGGER.warn("Removed corrupt persisted scoped-state entry {}: {}",
                location, exception.getMessage());
    }

}
