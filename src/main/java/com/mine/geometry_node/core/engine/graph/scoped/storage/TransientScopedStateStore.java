package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Namespace-aware scoped state whose lifetime is owned by a runtime resource. */
public final class TransientScopedStateStore {
    private boolean updating;
    private final Map<ScopedStateNamespace, PersistentScopedStateBucket> buckets =
            new EnumMap<>(ScopedStateNamespace.class);

    public @Nullable ScopedStateEntry get(ScopedStateNamespace namespace, String name,
                                          HolderLookup.Provider registries, String location) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.get(name, registries, location) : null;
    }

    public void put(ScopedStateNamespace namespace, String name, Object value, int maxEntries,
                    HolderLookup.Provider registries, String locationPrefix,
                    Runnable limitNotifier) {
        update(namespace, current -> {
            Map<String, Object> next = new java.util.LinkedHashMap<>(current);
            next.put(name, value);
            return next;
        }, maxEntries, registries, locationPrefix, limitNotifier);
    }

    public Map<String, Object> readValues(ScopedStateNamespace namespace, HolderLookup.Provider registries,
                                          String locationPrefix) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket == null ? Map.of() : bucket.readValues(registries, locationPrefix);
    }

    public boolean update(ScopedStateNamespace namespace, UnaryOperator<Map<String, Object>> transformation,
                          int maxEntries, HolderLookup.Provider registries, String locationPrefix,
                          Runnable limitNotifier) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
        updating = true;
        try {
            PersistentScopedStateBucket bucket = buckets.get(namespace);
            if (bucket == null) bucket = new PersistentScopedStateBucket();
            int oldSize = bucket.size();
            boolean changed = bucket.update(transformation, maxEntries, registries, locationPrefix, () -> {});
            if (changed) {
                buckets.put(namespace, bucket);
                if (oldSize < maxEntries && bucket.size() == maxEntries)
                    PersistentScopedStateBucket.notifyCommitted(limitNotifier);
            }
            return changed;
        } finally { updating = false; }
    }

    public boolean remove(ScopedStateNamespace namespace, String name) {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        if (bucket == null || !bucket.remove(name)) return false;
        return true;
    }

    public boolean hasRecord(ScopedStateNamespace namespace, String name) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null && bucket.hasRecord(name);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                 HolderLookup.Provider registries,
                                                 String locationPrefix, int limit) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.entries(registries, locationPrefix, limit) : Map.of();
    }

    public long revision(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.revision() : 0L;
    }

    public int size(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.size() : 0;
    }

}
