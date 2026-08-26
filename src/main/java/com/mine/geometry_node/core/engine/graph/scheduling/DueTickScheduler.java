package com.mine.geometry_node.core.engine.graph.scheduling;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Keyed deadline scheduler with lazy replacement of stale queue entries.
 * Runtime-specific owners remain responsible for ticking and rescheduling
 * their values.
 */
public final class DueTickScheduler<K, V> {
    private static final int MIN_COMPACTION_QUEUE_SIZE = 64;
    private static final int STALE_ENTRY_FACTOR = 4;

    private final PriorityQueue<Scheduled<K, V>> queue =
            new PriorityQueue<>(Comparator.comparingLong(Scheduled::dueTick));
    private final Map<K, Scheduled<K, V>> active = new HashMap<>();

    public boolean schedule(K key, V value, long dueTick) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Scheduler key and value cannot be null");
        }

        Scheduled<K, V> current = active.get(key);
        if (current != null && current.value() == value && current.dueTick() == dueTick) {
            return false;
        }

        Scheduled<K, V> scheduled = new Scheduled<>(key, value, dueTick);
        active.put(key, scheduled);
        queue.offer(scheduled);
        compactIfNeeded();
        return true;
    }

    public boolean cancel(K key) {
        boolean removed = active.remove(key) != null;
        if (removed) compactIfNeeded();
        return removed;
    }

    @Nullable
    public Scheduled<K, V> pollDue(long currentTick) {
        discardStaleEntries();
        Scheduled<K, V> scheduled = queue.peek();
        if (scheduled == null || scheduled.dueTick() > currentTick) {
            return null;
        }

        queue.poll();
        active.remove(scheduled.key(), scheduled);
        return scheduled;
    }

    public long nextDueTick() {
        discardStaleEntries();
        Scheduled<K, V> scheduled = queue.peek();
        return scheduled != null ? scheduled.dueTick() : Long.MAX_VALUE;
    }

    public boolean clear() {
        boolean changed = !active.isEmpty();
        active.clear();
        queue.clear();
        return changed;
    }

    public int activeCount() {
        return active.size();
    }

    int queuedEntryCount() {
        return queue.size();
    }

    private void discardStaleEntries() {
        while (!queue.isEmpty()) {
            Scheduled<K, V> scheduled = queue.peek();
            if (active.get(scheduled.key()) == scheduled) {
                return;
            }
            queue.poll();
        }
    }

    private void compactIfNeeded() {
        int liveCount = active.size();
        int compactThreshold = Math.max(MIN_COMPACTION_QUEUE_SIZE, liveCount * STALE_ENTRY_FACTOR + 16);
        if (queue.size() <= compactThreshold) return;

        queue.clear();
        queue.addAll(active.values());
    }

    public record Scheduled<K, V>(K key, V value, long dueTick) {
    }
}
