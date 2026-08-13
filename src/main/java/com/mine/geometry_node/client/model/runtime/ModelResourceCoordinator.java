package com.mine.geometry_node.client.model.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelResourceCoordinator implements AutoCloseable {
    private final Loader loader;
    private final Map<LocalModelAssetRequest, Entry> entries = new HashMap<>();
    private boolean closed;

    public ModelResourceCoordinator(Loader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public ClientModelResourceLease acquire(LocalModelAssetRequest request) {
        Objects.requireNonNull(request, "request");
        Entry entry;
        boolean start = false;
        synchronized (this) {
            if (closed) throw new IllegalStateException("model resource coordinator is closed");
            entry = entries.get(request);
            if (entry == null) {
                entry = new Entry(request);
                entries.put(request, entry);
                start = true;
            }
            entry.references++;
        }
        if (start) start(entry);
        return new ClientModelResourceLease(this, entry);
    }

    public synchronized int entryCount() { return entries.size(); }

    private void start(Entry entry) {
        CompletableFuture<LoadedModelResource> loading;
        try {
            loading = loader.load(entry.request, entry.cancelled::get);
        } catch (Throwable throwable) {
            loading = CompletableFuture.failedFuture(throwable);
        }
        loading.whenComplete((resource, failure) -> {
            boolean dispose;
            synchronized (this) {
                dispose = closed || entry.cancelled.get() || entries.get(entry.request) != entry;
                if (failure != null) entries.remove(entry.request, entry);
                else if (!dispose) entry.loaded = resource;
            }
            if (failure != null) entry.resource.completeExceptionally(failure);
            else {
                entry.resource.complete(resource);
                if (dispose) resource.release();
            }
        });
    }

    void release(Entry entry) {
        LoadedModelResource resource;
        synchronized (this) {
            if (entry.references <= 0) throw new IllegalStateException("model resource lease underflow");
            entry.references--;
            if (entry.references == 0 && entries.remove(entry.request, entry)) {
                entry.cancelled.set(true);
                resource = entry.loaded;
            } else {
                resource = null;
            }
        }
        if (resource != null) resource.release();
    }

    @Override public void close() {
        List<Entry> removed;
        synchronized (this) {
            if (closed) return;
            closed = true;
            removed = List.copyOf(entries.values());
            entries.clear();
            removed.forEach(entry -> entry.cancelled.set(true));
        }
        for (Entry entry : removed) {
            if (entry.loaded != null) entry.loaded.release();
        }
    }

    public interface Loader {
        CompletableFuture<LoadedModelResource> load(LocalModelAssetRequest request, Cancellation cancellation);
    }

    @FunctionalInterface public interface Cancellation { boolean isCancelled(); }

    static final class Entry {
        private final LocalModelAssetRequest request;
        private final CompletableFuture<LoadedModelResource> resource = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private LoadedModelResource loaded;
        private int references;

        private Entry(LocalModelAssetRequest request) { this.request = request; }
        CompletableFuture<LoadedModelResource> resource() { return resource; }
    }
}
