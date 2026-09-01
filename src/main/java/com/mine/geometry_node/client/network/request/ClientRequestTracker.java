package com.mine.geometry_node.client.network.request;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Tracks short request/response exchanges across client network features. */
public final class ClientRequestTracker {
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final Map<Integer, PendingRequest<?>> REQUESTS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "GeometryNode-ClientRequest-Timeout");
                thread.setDaemon(true);
                return thread;
            });

    private ClientRequestTracker() {
    }

    public static Group group(String name) {
        return new Group(name);
    }

    public static final class Group {
        private final String name;

        private Group(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public int nextRequestId() {
            return REQUEST_IDS.getAndIncrement();
        }

        public <T> int register(Class<T> responseType, Consumer<? super T> callback) {
            int requestId = nextRequestId();
            register(requestId, responseType, callback);
            return requestId;
        }

        public <T> void register(int requestId, Class<T> responseType, Consumer<? super T> callback) {
            register(requestId, responseType, callback, null);
        }

        public <T> void register(int requestId, Class<T> responseType, Consumer<? super T> callback,
                                 Runnable onTimeout) {
            cancel(requestId);
            PendingRequest<T> pending = new PendingRequest<>(this, responseType, callback, onTimeout);
            PendingRequest<?> existing = REQUESTS.putIfAbsent(requestId, pending);
            if (existing != null) {
                throw new IllegalStateException("Client request ID is already registered: " + requestId);
            }
            pending.setTimeout(TIMEOUT_EXECUTOR.schedule(
                    () -> {
                        if (REQUESTS.remove(requestId, pending)) pending.timeout();
                    },
                    DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            ));
        }

        public <T> boolean complete(int requestId, T response) {
            return complete(requestId, response, ignored -> { });
        }

        public <T> boolean complete(int requestId, T response, Consumer<? super T> beforeCallback) {
            PendingRequest<?> pending = REQUESTS.get(requestId);
            if (pending == null || pending.owner != this || !pending.accepts(response)) return false;
            if (!REQUESTS.remove(requestId, pending)) return false;
            if (beforeCallback != null) beforeCallback.accept(response);
            pending.complete(response);
            return true;
        }

        public void cancel(int requestId) {
            PendingRequest<?> pending = REQUESTS.get(requestId);
            if (pending == null || pending.owner != this) return;
            if (REQUESTS.remove(requestId, pending)) pending.cancelTimeout();
        }

        public void reset() {
            for (Map.Entry<Integer, PendingRequest<?>> entry : REQUESTS.entrySet()) {
                PendingRequest<?> pending = entry.getValue();
                if (pending.owner == this && REQUESTS.remove(entry.getKey(), pending)) {
                    pending.cancelTimeout();
                }
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class PendingRequest<T> {
        private final Group owner;
        private final Class<T> responseType;
        private final Consumer<? super T> callback;
        private final Runnable timeoutCallback;
        private volatile ScheduledFuture<?> timeout;
        private boolean closed;

        private PendingRequest(Group owner, Class<T> responseType, Consumer<? super T> callback,
                               Runnable timeoutCallback) {
            this.owner = owner;
            this.responseType = Objects.requireNonNull(responseType, "responseType");
            this.callback = callback != null ? callback : ignored -> { };
            this.timeoutCallback = timeoutCallback != null ? timeoutCallback : () -> { };
        }

        private boolean accepts(Object response) {
            return responseType.isInstance(response);
        }

        private void complete(Object response) {
            cancelTimeout();
            callback.accept(responseType.cast(response));
        }

        private void timeout() {
            cancelTimeout();
            timeoutCallback.run();
        }

        private synchronized void setTimeout(ScheduledFuture<?> timeout) {
            this.timeout = timeout;
            if (closed) timeout.cancel(false);
        }

        private synchronized void cancelTimeout() {
            closed = true;
            ScheduledFuture<?> current = timeout;
            if (current != null) current.cancel(false);
        }
    }
}
