package com.mine.geometry_node.core.cache;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded I/O dispatch. Rejection fails the future, never runs I/O on the caller/UI thread. */
public final class CacheExecutors {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "GeometryNode-CacheIO-" + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    static { IO.allowCoreThreadTimeOut(true); }
    private CacheExecutors() {}
    @FunctionalInterface public interface ThrowingRunnable { void run() throws Exception; }
    public static <T> CompletableFuture<T> submit(Callable<T> task) {
        var future = new CompletableFuture<T>();
        try {
            IO.execute(() -> {
                if (future.isCancelled()) return;
                try { future.complete(task.call()); }
                catch (Throwable failure) {
                    future.completeExceptionally(failure);
                    // Preserve fatal JVM/thread termination semantics after notifying waiters.
                    if (failure instanceof VirtualMachineError fatal) throw fatal;
                    if (failure instanceof ThreadDeath fatal) throw fatal;
                }
            });
        } catch (RejectedExecutionException failure) { future.completeExceptionally(failure); }
        return future;
    }
    public static CompletableFuture<Void> run(ThrowingRunnable task) {
        return submit(() -> { task.run(); return null; });
    }
}
