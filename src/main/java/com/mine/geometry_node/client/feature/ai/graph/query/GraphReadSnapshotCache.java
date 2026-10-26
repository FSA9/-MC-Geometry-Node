package com.mine.geometry_node.client.feature.ai.graph.query;

import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheExecutors;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.cache.CacheStats;
import com.mine.geometry_node.core.cache.ManagedCache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Bounded revision cache for MCP graph reads. The editor thread captures serialized documents;
 * query workers materialize immutable indexes on demand.
 */
public final class GraphReadSnapshotCache implements AutoCloseable {
    private static final int MAX_REVISIONS = 16;

    public record Key(String sessionId, String scopeId, long revision) {
        public Key {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(scopeId, "scopeId");
        }
    }

    public static final class Handle {
        private final Entry entry;

        private Handle(Entry entry) {
            this.entry = entry;
        }

        public GraphReadSnapshot resolve() { return entry.resolve(); }
    }

    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;
    private final CacheRegistry.Registration cacheRegistration = CacheRegistry.GLOBAL.register(
            new CacheDescriptor("geometry_node:ai.graph_read_snapshots", "geometry_node.cache.ai.graph_read_snapshots",
                    CacheDescriptor.Domain.CLIENT, CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.OPERATION),
            new ManagedCache() {
                @Override public java.util.concurrent.CompletionStage<CacheStats> stats() {
                    return CacheExecutors.submit(GraphReadSnapshotCache.this::cacheStats);
                }
                @Override public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                    if (!supports(request)) return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Graph snapshot cache does not support scoped clearing"));
                    return CacheExecutors.run(GraphReadSnapshotCache.this::clearCachedSnapshots);
                }
            });

    private synchronized CacheStats cacheStats() { return CacheStats.memory(entries.size(), -1); }

    /** Existing query handles retain immutable captured documents; only future lookup reuse is evicted. */
    synchronized void clearCachedSnapshots() { entries.clear(); }

    /** Reuses a revision without asking the editor to serialize the document again. */
    public synchronized Handle find(Key key) {
        if (closed) throw new IllegalStateException("graph read snapshot cache is closed");
        Entry entry = entries.get(Objects.requireNonNull(key, "key"));
        return entry == null ? null : new Handle(entry);
    }

    /** Accepts a document already captured by the owner of the editor model. */
    public synchronized Handle capture(Key key, GraphReadSnapshot.CapturedDocument document) {
        if (closed) throw new IllegalStateException("graph read snapshot cache is closed");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(document, "document");
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(document);
            entries.put(key, entry);
            trim();
        }
        return new Handle(entry);
    }

    @Override
    public synchronized void close() {
        closed = true;
        entries.clear();
        cacheRegistration.close();
    }

    private void trim() {
        while (entries.size() > MAX_REVISIONS) {
            Key eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    private static final class Entry {
        private final GraphReadSnapshot.CapturedDocument document;
        private final CompletableFuture<GraphReadSnapshot> snapshot = new CompletableFuture<>();
        private boolean building;

        private Entry(GraphReadSnapshot.CapturedDocument document) {
            this.document = document;
        }

        GraphReadSnapshot resolve() {
            boolean buildHere;
            synchronized (this) {
                buildHere = !building && !snapshot.isDone();
                if (buildHere) building = true;
            }
            if (buildHere) {
                try {
                    snapshot.complete(GraphReadSnapshot.build(document));
                } catch (RuntimeException failure) {
                    snapshot.completeExceptionally(failure);
                }
            }
            try {
                return snapshot.join();
            } catch (CompletionException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw failure;
            }
        }
    }
}
