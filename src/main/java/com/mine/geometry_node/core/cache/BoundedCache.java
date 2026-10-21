package com.mine.geometry_node.core.cache;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Thread-safe weighted LRU with expiration after write and generation-guarded async publication.
 * A successful put transfers ownership; a rejected put leaves ownership with the caller.
 * Disposers run outside the lock on the invoking thread: GPU/UI owners must dispatch themselves.
 * Returned values are not leased; use a business lease adapter for resources held across eviction.
 * Repeated value identities are disposed once after their last cached entry is removed.
 * Objects already submitted for disposal by another operation must never be resubmitted.
 * Weight is charged per entry, even when the value identity is shared.
 */
public final class BoundedCache<K, V> implements AutoCloseable {
    private final int maxEntries;
    private final long maxWeight, ttlNanos;
    private final ToLongFunction<V> weigher;
    private final Consumer<V> disposer;
    private final LongSupplier clock;
    private final LinkedHashMap<K, Value<V>> values = new LinkedHashMap<>(16, .75f, true);
    private final IdentityHashMap<V, Integer> owners = new IdentityHashMap<>();
    private long weight, generation;
    private boolean closed;

    public BoundedCache(int maxEntries, long maxWeight, Duration ttl, ToLongFunction<V> weigher, Consumer<V> disposer) {
        this(maxEntries, maxWeight, ttl, weigher, disposer, System::nanoTime);
    }
    public BoundedCache(int maxEntries, long maxWeight, Duration ttl, ToLongFunction<V> weigher,
                        Consumer<V> disposer, LongSupplier clock) {
        if (maxEntries <= 0 || maxWeight <= 0 || ttl.isNegative()) throw new IllegalArgumentException("Invalid cache bounds");
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
        this.ttlNanos = ttl.toNanos();
        this.weigher = Objects.requireNonNull(weigher);
        this.disposer = Objects.requireNonNull(disposer);
        this.clock = Objects.requireNonNull(clock);
    }
    public V get(K key) {
        List<V> retired = new ArrayList<>();
        V result;
        synchronized (this) {
            expire(retired);
            Value<V> value = values.get(key);
            result = value == null ? null : value.value;
        }
        dispose(retired);
        return result;
    }
    public boolean put(K key, V value) { return put(key, value, null); }
    public boolean putIfCurrent(K key, V value, long expectedGeneration) { return put(key, value, expectedGeneration); }
    public synchronized long generation() { return generation; }

    private boolean put(K key, V value, Long expectedGeneration) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        long valueWeight = weigher.applyAsLong(value);
        if (valueWeight < 0) throw new IllegalArgumentException("Negative cache weight");
        List<V> retired = new ArrayList<>();
        synchronized (this) {
            if (closed || expectedGeneration != null && generation != expectedGeneration || valueWeight > maxWeight)
                return false;
            // Pin the accepted identity before expiry/eviction can retire an older alias.
            owners.merge(value, 1, Integer::sum);
            expire(retired);
            Value<V> previous = values.remove(key);
            if (previous != null) {
                weight -= previous.weight;
                release(previous.value, retired);
            }
            // Subtraction avoids overflow for very large weights.
            while (!values.isEmpty() && (values.size() >= maxEntries || weight > maxWeight - valueWeight)) {
                var iterator = values.entrySet().iterator();
                Value<V> eldest = iterator.next().getValue();
                iterator.remove();
                weight -= eldest.weight;
                release(eldest.value, retired);
            }
            values.put(key, new Value<>(value, valueWeight, clock.getAsLong()));
            weight += valueWeight;
        }
        try { dispose(retired); }
        catch (Throwable error) {
            rethrowFatal(error);
            // Ownership has committed. Do not imply rejection by throwing to the submitting owner.
            System.getLogger(BoundedCache.class.getName()).log(System.Logger.Level.ERROR,
                    "Failed to dispose evicted cache values after accepting a replacement", error);
        }
        return true;
    }
    public void invalidate(K key) {
        List<V> retired = new ArrayList<>();
        synchronized (this) {
            // Conservative: any outstanding asynchronous load must acquire a fresh generation.
            generation++;
            Value<V> old = values.remove(key);
            if (old != null) { weight -= old.weight; release(old.value, retired); }
        }
        dispose(retired);
    }
    public void clear() { clear(false); }
    @Override public void close() { clear(true); }
    private void clear(boolean close) {
        List<V> retired;
        synchronized (this) {
            if (closed) return;
            closed = close;
            generation++;
            retired = new ArrayList<>();
            for (Value<V> value : values.values()) release(value.value, retired);
            values.clear();
            owners.clear();
            weight = 0;
        }
        dispose(retired);
    }
    public int size() { trim(); synchronized (this) { return values.size(); } }
    public long weight() { trim(); synchronized (this) { return weight; } }
    public void trim() {
        List<V> retired = new ArrayList<>();
        synchronized (this) { expire(retired); }
        dispose(retired);
    }
    private void expire(List<V> retired) {
        if (ttlNanos == 0) return;
        long now = clock.getAsLong();
        var iterator = values.values().iterator();
        while (iterator.hasNext()) {
            Value<V> entry = iterator.next();
            if (now - entry.written >= ttlNanos) {
                iterator.remove();
                weight -= entry.weight;
                release(entry.value, retired);
            }
        }
    }
    private void release(V value, List<V> retired) {
        int remaining = owners.get(value) - 1;
        if (remaining == 0) { owners.remove(value); retired.add(value); }
        else owners.put(value, remaining);
    }
    private void dispose(List<V> retired) {
        Throwable failure = null;
        for (V value : retired) {
            try { disposer.accept(value); }
            catch (Throwable error) {
                rethrowFatal(error);
                if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Cache disposal failed", failure);
    }
    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError fatal) throw fatal;
        if (error instanceof ThreadDeath fatal) throw fatal;
    }
    private record Value<V>(V value, long weight, long written) {}
}
