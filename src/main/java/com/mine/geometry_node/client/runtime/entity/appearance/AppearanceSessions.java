package com.mine.geometry_node.client.runtime.entity.appearance;

import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owner-thread resources, deliberately separate from immutable preview snapshot caches. */
final class AppearanceSessions<S, C, K, P> {
    private final class Entry {
        final C context;
        final K key;
        P proxy;
        long touched;
        Entry(C context, K key) { this.context = context; this.key = key; touched = clock.getAsLong(); }
    }

    private final IdentityHashMap<S, Entry> entries = new IdentityHashMap<>();
    private final Consumer<P> disposer;
    private final LongSupplier clock;
    private final long idleNanos;
    private final int capacity;
    private final Set<S> resolving = Collections.newSetFromMap(new IdentityHashMap<>());

    AppearanceSessions(Consumer<P> disposer, LongSupplier clock, long idleNanos, int capacity) {
        this.disposer = disposer;
        this.clock = clock;
        this.idleNanos = idleNanos;
        this.capacity = capacity;
    }

    boolean enter(S source) { return resolving.add(source); }
    void exit(S source) { resolving.remove(source); }

    P acquire(S source, C context, K key, Supplier<P> factory) {
        Entry entry = entries.get(source);
        if (entry != null && (entry.context != context || !Objects.equals(entry.key, key))) {
            remove(source);
            entry = null;
        }
        if (entry == null) {
            // Reject excess demand instead of evicting visible sources and allocating every frame.
            if (entries.size() >= capacity) return null;
            entry = new Entry(context, key);
            entries.put(source, entry); // A failed factory is remembered until spec/lifecycle changes.
            entry.proxy = factory.get();
        }
        entry.touched = clock.getAsLong();
        return entry.proxy;
    }

    void fail(S source) {
        Entry entry = entries.get(source);
        if (entry != null && entry.proxy != null) {
            P proxy = entry.proxy;
            entry.proxy = null;
            disposer.accept(proxy);
        }
    }

    void remove(S source) {
        Entry entry = entries.remove(source);
        if (entry != null && entry.proxy != null) disposer.accept(entry.proxy);
    }

    void sweep(Predicate<S> removed) {
        long now = clock.getAsLong();
        var retired = new java.util.ArrayList<P>();
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var value = iterator.next();
            Entry entry = value.getValue();
            if (removed.test(value.getKey()) || now - entry.touched >= idleNanos) {
                iterator.remove();
                if (entry.proxy != null) retired.add(entry.proxy);
            }
        }
        disposeAll(retired);
    }

    void clearContext(C context) {
        var retired = new java.util.ArrayList<P>();
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.context == context) {
                iterator.remove();
                if (entry.proxy != null) retired.add(entry.proxy);
            }
        }
        disposeAll(retired);
    }

    void clear() {
        var previous = entries.values().stream().map(entry -> entry.proxy).filter(Objects::nonNull).toList();
        entries.clear();
        disposeAll(previous);
    }

    private void disposeAll(List<P> proxies) {
        RuntimeException failure = null;
        for (P proxy : proxies) {
            try { disposer.accept(proxy); }
            catch (RuntimeException error) {
                AppearanceFailures.rethrowFatal(error);
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    int size() { return entries.size(); }
}
