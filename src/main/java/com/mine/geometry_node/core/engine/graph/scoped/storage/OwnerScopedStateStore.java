package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.config.ScopedStateServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Typed OWNER scoped state serialized independently from blueprint attributes. */
public final class OwnerScopedStateStore {
    private boolean updating;
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
        return bucket != null ? bucket.get(name, registries, location(namespace, name)) : null;
    }

    public void put(String name, Object value, int maxEntries,
                    HolderLookup.Provider registries) {
        put(ScopedStateNamespace.PUBLIC, name, value, maxEntries, registries, () -> {});
    }

    public void put(ScopedStateNamespace namespace, String name, Object value,
                    int maxEntries, HolderLookup.Provider registries,
                    Runnable limitNotifier) {
        update(namespace, current -> {
            Map<String, Object> next = new java.util.LinkedHashMap<>(current);
            next.put(name, value);
            return next;
        }, maxEntries, registries, limitNotifier);
    }

    public Map<String, Object> readValues(ScopedStateNamespace namespace, HolderLookup.Provider registries) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket == null ? Map.of() : bucket.readValues(registries, locationPrefix(namespace));
    }

    public boolean update(ScopedStateNamespace namespace, UnaryOperator<Map<String, Object>> transformation,
                          int maxEntries, HolderLookup.Provider registries, Runnable limitNotifier) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
        updating = true;
        try {
            PersistentScopedStateBucket bucket = buckets.get(namespace);
            if (bucket == null) bucket = new PersistentScopedStateBucket();
            int oldSize = bucket.size();
            boolean changed = bucket.update(transformation, Math.min(maxEntries, HARD_MAX_RECORDS), registries, locationPrefix(namespace), () -> {});
            if (changed) {
                buckets.put(namespace, bucket);
                if (oldSize < Math.min(maxEntries, HARD_MAX_RECORDS) && bucket.size() == Math.min(maxEntries, HARD_MAX_RECORDS))
                    PersistentScopedStateBucket.notifyCommitted(limitNotifier);
            }
            return changed;
        } finally { updating = false; }
    }

    public boolean remove(String name) {
        return remove(ScopedStateNamespace.PUBLIC, name);
    }

    public boolean remove(ScopedStateNamespace namespace, String name) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        if (bucket == null || !bucket.remove(name)) return false;
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

    public boolean isEmpty() { return buckets.values().stream().allMatch(PersistentScopedStateBucket::isEmpty); }

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
                ? bucket.entries(registries, locationPrefix(namespace), limit) : Map.of();
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
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
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
            // Decode lazily: addon codecs may register after attachment loading.
            if (!bucket.isEmpty()) buckets.put(namespace, bucket);
        });
    }

    private static String location(ScopedStateNamespace namespace, String name) {
        return locationPrefix(namespace) + name;
    }

    private static String locationPrefix(ScopedStateNamespace namespace) {
        return namespace.serializedName() + "/OWNER/";
    }
}
