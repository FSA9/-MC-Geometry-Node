package com.mine.geometry_node.client.network.request;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Tracks short request/response exchanges across client network features. */
public final class ClientRequestTracker {
    private static final long DEFAULT_TIMEOUT_SECONDS = 10L;
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final Map<Object, PendingRequest<?>> REQUESTS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "GeometryNode-ClientRequest-Timeout");
                thread.setDaemon(true);
                return thread;
            });

    private ClientRequestTracker() {
    }

    /** Creates a request group whose success and timeout callbacks use the supplied executor. */
    public static Group group(String name, Executor callbackExecutor) {
        return new Group(name, callbackExecutor);
    }

    public static final class Group {
        private final String name;
        private final Executor callbackExecutor;

        private Group(String name, Executor callbackExecutor) {
            this.name = Objects.requireNonNull(name, "name");
            this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
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
            registerKey(requestId, responseType, callback, onTimeout);
        }

        public <T> void register(UUID requestId, Class<T> responseType, Consumer<? super T> callback,
                                 Runnable onTimeout) {
            registerKey(Objects.requireNonNull(requestId, "requestId"), responseType, callback, onTimeout);
        }

        private <T> void registerKey(Object requestId, Class<T> responseType, Consumer<? super T> callback,
                                     Runnable onTimeout) {
            cancelKey(requestId);
            PendingRequest<T> pending = new PendingRequest<>(
                    this, responseType, callback, onTimeout, callbackExecutor);
            PendingRequest<?> existing = REQUESTS.putIfAbsent(requestId, pending);
            if (existing != null) {
                throw new IllegalStateException("Client request ID is already registered: " + requestId);
            }
            pending.setTimeout(TIMEOUT_EXECUTOR.schedule(
                    () -> {
                        if (REQUESTS.remove(requestId, pending)) {
                            pending.timeout();
                        }
                    },
                    DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            ));
        }

        public <T> boolean complete(int requestId, T response) {
            return complete(requestId, response, ignored -> { });
        }

        public <T> boolean complete(int requestId, T response, Consumer<? super T> beforeCallback) {
            return completeKey(requestId, response, beforeCallback);
        }

        public <T> boolean complete(UUID requestId, T response) {
            return completeKey(Objects.requireNonNull(requestId, "requestId"), response, ignored -> { });
        }

        private <T> boolean completeKey(Object requestId, T response, Consumer<? super T> beforeCallback) {
            PendingRequest<?> pending = REQUESTS.get(requestId);
            if (pending == null || pending.owner != this || !pending.accepts(response)) return false;
            if (!REQUESTS.remove(requestId, pending)) return false;
            Runnable before = beforeCallback == null ? null : () -> beforeCallback.accept(response);
            pending.complete(response, before);
            return true;
        }

        public void cancel(int requestId) {
            cancelKey(requestId);
        }

        public boolean cancel(UUID requestId) {
            return requestId != null && cancelKey(requestId);
        }

        private boolean cancelKey(Object requestId) {
            PendingRequest<?> pending = REQUESTS.get(requestId);
            if (pending == null || pending.owner != this) return false;
            if (!REQUESTS.remove(requestId, pending)) return false;
            pending.cancelTimeout();
            return true;
        }

        public void reset() {
            for (Map.Entry<Object, PendingRequest<?>> entry : REQUESTS.entrySet()) {
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
        private final Executor callbackExecutor;
        private volatile ScheduledFuture<?> timeout;
        private boolean closed;

        private PendingRequest(Group owner, Class<T> responseType, Consumer<? super T> callback,
                               Runnable timeoutCallback, Executor callbackExecutor) {
            this.owner = owner;
            this.responseType = Objects.requireNonNull(responseType, "responseType");
            this.callback = callback != null ? callback : ignored -> { };
            this.timeoutCallback = timeoutCallback != null ? timeoutCallback : () -> { };
            this.callbackExecutor = callbackExecutor;
        }

        private boolean accepts(Object response) {
            return responseType.isInstance(response);
        }

        private void complete(Object response, Runnable beforeCallback) {
            cancelTimeout();
            T accepted = responseType.cast(response);
            callbackExecutor.execute(() -> {
                if (beforeCallback != null) beforeCallback.run();
                callback.accept(accepted);
            });
        }

        private void timeout() {
            cancelTimeout();
            callbackExecutor.execute(timeoutCallback);
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
