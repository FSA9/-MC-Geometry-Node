package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.core.node.document.NodeGraph;
import icyllis.modernui.core.Core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Bounded revision cache for MCP graph reads. The editor thread captures serialized documents;
 * query workers materialize immutable indexes on demand.
 */
final class GraphReadSnapshotCache implements AutoCloseable {
    private static final int MAX_REVISIONS = 16;

    record Key(String sessionId, String scopeId, long revision) {
        Key {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(scopeId, "scopeId");
        }
    }

    static final class Handle {
        private final Entry entry;

        private Handle(Entry entry) {
            this.entry = entry;
        }

        GraphReadSnapshot resolve() { return entry.resolve(); }
    }

    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    /** Must be called while the editor model is owned by the UI thread. */
    synchronized Handle capture(Key key, NodeGraph graph) {
        if (!Core.isOnUiThread()) {
            throw new IllegalStateException("graph read snapshots must be captured on the ModernUI thread");
        }
        if (closed) throw new IllegalStateException("graph read snapshot cache is closed");
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(GraphReadSnapshot.captureDocument(graph));
            entries.put(key, entry);
            trim();
        }
        return new Handle(entry);
    }

    @Override
    public synchronized void close() {
        closed = true;
        entries.clear();
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
