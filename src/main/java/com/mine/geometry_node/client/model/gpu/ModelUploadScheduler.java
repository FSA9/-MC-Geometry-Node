package com.mine.geometry_node.client.model.gpu;

import java.util.ArrayDeque;
import java.util.Objects;

/** Render-thread scheduler for transactional model upload work. */
public final class ModelUploadScheduler implements AutoCloseable {
    public static final long DEFAULT_FRAME_BYTES = 8L << 20;
    public static final int DEFAULT_FRAME_OBJECTS = 4;
    public static final long DEFAULT_FRAME_NANOS = 2_000_000L;

    private final RenderThreadDispatcher renderThread;
    private final long frameBytes;
    private final int frameObjects;
    private final long frameNanos;
    private final ArrayDeque<WorkItem> queue = new ArrayDeque<>();
    private boolean closed;
    private long uploadedBytes;
    private long uploadNanos;
    private long completedItems;
    private long cancelledItems;
    private long failedItems;
    private long lastFrameBytes;
    private int lastFrameObjects;
    private long lastFrameNanos;

    public ModelUploadScheduler(RenderThreadDispatcher renderThread) {
        this(renderThread, DEFAULT_FRAME_BYTES, DEFAULT_FRAME_OBJECTS, DEFAULT_FRAME_NANOS);
    }

    ModelUploadScheduler(RenderThreadDispatcher renderThread, long frameBytes, int frameObjects, long frameNanos) {
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        if (frameBytes < 1 || frameObjects < 1 || frameNanos < 1) {
            throw new IllegalArgumentException("upload scheduler budgets must be positive");
        }
        this.frameBytes = frameBytes;
        this.frameObjects = frameObjects;
        this.frameNanos = frameNanos;
    }

    public synchronized boolean enqueue(WorkItem item) {
        Objects.requireNonNull(item, "item");
        if (closed) return false;
        queue.addLast(item);
        return true;
    }

    public void pump() {
        renderThread.assertRenderThread();
        long started = System.nanoTime();
        long bytes = 0;
        long nanos = 0;
        int objects = 0;
        boolean progressed = false;
        while (true) {
            WorkItem item;
            synchronized (this) { item = queue.pollFirst(); }
            if (item == null) break;
            if (item.cancelled()) {
                cancel(item);
                continue;
            }
            long nextBytes = Math.max(0, item.nextBytes());
            int nextObjects = Math.max(0, item.nextObjects());
            boolean budgetExceeded = nextBytes > frameBytes - bytes || nextObjects > frameObjects - objects;
            if (progressed && (budgetExceeded || System.nanoTime() - started >= frameNanos)) {
                synchronized (this) { queue.addFirst(item); }
                break;
            }
            long stepStarted = System.nanoTime();
            boolean succeeded = false;
            try {
                boolean complete = item.runStep();
                bytes = saturatedAdd(bytes, nextBytes);
                objects = Math.min(Integer.MAX_VALUE, objects + nextObjects);
                progressed = true;
                succeeded = true;
                if (complete) {
                    item.completed();
                    synchronized (this) { completedItems++; }
                } else {
                    synchronized (this) { queue.addLast(item); }
                }
            } catch (Throwable failure) {
                try { item.failed(failure); }
                finally { synchronized (this) { failedItems++; } }
            }
            synchronized (this) {
                if (succeeded) uploadedBytes = saturatedAdd(uploadedBytes, nextBytes);
                long stepNanos = System.nanoTime() - stepStarted;
                nanos = saturatedAdd(nanos, stepNanos);
                uploadNanos = saturatedAdd(uploadNanos, stepNanos);
            }
            if (bytes >= frameBytes || objects >= frameObjects || System.nanoTime() - started >= frameNanos) break;
        }
        synchronized (this) {
            lastFrameBytes = bytes;
            lastFrameObjects = objects;
            lastFrameNanos = nanos;
        }
    }

    public synchronized Diagnostics diagnostics() {
        long queuedBytes = 0, stagingBytes = 0;
        int queuedObjects = 0;
        for (WorkItem item : queue) {
            queuedBytes = saturatedAdd(queuedBytes, Math.max(0, item.remainingBytes()));
            stagingBytes = saturatedAdd(stagingBytes, Math.max(0, item.stagingBytes()));
            queuedObjects = Math.min(Integer.MAX_VALUE, queuedObjects + Math.max(0, item.remainingObjects()));
        }
        return new Diagnostics(queue.size(), queuedBytes, queuedObjects, stagingBytes,
                lastFrameBytes, lastFrameObjects, lastFrameNanos,
                uploadedBytes, uploadNanos, completedItems, cancelledItems, failedItems);
    }

    @Override public void close() {
        WorkItem[] removed;
        synchronized (this) {
            if (closed) return;
            closed = true;
            removed = queue.toArray(WorkItem[]::new);
            queue.clear();
        }
        for (WorkItem item : removed) cancel(item);
    }

    private void cancel(WorkItem item) {
        try { item.cancelledByScheduler(); }
        finally { synchronized (this) { cancelledItems++; } }
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public interface WorkItem {
        long nextBytes();
        int nextObjects();
        boolean cancelled();
        boolean runStep();
        void completed();
        void cancelledByScheduler();
        void failed(Throwable failure);
        default long remainingBytes() { return nextBytes(); }
        default int remainingObjects() { return nextObjects(); }
        default long stagingBytes() { return remainingBytes(); }
    }

    public record Diagnostics(int queuedItems, long queuedBytes, int queuedObjects, long stagingBytes,
                              long lastFrameBytes, int lastFrameObjects, long lastFrameNanos,
                              long uploadedBytes, long uploadNanos,
                              long completedItems, long cancelledItems, long failedItems) {}
}
