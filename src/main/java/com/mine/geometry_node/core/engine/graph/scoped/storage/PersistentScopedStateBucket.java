package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Shared CRUD, limit, revision, snapshot and NBT logic for one persistent scope bucket. */
final class PersistentScopedStateBucket {
    private final Map<String, PersistentScopedStateEntry> entries = new LinkedHashMap<>();
    private long revision;

    @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries,
                                   String location) {
        PersistentScopedStateEntry stored = entries.get(name);
        return stored != null ? stored.read(registries, location) : null;
    }

    boolean put(String name, Object value, int maxEntries,
                HolderLookup.Provider registries, String location,
                Runnable limitNotifier) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(limitNotifier, "limitNotifier");
        PersistentScopedStateEntry previous = entries.get(name);
        if (previous == null && entries.size() >= maxEntries) {
            limitNotifier.run();
            throw new ScopedStateAccessException(
                    "Scoped-state namespace entry limit exceeded: " + maxEntries);
        }

        GraphValueSnapshot.FrozenValue frozen = GraphValueSnapshot.freeze(value);
        if (previous != null && previous.equivalentTo(frozen, registries, location)) {
            return false;
        }

        Tag encoded = ScopedStateValueCodec.encode(frozen.value(), registries, location);
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
                                           String locationPrefix, int limit) {
        if (limit <= 0 || entries.isEmpty()) return Map.of();
        Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
        for (String name : entries.keySet()) {
            try {
                ScopedStateEntry entry = get(name, registries, locationPrefix + name);
                if (entry != null) result.put(name, entry);
            } catch (RuntimeException ignored) {
                // Enumeration is best-effort; direct get retains the diagnostic failure.
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

    void loadEntries(Iterable<Tag> serialized, int hardLimit) {
        for (Tag raw : serialized) {
            if (!(raw instanceof CompoundTag tag)) continue;
            Tag encoded = tag.get("Value");
            if (encoded != null) {
                String name = tag.getStringOr("Name", "");
                if (entries.size() < hardLimit || entries.containsKey(name)) {
                    entries.put(name, PersistentScopedStateEntry.loaded(encoded));
                }
            }
        }
    }

}
