package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.metadata.DataValuePolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.function.UnaryOperator;

/** Shared CRUD, limit, revision, snapshot and NBT logic for one persistent scope bucket. */
final class PersistentScopedStateBucket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean updating;
    private final Map<String, PersistentScopedStateEntry> entries = new LinkedHashMap<>();
    private long revision;

    @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries,
                                   String location) {
        PersistentScopedStateEntry stored = entries.get(name);
        if (stored == null) return null;
        try {
            return stored.read(registries, location);
        } catch (RuntimeException exception) {
            // A codec may be supplied by an addon that is currently absent. Keep its saved bytes.
            reportCorruptEntry(location, exception);
            return null;
        }
    }

    /** A detached staging view. Undecodable records block mutation rather than losing saved bytes. */
    Map<String, Object> readValues(HolderLookup.Provider registries, String locationPrefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        entries.forEach((name, entry) -> {
            result.put(name, entry.read(registries, locationPrefix + name).value());
        });
        return Collections.unmodifiableMap(result);
    }

    /** Prepare every value/codec before changing records, revision or owner notifications. */
    boolean update(UnaryOperator<Map<String, Object>> transformation, int maxEntries,
                   HolderLookup.Provider registries, String locationPrefix, Runnable limitNotifier) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
        updating = true;
        try {
            return prepareAndCommit(transformation, maxEntries, registries, locationPrefix, limitNotifier);
        } finally { updating = false; }
    }

    private boolean prepareAndCommit(UnaryOperator<Map<String, Object>> transformation, int maxEntries,
                                     HolderLookup.Provider registries, String locationPrefix, Runnable limitNotifier) {
        Objects.requireNonNull(transformation, "transformation");
        Objects.requireNonNull(limitNotifier, "limitNotifier");
        DataValuePolicy policy = GraphMetadataValuePolicy.scoped(registries);
        Map<String, Object> current = readValues(registries, locationPrefix);
        Map<String, Object> candidate = DataDictionary.snapshot(
                Objects.requireNonNull(transformation.apply(DataDictionary.snapshot(current, policy)), "transformation result"), policy);
        if (candidate.size() > maxEntries && candidate.size() > current.size())
            throw new ScopedStateAccessException("Scoped-state namespace entry limit exceeded: " + maxEntries);

        Map<String, PersistentScopedStateEntry> staged = new LinkedHashMap<>();
        boolean changed = entries.size() != candidate.size();
        for (Map.Entry<String, Object> item : candidate.entrySet()) {
            String name = item.getKey();
            PersistentScopedStateEntry previous = entries.get(name);
            Object before = current.get(name);
            if (previous != null && before != null && policy.equivalent(before, item.getValue())) {
                staged.put(name, previous);
            } else {
                GraphValueSnapshot.FrozenValue frozen = GraphValueSnapshot.freeze(item.getValue());
                Tag encoded = ScopedStateValueCodec.encode(frozen.value(), registries, locationPrefix + name);
                staged.put(name, PersistentScopedStateEntry.written(encoded, frozen));
                changed = true;
            }
        }
        if (!changed) return false;
        int oldSize = entries.size();
        entries.clear();
        entries.putAll(staged);
        revision++;
        if (oldSize < maxEntries && entries.size() == maxEntries) notifyCommitted(limitNotifier);
        return true;
    }

    boolean remove(String name) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
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
        Iterator<Map.Entry<String, PersistentScopedStateEntry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PersistentScopedStateEntry> item = iterator.next();
            String name = item.getKey();
            String location = locationPrefix + name;
            try {
                result.put(name, item.getValue().read(registries, location));
            } catch (RuntimeException exception) {
                reportCorruptEntry(location, exception);
            }
            if (result.size() >= limit) break;
        }
        return Collections.unmodifiableMap(result);
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
                LOGGER.warn("Removed malformed persisted scoped-state entry from {}",
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
                    LOGGER.warn(
                            "Removed persisted scoped-state entry exceeding the hard limit: {}{}",
                            locationPrefix, name);
                }
            } else {
                discarded = true;
                LOGGER.warn("Removed persisted scoped-state entry without a value: {}{}",
                        locationPrefix, tag.getStringOr("Name", ""));
            }
        }
        return discarded;
    }

    static void notifyCommitted(Runnable notification) {
        try { notification.run(); }
        catch (RuntimeException failure) { LOGGER.warn("Scoped metadata committed; notification failed", failure); }
    }

    private static void reportCorruptEntry(String location, RuntimeException exception) {
        LOGGER.warn("Cannot decode persisted scoped-state entry {}; retaining saved data: {}",
                location, exception.getMessage());
    }

}
