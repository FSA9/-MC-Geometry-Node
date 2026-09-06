package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Typed OWNER scoped state serialized independently from blueprint attributes. */
public final class OwnerScopedStateStore {
    private static final int HARD_MAX_RECORDS = ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final int HARD_MAX_TOTAL_RECORDS =
            HARD_MAX_RECORDS * ScopedStateNamespace.values().length;
    private final Map<ScopedStateNamespace, PersistentScopedStateBucket> buckets =
            new EnumMap<>(ScopedStateNamespace.class);

    public @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries) {
        return get(ScopedStateNamespace.PUBLIC, name, registries);
    }

    public @Nullable ScopedStateEntry get(ScopedStateNamespace namespace, String name,
                                          HolderLookup.Provider registries) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.get(name, registries, location(namespace, name),
                () -> removeEmptyBucket(namespace, bucket)) : null;
    }

    public void put(String name, Object value, int maxEntries,
                    HolderLookup.Provider registries) {
        put(ScopedStateNamespace.PUBLIC, name, value, maxEntries, registries, () -> {});
    }

    public void put(ScopedStateNamespace namespace, String name, Object value,
                    int maxEntries, HolderLookup.Provider registries,
                    Runnable limitNotifier) {
        int limit = Math.min(maxEntries, HARD_MAX_RECORDS);
        PersistentScopedStateBucket bucket = buckets.computeIfAbsent(
                namespace, ignored -> new PersistentScopedStateBucket());
        try {
            bucket.put(
                    name, value, limit, registries, locationPrefix(namespace),
                    limitNotifier, () -> {});
        } catch (RuntimeException exception) {
            if (bucket.isEmpty()) buckets.remove(namespace);
            throw exception;
        }
    }

    public boolean remove(String name) {
        return remove(ScopedStateNamespace.PUBLIC, name);
    }

    public boolean remove(ScopedStateNamespace namespace, String name) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        if (bucket == null || !bucket.remove(name)) return false;
        if (bucket.isEmpty()) buckets.remove(namespace);
        return true;
    }

    public long revision(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.revision() : 0L;
    }
    public boolean hasRecord(String name) {
        return hasRecord(ScopedStateNamespace.PUBLIC, name);
    }

    public boolean hasRecord(ScopedStateNamespace namespace, String name) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null && bucket.hasRecord(name);
    }

    public int size() { return size(ScopedStateNamespace.PUBLIC); }

    public int size(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.size() : 0;
    }

    public boolean isEmpty() { return buckets.isEmpty(); }

    public Map<String, ScopedStateEntry> entries(HolderLookup.Provider registries) {
        return entries(ScopedStateNamespace.PUBLIC, registries, Integer.MAX_VALUE);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                  HolderLookup.Provider registries) {
        return entries(namespace, registries, Integer.MAX_VALUE);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                 HolderLookup.Provider registries, int limit) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null
                ? bucket.entries(registries, locationPrefix(namespace), limit,
                        () -> removeEmptyBucket(namespace, bucket)) : Map.of();
    }

    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag serialized = new ListTag();
        for (Map.Entry<ScopedStateNamespace, PersistentScopedStateBucket> bucket
                : buckets.entrySet()) {
            for (Tag raw : bucket.getValue().saveEntries()) {
                CompoundTag tag = ((CompoundTag) raw).copy();
                tag.putString("Namespace", bucket.getKey().serializedName());
                serialized.add(tag);
            }
        }
        root.put("Entries", serialized);
        return root;
    }

    public void load(CompoundTag root, HolderLookup.Provider registries) {
        buckets.clear();
        Map<ScopedStateNamespace, Map<String, CompoundTag>> serializedByNamespace =
                new EnumMap<>(ScopedStateNamespace.class);
        int uniqueEntries = 0;
        for (Tag raw : root.getListOrEmpty("Entries")) {
            if (!(raw instanceof CompoundTag tag) || tag.get("Value") == null) continue;
            ScopedStateNamespace namespace;
            if (!tag.contains("Namespace")) {
                namespace = ScopedStateNamespace.PUBLIC;
            } else {
                namespace = ScopedStateNamespace.fromSerializedName(
                        tag.getStringOr("Namespace", "")).orElse(null);
                if (namespace == null) continue;
            }
            String name = tag.getStringOr("Name", "");
            Map<String, CompoundTag> entries = serializedByNamespace.computeIfAbsent(
                    namespace, ignored -> new java.util.LinkedHashMap<>());
            if (entries.containsKey(name)) {
                entries.put(name, tag.copy());
            } else if (uniqueEntries < HARD_MAX_TOTAL_RECORDS) {
                entries.put(name, tag.copy());
                uniqueEntries++;
            }
        }
        serializedByNamespace.forEach((namespace, entries) -> {
            PersistentScopedStateBucket bucket = new PersistentScopedStateBucket();
            bucket.loadEntries(entries.values(), HARD_MAX_RECORDS, locationPrefix(namespace));
            bucket.removeCorruptEntries(registries, locationPrefix(namespace), () -> {});
            if (!bucket.isEmpty()) buckets.put(namespace, bucket);
        });
    }

    private void removeEmptyBucket(ScopedStateNamespace namespace,
                                   PersistentScopedStateBucket bucket) {
        if (bucket.isEmpty()) buckets.remove(namespace, bucket);
    }

    private static String location(ScopedStateNamespace namespace, String name) {
        return locationPrefix(namespace) + name;
    }

    private static String locationPrefix(ScopedStateNamespace namespace) {
        return namespace.serializedName() + "/OWNER/";
    }
}
