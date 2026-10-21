package com.mine.geometry_node.core.engine.system.model.runtime;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Shared CPU asset ownership. A complete asset key identifies one worker task; independent leases
 * keep its result alive. No client or graphics types are referenced here. A caller must keep its
 * lease open for as long as it reads the result, including queued render snapshots.
 */
public final class ModelRepository<K, A> implements AutoCloseable {
    private final Executor executor;
    private final boolean ownsExecutor;
    private final Consumer<A> disposer;
    private final Map<K, Entry> entries = new HashMap<>();
    // Invalidated revisions can still be leased; account for them until the last owner releases.
    private final Set<Entry> leasedEntries = Collections.newSetFromMap(new IdentityHashMap<>());
    private long nextGeneration, nextSubscriber;
    private boolean closed;

    public ModelRepository(Executor executor, Consumer<A> disposer) { this(executor, disposer, false); }
    public ModelRepository(Executor executor, Consumer<A> disposer, boolean ownsExecutor) {
        this.executor=Objects.requireNonNull(executor); this.disposer=Objects.requireNonNull(disposer);
        this.ownsExecutor=ownsExecutor;
    }

    public ModelLease<A> acquire(K key, ModelAssetLoader<K, A> loader) {
        Objects.requireNonNull(key); Objects.requireNonNull(loader);
        Entry entry; long subscriber; CompletableFuture<A> future = new CompletableFuture<>();
        boolean start; A ready; Throwable failure;
        synchronized (this) {
            if (closed) throw new IllegalStateException("model repository is closed");
            entry=entries.get(key); start=entry==null;
            if (start) { entry=new Entry(key,++nextGeneration,loader); entries.put(key,entry); leasedEntries.add(entry); }
            subscriber=++nextSubscriber; entry.subscribers.put(subscriber,future);
            ready=entry.value; failure=entry.failure;
        }
        Entry captured=entry;
        ModelLease<A> lease=new ModelLease<>(entry.generation, future.minimalCompletionStage(), () -> release(captured,subscriber,future));
        if (ready!=null) future.complete(ready); else if (failure!=null) future.completeExceptionally(failure);
        if (start) schedule(entry);
        return lease;
    }

    public synchronized Optional<ModelLoadState> state(K key) {
        Entry entry=entries.get(key); return entry==null?Optional.empty():Optional.of(entry.state);
    }
    public synchronized int size() { return entries.size(); }
    public synchronized long generation() { return nextGeneration; }
    public synchronized int retainedSize() { return leasedEntries.size(); }

    /** All retained entries have subscribers; user cache cleanup must not cancel their work. */
    public synchronized com.mine.geometry_node.core.cache.CacheStats cacheStats(java.util.function.ToLongFunction<A> bytes) {
        long total = 0;
        for (Entry entry : leasedEntries) if (entry.value != null) total = Math.addExact(total, bytes.applyAsLong(entry.value));
        return new com.mine.geometry_node.core.cache.CacheStats(leasedEntries.size(), total, 0, leasedEntries.size());
    }

    /** Prevents reuse; existing completed leases retain their value until their last close. */
    public void invalidate(K key) {
        Entry entry;
        synchronized (this) { entry=entries.remove(key); }
        if (entry!=null) invalidateEntry(entry);
    }
    public void clear() {
        List<Entry> old;
        synchronized (this) { old=List.copyOf(entries.values()); entries.clear(); }
        old.forEach(this::invalidateEntry);
    }

    private void schedule(Entry entry) {
        try { executor.execute(() -> load(entry)); }
        catch (RejectedExecutionException rejected) { finish(entry,null,rejected); }
    }
    private void load(Entry entry) {
        A value=null; Throwable failure=null;
        try {
            synchronized (this) { if(entry.invalidated)return; entry.state=ModelLoadState.READING; }
            value=Objects.requireNonNull(entry.loader.load(entry.key,entry.cancellation),"model loader returned null");
            entry.cancellation.check();
        } catch (Throwable error) { failure=error; }
        finish(entry,value,failure);
    }
    private void finish(Entry entry,A value,Throwable failure) {
        List<CompletableFuture<A>> consumers; boolean discard;
        synchronized (this) {
            discard=entry.invalidated||entry.subscribers.isEmpty();
            if(discard) { entry.state=ModelLoadState.CANCELLED; consumers=List.of(); }
            else {
                entry.value=failure==null?value:null; entry.failure=failure;
                entry.state=failure==null?ModelLoadState.READY:ModelLoadState.FAILED;
                consumers=List.copyOf(entry.subscribers.values());
            }
        }
        if(discard) { if(value!=null)disposer.accept(value); return; }
        if(failure!=null) { if(value!=null)disposer.accept(value); consumers.forEach(f->f.completeExceptionally(failure)); }
        else consumers.forEach(f->f.complete(value));
    }
    private void release(Entry entry,long subscriber,CompletableFuture<A> future) {
        A retired=null;
        synchronized (this) {
            if(entry.subscribers.remove(subscriber)==null)return;
            if(entry.subscribers.isEmpty()) {
                leasedEntries.remove(entry);
                entries.remove(entry.key,entry); entry.invalidated=true; entry.cancellation.cancel();
                retired=entry.value; entry.value=null; entry.state=ModelLoadState.DISPOSED;
            }
        }
        future.completeExceptionally(new CancellationException("model lease closed"));
        if(retired!=null)disposer.accept(retired);
    }
    private void invalidateEntry(Entry entry) {
        List<CompletableFuture<A>> consumers; A retired=null;
        synchronized (this) {
            entry.invalidated=true; entry.cancellation.cancel();
            consumers=entry.value==null?List.copyOf(entry.subscribers.values()):List.of();
            if(entry.subscribers.isEmpty()) { retired=entry.value; entry.value=null; }
        }
        consumers.forEach(f->f.completeExceptionally(new CancellationException("model asset invalidated")));
        if(retired!=null)disposer.accept(retired);
    }
    @Override public void close() {
        synchronized (this) { if(closed)return; closed=true; }
        clear();
        if(ownsExecutor&&executor instanceof ExecutorService service)service.shutdownNow();
    }

    private final class Entry {
        final K key; final long generation; final ModelAssetLoader<K, A> loader;
        final ModelCancellation cancellation=new ModelCancellation();
        final Map<Long,CompletableFuture<A>> subscribers=new HashMap<>();
        ModelLoadState state=ModelLoadState.REQUESTED; boolean invalidated; A value; Throwable failure;
        Entry(K key,long generation,ModelAssetLoader<K, A> loader) { this.key=key;this.generation=generation;this.loader=loader; }
    }
}
