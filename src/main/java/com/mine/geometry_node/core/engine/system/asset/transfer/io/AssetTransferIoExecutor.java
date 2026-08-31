package com.mine.geometry_node.core.engine.system.asset.transfer.io;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AssetTransferIoExecutor implements AutoCloseable {
    private final ExecutorService executor;

    public AssetTransferIoExecutor(String threadPrefix, int threadCount, int queueCapacity) {
        AtomicInteger threadIds = new AtomicInteger();
        executor = new ThreadPoolExecutor(
                Math.max(1, threadCount), Math.max(1, threadCount), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                task -> {
                    Thread thread = new Thread(task, threadPrefix + "-" + threadIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(IoSupplier<T> operation) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return operation.get();
                } catch (Exception exception) {
                    throw new AssetTransferIoException(exception);
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new AssetTransferIoException(exception));
        }
    }

    public CompletableFuture<Void> run(IoRunnable operation) {
        return submit(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface public interface IoSupplier<T> { T get() throws Exception; }
    @FunctionalInterface public interface IoRunnable { void run() throws Exception; }

    public static final class AssetTransferIoException extends RuntimeException {
        private AssetTransferIoException(Throwable cause) { super(cause); }
    }
}
