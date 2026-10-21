package com.mine.geometry_node.client.model.render.resource;

import java.util.*;
import java.util.function.Consumer;

/**
 * Deterministic render-thread upload scheduler. A single item larger than the byte budget is allowed
 * when it is the only item, so a valid large asset cannot deadlock the queue; the oversize is reported.
 */
public final class ModelUploadQueue<T> {
    public record Item<T>(T value, long estimatedBytes, int priority, long sequence) {
        public Item {
            Objects.requireNonNull(value);
            if (estimatedBytes < 0 || priority < 0 || sequence < 0) throw new IllegalArgumentException("invalid upload item");
        }
    }
    public record FrameResult(int processed, long bytes, long nanos, int deferred, boolean oversize) {}
    private final PriorityQueue<Item<T>> queue = new PriorityQueue<>(Comparator
            .comparingInt(Item<T>::priority).reversed()
            .thenComparingLong(Item<T>::sequence));
    private long sequence;
    private long enqueuedBytes;
    private long processedItems;
    private long processedBytes;
    private long deferredItems;
    private long oversizeItems;
    private long peakEnqueuedBytes;
    private int peakQueueItems;

    public synchronized void enqueue(T value, long estimatedBytes, int priority) {
        Item<T> item = new Item<>(value, estimatedBytes, priority, sequence++);
        queue.add(item);
        enqueuedBytes = Math.addExact(enqueuedBytes, estimatedBytes);
        peakEnqueuedBytes = Math.max(peakEnqueuedBytes, enqueuedBytes);
        peakQueueItems = Math.max(peakQueueItems, queue.size());
    }
    public synchronized void clear() { queue.clear(); enqueuedBytes = 0; }
    /** Discards obsolete requests immediately, without consuming a later frame's upload budget. */
    public synchronized void removeIf(java.util.function.Predicate<T> predicate) {
        for (var iterator = queue.iterator(); iterator.hasNext();) {
            Item<T> item = iterator.next();
            if (predicate.test(item.value())) {
                iterator.remove();
                enqueuedBytes -= item.estimatedBytes();
            }
        }
    }
    public synchronized int size() { return queue.size(); }
    public synchronized long enqueuedBytes() { return enqueuedBytes; }
    public synchronized long processedItems() { return processedItems; }
    public synchronized long processedBytes() { return processedBytes; }
    public synchronized long deferredItems() { return deferredItems; }
    public synchronized long oversizeItems() { return oversizeItems; }
    public synchronized long peakEnqueuedBytes() { return peakEnqueuedBytes; }
    public synchronized int peakQueueItems() { return peakQueueItems; }

    /** Processes work on the render thread only. The consumer may throw; the item is then consumed. */
    public FrameResult process(long maxBytes, long maxNanos, Consumer<T> consumer) {
        if (maxBytes < 1 || maxNanos < 1) throw new IllegalArgumentException("invalid frame upload budget");
        Objects.requireNonNull(consumer);
        long started = System.nanoTime();
        int processed = 0;
        long bytes = 0;
        boolean oversize = false;
        while (true) {
            Item<T> item;
            synchronized (this) { item = queue.peek(); }
            if (item == null) break;
            boolean first = processed == 0;
            if (!first && (bytes + item.estimatedBytes() > maxBytes || System.nanoTime() - started >= maxNanos)) break;
            synchronized (this) {
                item = queue.poll();
                if (item == null) continue;
                enqueuedBytes -= item.estimatedBytes();
            }
            if (item.estimatedBytes() > maxBytes) { oversize = true; synchronized (this) { oversizeItems++; } }
            consumer.accept(item.value());
            processed++;
            bytes = Math.addExact(bytes, item.estimatedBytes());
            synchronized (this) { processedItems++; processedBytes = Math.addExact(processedBytes, item.estimatedBytes()); }
            if (!first && System.nanoTime() - started >= maxNanos) break;
        }
        synchronized (this) { deferredItems += queue.size(); }
        return new FrameResult(processed, bytes, System.nanoTime() - started, queue.size(), oversize);
    }
}
