package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Navigation-owned LRU; callers serialize access and dispose on the owner's thread. */
final class NavigationOwnerCache<K, V> {
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(64, 0.75F, true);
    private final int maxEntries;
    private final long idleNanos;
    private final long sweepNanos;
    private final Predicate<? super V> released;
    private final Consumer<? super V> disposer;
    private boolean swept;
    private long lastSweepNanos;

    NavigationOwnerCache(int maxEntries, long idleNanos, long sweepNanos,
                         Predicate<? super V> released, Consumer<? super V> disposer) {
        if (maxEntries <= 0 || idleNanos <= 0 || sweepNanos <= 0) {
            throw new IllegalArgumentException("Invalid navigation owner cache bounds");
        }
        this.maxEntries = maxEntries;
        this.idleNanos = idleNanos;
        this.sweepNanos = sweepNanos;
        this.released = Objects.requireNonNull(released, "released");
        this.disposer = Objects.requireNonNull(disposer, "disposer");
    }

    V get(K key, long now) {
        if (!swept || now - lastSweepNanos >= sweepNanos) {
            swept = true;
            lastSweepNanos = now;
            removeIf(entry -> expired(entry, now));
        }
        Entry<V> entry = entries.get(key);
        if (entry == null) return null;
        // Throttling unrelated owners must never extend the current owner's lifetime.
        if (expired(entry, now)) {
            entries.remove(key);
            disposer.accept(entry.value);
            return null;
        }
        entry.lastAccessNanos = now;
        return entry.value;
    }

    void put(K key, V value, long now) {
        Objects.requireNonNull(value, "value");
        Entry<V> previous = entries.put(key, new Entry<>(value, now));
        if (previous != null && previous.value != value) disposer.accept(previous.value);
        if (entries.size() > maxEntries) {
            var iterator = entries.values().iterator();
            V eldest = iterator.next().value;
            iterator.remove();
            disposer.accept(eldest);
        }
    }

    void forEach(Consumer<? super V> consumer) {
        entries.values().forEach(entry -> consumer.accept(entry.value));
    }

    void removeOwners(Predicate<? super V> predicate) {
        removeIf(entry -> predicate.test(entry.value));
    }

    void clear() {
        removeIf(entry -> true);
        swept = false;
    }

    private boolean expired(Entry<V> entry, long now) {
        return released.test(entry.value) || now - entry.lastAccessNanos >= idleNanos;
    }

    private void removeIf(Predicate<Entry<V>> predicate) {
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry<V> entry = iterator.next();
            if (!predicate.test(entry)) continue;
            iterator.remove();
            disposer.accept(entry.value);
        }
    }

    private static final class Entry<V> {
        private final V value;
        private long lastAccessNanos;

        private Entry(V value, long lastAccessNanos) {
            this.value = value;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
