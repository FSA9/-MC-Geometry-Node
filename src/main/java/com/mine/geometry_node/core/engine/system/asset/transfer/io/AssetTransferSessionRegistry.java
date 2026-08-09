package com.mine.geometry_node.core.engine.system.asset.transfer.io;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AssetTransferSessionRegistry<T extends AutoCloseable> implements AutoCloseable {
    private final Map<UUID, Entry<T>> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor;
    private final long idleTimeoutNanos;
    private final TimeoutListener timeoutListener;

    public AssetTransferSessionRegistry(String threadName, Duration idleTimeout, TimeoutListener timeoutListener) {
        idleTimeoutNanos = idleTimeout.toNanos();
        this.timeoutListener = timeoutListener != null ? timeoutListener : ignored -> { };
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = Math.max(250L, Math.min(1_000L, idleTimeout.toMillis() / 2L));
        timeoutExecutor.scheduleAtFixedRate(this::expireIdle, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public boolean add(UUID transferId, T session) {
        return entries.putIfAbsent(transferId, new Entry<>(session, System.nanoTime())) == null;
    }

    public T getAndTouch(UUID transferId) {
        Entry<T> entry = entries.get(transferId);
        if (entry == null) return null;
        entry.lastActivityNanos = System.nanoTime();
        return entry.session;
    }

    public T remove(UUID transferId) {
        Entry<T> removed = entries.remove(transferId);
        return removed != null ? removed.session : null;
    }

    public void closeAndRemove(UUID transferId) {
        closeQuietly(remove(transferId));
    }

    public int size() { return entries.size(); }

    private void expireIdle() {
        long deadline = System.nanoTime() - idleTimeoutNanos;
        for (Map.Entry<UUID, Entry<T>> candidate : entries.entrySet()) {
            Entry<T> entry = candidate.getValue();
            if (entry.lastActivityNanos > deadline || !entries.remove(candidate.getKey(), entry)) continue;
            closeQuietly(entry.session);
            timeoutListener.onTimeout(candidate.getKey());
        }
    }

    @Override
    public void close() {
        timeoutExecutor.shutdownNow();
        for (Entry<T> entry : new ArrayList<>(entries.values())) closeQuietly(entry.session);
        entries.clear();
    }

    private static void closeQuietly(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

    private static final class Entry<T> {
        private final T session;
        private volatile long lastActivityNanos;
        private Entry(T session, long lastActivityNanos) {
            this.session = session;
            this.lastActivityNanos = lastActivityNanos;
        }
    }

    @FunctionalInterface public interface TimeoutListener { void onTimeout(UUID transferId); }
}
