package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.runtime.ModelInstanceId;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Dedicated bounded executor that coalesces queued and running work by model instance. */
public final class HostLightingExecutor implements AutoCloseable {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 512;
    private static final AtomicLong SESSION_IDS = new AtomicLong();

    private final long session = nextSession();
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<ModelInstanceId, Task> latest = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Failure> lastFailure = new AtomicReference<>();
    private final AtomicLong submitted = new AtomicLong(), completed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong(), rejected = new AtomicLong(), failed = new AtomicLong();

    public HostLightingExecutor(String threadName, int concurrency, int queueCapacity) {
        if (threadName == null || threadName.isBlank()) throw new IllegalArgumentException("threadName must not be blank");
        if (concurrency < 1 || queueCapacity < 1) throw new IllegalArgumentException("executor limits must be positive");
        AtomicLong threadIds = new AtomicLong();
        executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, threadName + "-" + threadIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public Ticket submit(ModelInstanceId instanceId, long generation, Work work) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(work, "work");
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        rejectIfClosed();
        Task task = new Task(instanceId, generation, work);
        Task previous = latest.put(instanceId, task);
        if (previous != null && previous.cancel()) {
            executor.remove(previous);
            cancelled.incrementAndGet();
        }
        if (closed.get()) {
            latest.remove(instanceId, task);
            task.cancel();
            rejected.incrementAndGet();
            throw new RejectedExecutionException("lighting executor session " + session + " is closed");
        }
        try {
            executor.execute(task);
            submitted.incrementAndGet();
            return task;
        } catch (RejectedExecutionException exception) {
            latest.remove(instanceId, task);
            task.cancel();
            rejected.incrementAndGet();
            throw exception;
        }
    }

    public void cancel(ModelInstanceId instanceId) {
        Task task = latest.remove(instanceId);
        if (task != null && task.cancel()) {
            executor.remove(task);
            cancelled.incrementAndGet();
        }
    }

    public void cancelAll() {
        for (Task task : latest.values()) {
            if (latest.remove(task.instanceId, task) && task.cancel()) {
                executor.remove(task);
                cancelled.incrementAndGet();
            }
        }
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(submitted.get(), completed.get(), cancelled.get(), rejected.get(), failed.get(),
                executor.getActiveCount(), executor.getQueue().size(), latest.size(), session, closed.get(),
                lastFailure.get());
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cancelAll();
        for (Runnable queued : executor.shutdownNow()) {
            if (queued instanceof Task task && task.cancel()) cancelled.incrementAndGet();
        }
    }

    public long session() { return session; }
    public boolean closed() { return closed.get(); }

    @FunctionalInterface
    public interface Work { void run(Ticket ticket) throws Exception; }

    public interface Ticket {
        ModelInstanceId instanceId();
        long session();
        long generation();
        boolean cancelled();
        boolean sessionActive();
        default void checkCancelled() {
            if (!sessionActive()) {
                throw new java.util.concurrent.CancellationException("lighting work session is no longer active");
            }
        }
    }

    public record Diagnostics(long submitted, long completed, long cancelled, long rejected, long failed,
                              int active, int queued, int trackedInstances, long session, boolean closed,
                              Failure lastFailure) {}

    /** A bounded diagnostic value: it deliberately retains no Throwable or stack trace. */
    public record Failure(ModelInstanceId instanceId, long session, long generation, String type, String message) {
        public Failure {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
        }
    }

    private final class Task implements Runnable, Ticket {
        private final ModelInstanceId instanceId;
        private final long generation;
        private final Work work;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Task(ModelInstanceId instanceId, long generation, Work work) {
            this.instanceId = instanceId;
            this.generation = generation;
            this.work = work;
        }

        @Override public void run() {
            try {
                if (!cancelled()) work.run(this);
                if (!cancelled()) completed.incrementAndGet();
            } catch (java.util.concurrent.CancellationException ignored) {
                // Cancellation is an expected ownership transition, not a solve failure.
            } catch (Exception exception) {
                if (!cancelled()) {
                    lastFailure.set(failure(instanceId, generation, exception));
                    failed.incrementAndGet();
                }
            } finally {
                latest.remove(instanceId, this);
            }
        }

        @Override public ModelInstanceId instanceId() { return instanceId; }
        @Override public long session() { return session; }
        @Override public long generation() { return generation; }
        @Override public boolean cancelled() { return cancelled.get(); }
        @Override public boolean sessionActive() { return !closed.get() && !cancelled(); }
        private boolean cancel() { return cancelled.compareAndSet(false, true); }
    }

    private void rejectIfClosed() {
        if (!closed.get()) return;
        rejected.incrementAndGet();
        throw new RejectedExecutionException("lighting executor session " + session + " is closed");
    }

    private Failure failure(ModelInstanceId instanceId, long generation, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "(no message)";
        if (message.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
        }
        return new Failure(instanceId, session, generation, exception.getClass().getName(), message);
    }

    private static long nextSession() {
        long next = SESSION_IDS.incrementAndGet();
        if (next <= 0) throw new IllegalStateException("lighting executor session id exhausted");
        return next;
    }
}
