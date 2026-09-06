package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Namespace-aware scoped state whose lifetime is owned by a runtime resource. */
public final class TransientScopedStateStore {
    private final Map<ScopedStateNamespace, PersistentScopedStateBucket> buckets =
            new EnumMap<>(ScopedStateNamespace.class);

    public @Nullable ScopedStateEntry get(ScopedStateNamespace namespace, String name,
                                          HolderLookup.Provider registries, String location) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.get(name, registries, location,
                () -> removeEmptyBucket(namespace, bucket)) : null;
    }

    public void put(ScopedStateNamespace namespace, String name, Object value, int maxEntries,
                    HolderLookup.Provider registries, String locationPrefix,
                    Runnable limitNotifier) {
        PersistentScopedStateBucket bucket = buckets.computeIfAbsent(
                namespace, ignored -> new PersistentScopedStateBucket());
        try {
            bucket.put(name, value, maxEntries, registries, locationPrefix,
                    limitNotifier, () -> {});
        } catch (RuntimeException exception) {
            if (bucket.isEmpty()) buckets.remove(namespace);
            throw exception;
        }
    }

    public boolean remove(ScopedStateNamespace namespace, String name) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        if (bucket == null || !bucket.remove(name)) return false;
        if (bucket.isEmpty()) buckets.remove(namespace);
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
        return bucket != null ? bucket.entries(registries, locationPrefix, limit,
                () -> removeEmptyBucket(namespace, bucket)) : Map.of();
    }

    public long revision(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.revision() : 0L;
    }

    public int size(ScopedStateNamespace namespace) {
        PersistentScopedStateBucket bucket = buckets.get(namespace);
        return bucket != null ? bucket.size() : 0;
    }

    private void removeEmptyBucket(ScopedStateNamespace namespace,
                                   PersistentScopedStateBucket bucket) {
        if (bucket.isEmpty()) buckets.remove(namespace, bucket);
    }
}
