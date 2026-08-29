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

    private final PriorityQueue<QueueEntry<K, V>> queue = new PriorityQueue<>(Comparator
            .comparingLong((QueueEntry<K, V> entry) -> entry.scheduled().dueTick())
            .thenComparingLong(QueueEntry::sequence));
    private final Map<K, QueueEntry<K, V>> active = new HashMap<>();
    private long sequence;

    public boolean schedule(K key, V value, long dueTick) {
        return schedule(key, value, dueTick, false);
    }

    /**
     * Replaces the active entry even when its value and due tick are unchanged,
     * moving it behind entries already scheduled for the same tick.
     */
    public void scheduleReplacing(K key, V value, long dueTick) {
        schedule(key, value, dueTick, true);
    }

    private boolean schedule(K key, V value, long dueTick, boolean replaceUnchanged) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Scheduler input and value cannot be null");
        }

        QueueEntry<K, V> current = active.get(key);
        if (!replaceUnchanged && current != null
                && current.scheduled().value() == value && current.scheduled().dueTick() == dueTick) {
            return false;
        }

        Scheduled<K, V> scheduled = new Scheduled<>(key, value, dueTick);
        QueueEntry<K, V> entry = new QueueEntry<>(scheduled, ++sequence);
        active.put(key, entry);
        queue.offer(entry);
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
        QueueEntry<K, V> entry = queue.peek();
        Scheduled<K, V> scheduled = entry != null ? entry.scheduled() : null;
        if (scheduled == null || scheduled.dueTick() > currentTick) {
            return null;
        }

        queue.poll();
        active.remove(scheduled.key(), entry);
        return scheduled;
    }

    public long nextDueTick() {
        discardStaleEntries();
        QueueEntry<K, V> entry = queue.peek();
        return entry != null ? entry.scheduled().dueTick() : Long.MAX_VALUE;
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

    public boolean contains(K key) {
        return active.containsKey(key);
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    int queuedEntryCount() {
        return queue.size();
    }

    private void discardStaleEntries() {
        while (!queue.isEmpty()) {
            QueueEntry<K, V> entry = queue.peek();
            if (active.get(entry.scheduled().key()) == entry) {
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

    private record QueueEntry<K, V>(Scheduled<K, V> scheduled, long sequence) {
    }
}
