package com.mine.geometry_node.client.feature.entity.preview;

import com.mine.geometry_node.core.cache.*;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewLimits;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewTarget;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owner-thread snapshot resolver. T must be immutable; no world, entity or UI objects are retained. */
final class EntityPreviewService<T> implements AutoCloseable {
    static final String CACHE_ID = "geometry_node:entity_preview.snapshots";
    static final String CACHE_LABEL = "geometry_node.cache.entity_preview_snapshots";
    static final int BATCH_SIZE = 8, MAX_PENDING = 128, MAX_INFLIGHT = 4;
    static final long MAX_APPEARANCE_BYTES = 64 * 1024, MAX_RESPONSE_BYTES = 512 * 1024;
    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    static final long REFRESH_NANOS = Duration.ofSeconds(30).toNanos();
    static final long MAX_STALE_NANOS = Duration.ofMinutes(2).toNanos();
    static final long TIMEOUT_NANOS = Duration.ofSeconds(5).toNanos();
    static final long QUEUE_TIMEOUT_NANOS = Duration.ofSeconds(8).toNanos();
    static final long FLUSH_NANOS = EntityPreviewLimits.REQUEST_INTERVAL_NANOS;
    static final long RETRY_NANOS = Duration.ofSeconds(2).toNanos();
    static final long MAX_RETRY_NANOS = Duration.ofSeconds(30).toNanos();

    enum State { LOADING, READY, UNAVAILABLE, ERROR }
    record Result<T>(State state, T template) {}
    record Entry<T>(EntityPreviewTarget target, T template, String message) {}
    record Size(long encodedBytes, long retainedBytes) {}
    @FunctionalInterface interface Transport {
        void send(long requestId, List<EntityPreviewTarget> targets);
    }
    private record Cached<T>(T template, State state, long retryAt, int failures, long bytes, long successAt) {}
    private record Request(List<EntityPreviewTarget> targets, long sentAt, Set<EntityPreviewTarget> superseded) {
        List<EntityPreviewTarget> activeTargets() {
            return targets.stream().filter(target -> !superseded.contains(target)).toList();
        }
    }

    private final T empty;
    private final Predicate<T> isEmpty;
    private final Function<T, Size> measure;
    private final LongSupplier clock;
    private final Transport transport;
    private final BoundedCache<EntityPreviewTarget, Cached<T>> cache;
    private final LinkedHashMap<EntityPreviewTarget, Long> pending = new LinkedHashMap<>();
    private final Map<Long, Request> inflight = new LinkedHashMap<>();
    private final Set<EntityPreviewTarget> busy = new HashSet<>();
    private final CacheRegistry.Registration registration;
    private Object connection;
    private volatile String namespace;
    private volatile long generation;
    private long nextRequest, lastFlush;
    private boolean flushed, closed;

    EntityPreviewService(T empty, Predicate<T> isEmpty, Function<T, Size> measure,
                         LongSupplier clock, Transport transport, Consumer<Runnable> ownerQueue,
                         CacheRegistry registry) {
        this(empty, isEmpty, measure, clock, transport, ownerQueue, registry, 256, 8 * 1024 * 1024);
    }

    EntityPreviewService(T empty, Predicate<T> isEmpty, Function<T, Size> measure,
                         LongSupplier clock, Transport transport, Consumer<Runnable> ownerQueue,
                         CacheRegistry registry, int maxEntries, long maxBytes) {
        this.empty = Objects.requireNonNull(empty);
        this.isEmpty = Objects.requireNonNull(isEmpty);
        this.measure = Objects.requireNonNull(measure);
        this.clock = Objects.requireNonNull(clock);
        this.transport = Objects.requireNonNull(transport);
        Objects.requireNonNull(ownerQueue);
        cache = new BoundedCache<>(maxEntries, maxBytes, CACHE_TTL,
                value -> value.bytes() + 640, ignored -> {}, clock);
        registration = registry.register(new CacheDescriptor(CACHE_ID, CACHE_LABEL,
                CacheDescriptor.Domain.CLIENT, CacheDescriptor.Storage.MEMORY,
                CacheDescriptor.Lifetime.CONNECTION), new ManagedCache() {
            @Override public CompletionStage<CacheStats> stats() {
                var result = new CompletableFuture<CacheStats>();
                dispatch(ownerQueue, result, () -> result.complete(CacheStats.memory(cache.size(), cache.weight())));
                return result;
            }
            @Override public boolean supports(CacheClearRequest request) {
                return request.reason() == CacheClearRequest.Reason.USER && (request.namespace() == null
                        || request.namespace().equals(namespace));
            }
            @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                return clearOutcome(request).thenApply(ignored -> null);
            }
            @Override public CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
                long expected = generation;
                var result = new CompletableFuture<CacheClearOutcome>();
                dispatch(ownerQueue, result, () -> {
                    if (closed || expected != generation || !supports(request)) {
                        result.complete(CacheClearOutcome.skipped());
                    } else {
                        clearSnapshots();
                        result.complete(CacheClearOutcome.completed(0));
                    }
                });
                return result;
            }
        });
    }

    /** Identity comparison deliberately distinguishes reconnects to the same server namespace. */
    void connection(Object identity, String activeNamespace) {
        if (closed) return;
        if (identity != null && (activeNamespace == null || activeNamespace.isBlank()))
            throw new IllegalArgumentException("Missing connection namespace");
        if (connection == identity && Objects.equals(namespace, activeNamespace)) return;
        clearSnapshots();
        if (connection != identity) flushed = false;
        connection = identity;
        namespace = identity == null ? null : activeNamespace;
    }

    long generation() { return generation; }

    /** Owner-thread authoritative snapshot. Fences only this target in already-sent batches. */
    void remember(EntityPreviewTarget target, T template) {
        if (closed || connection == null) return;
        long now = clock.getAsLong();
        // Supersession lives only inside bounded, expiring batches, including after cache eviction.
        for (Request request : inflight.values())
            if (request.targets().contains(target)) request.superseded().add(target);
        pending.remove(target);
        busy.remove(target);
        try {
            Size size = isEmpty.test(template) ? new Size(0, 0) : measure.apply(template);
            if (size.encodedBytes() < 0 || size.encodedBytes() > MAX_APPEARANCE_BYTES
                    || size.retainedBytes() < 0 || size.retainedBytes() > Long.MAX_VALUE - 640)
                throw new IllegalArgumentException("Oversized preview appearance");
            if (cache.put(target, new Cached<>(template, isEmpty.test(template) ? State.UNAVAILABLE : State.READY,
                    now + REFRESH_NANOS, 0, size.retainedBytes(), now))) return;
        } catch (RuntimeException invalid) {
            // A full editor reply may exceed the preview budget. Refresh through the bounded transport.
        }
        Cached<T> old = cache.get(target);
        if (old != null) cache.put(target, new Cached<>(old.template(), old.state(), now,
                old.failures(), old.bytes(), old.successAt()));
        resolve(target); // Retains a usable old image while normal queue/backoff/size limits apply.
    }

    Result<T> resolve(EntityPreviewTarget target) {
        if (closed || connection == null) return new Result<>(State.ERROR, empty);
        long now = clock.getAsLong();
        Cached<T> cached = cache.get(target);
        if (cached != null && !isEmpty.test(cached.template()) && !usable(cached, now)) {
            cached = new Cached<>(empty, State.ERROR, cached.retryAt(), cached.failures(), 0, 0);
            cache.put(target, cached);
        }
        if (!busy.contains(target) && (cached == null || now - cached.retryAt() >= 0)) {
            if (pending.size() < MAX_PENDING) {
                pending.put(target, now);
                busy.add(target);
            } else if (cached == null) {
                return new Result<>(State.ERROR, empty);
            }
        }
        if (cached != null && !isEmpty.test(cached.template()))
            return new Result<>(State.READY, cached.template());
        if (busy.contains(target)) return new Result<>(State.LOADING, empty);
        return new Result<>(cached == null ? State.ERROR : cached.state(), empty);
    }

    /** Sends at most four batches per second, even when both tick and delayed wakeups call this. */
    void tick() {
        if (closed || connection == null) return;
        long now = clock.getAsLong();
        var requests = inflight.entrySet().iterator();
        while (requests.hasNext()) {
            Request request = requests.next().getValue();
            if (now - request.sentAt() >= TIMEOUT_NANOS) {
                requests.remove();
                fail(request.activeTargets(), State.ERROR, now);
            }
        }
        var queued = pending.entrySet().iterator();
        while (queued.hasNext()) {
            var entry = queued.next();
            if (now - entry.getValue() >= QUEUE_TIMEOUT_NANOS) {
                queued.remove();
                fail(List.of(entry.getKey()), State.ERROR, now);
            }
        }
        cache.trim();
        if (pending.isEmpty() || inflight.size() >= MAX_INFLIGHT
                || flushed && now - lastFlush < FLUSH_NANOS) return;
        var targets = new ArrayList<EntityPreviewTarget>(BATCH_SIZE);
        var iterator = pending.keySet().iterator();
        while (iterator.hasNext() && targets.size() < BATCH_SIZE) {
            targets.add(iterator.next());
            iterator.remove();
        }
        List<EntityPreviewTarget> batch = List.copyOf(targets);
        long requestId = ++nextRequest;
        inflight.put(requestId, new Request(batch, now, new HashSet<>()));
        flushed = true;
        lastFlush = now;
        try { transport.send(requestId, batch); }
        catch (RuntimeException failure) {
            Request failed = inflight.remove(requestId);
            if (failed != null) fail(failed.activeTargets(), State.ERROR, now);
        }
    }

    /** Receiver captures generation before posting to the owner; IDs are never reused after clearing. */
    void accept(long requestId, List<Entry<T>> entries, long expectedGeneration) {
        if (closed || expectedGeneration != generation || connection == null) return;
        Request request = inflight.remove(requestId);
        if (request == null) return;
        long now = clock.getAsLong();
        if (now - request.sentAt() >= TIMEOUT_NANOS) {
            fail(request.activeTargets(), State.ERROR, now);
            return;
        }
        Map<EntityPreviewTarget, Entry<T>> received = new HashMap<>();
        Map<EntityPreviewTarget, Long> sizes = new HashMap<>();
        try {
            if (entries.size() > BATCH_SIZE) throw new IllegalArgumentException("Oversized preview batch");
            long total = 0;
            for (Entry<T> entry : entries) {
                if (entry == null || entry.template() == null || !request.targets().contains(entry.target())
                        || received.put(entry.target(), entry) != null)
                    throw new IllegalArgumentException("Unexpected preview target");
                Size size = isEmpty.test(entry.template()) ? new Size(0, 0) : measure.apply(entry.template());
                long bytes = size.encodedBytes();
                if (bytes < 0 || size.retainedBytes() < 0 || size.retainedBytes() > Long.MAX_VALUE - 640
                        || bytes > MAX_APPEARANCE_BYTES
                        || (total += bytes) > MAX_RESPONSE_BYTES)
                    throw new IllegalArgumentException("Oversized preview appearance");
                sizes.put(entry.target(), size.retainedBytes());
            }
        } catch (RuntimeException invalid) {
            fail(request.activeTargets(), State.ERROR, now);
            return;
        }
        for (EntityPreviewTarget target : request.activeTargets()) {
            Entry<T> entry = received.get(target);
            if (entry == null) fail(List.of(target), State.ERROR, now);
            else if (isEmpty.test(entry.template())) fail(List.of(target), failureState(entry.message()), now);
            else {
                busy.remove(target);
                if (!cache.put(target, new Cached<>(entry.template(), State.READY, now + REFRESH_NANOS,
                        0, sizes.get(target), now))) fail(List.of(target), State.ERROR, now);
            }
        }
    }

    private void fail(List<EntityPreviewTarget> targets, State state, long now) {
        for (EntityPreviewTarget target : targets) {
            busy.remove(target);
            Cached<T> old = cache.get(target);
            int failures = old == null ? 1 : Math.min(5, old.failures() + 1);
            long delay = Math.min(MAX_RETRY_NANOS, RETRY_NANOS << (failures - 1));
            boolean retain = state == State.ERROR && old != null && usable(old, now);
            cache.put(target, new Cached<>(retain ? old.template() : empty, state,
                    now + delay, failures, retain ? old.bytes() : 0, retain ? old.successAt() : 0));
        }
    }

    private boolean usable(Cached<T> value, long now) {
        return !isEmpty.test(value.template()) && now - value.successAt() < MAX_STALE_NANOS;
    }

    private static State failureState(String message) {
        if (message == null || message.isBlank()) return State.UNAVAILABLE;
        return switch (message) {
            case "entity_unavailable", "dimension_unavailable", "unsupported_player", "no_appearance",
                    "unsupported_type", "appearance_unavailable" -> State.UNAVAILABLE;
            default -> State.ERROR;
        };
    }

    boolean hasWork() { return !pending.isEmpty() || !inflight.isEmpty(); }
    int pendingCount() { return pending.size(); }
    int inflightCount() { return inflight.size(); }

    private void clearSnapshots() {
        generation++;
        pending.clear();
        inflight.clear();
        busy.clear();
        cache.clear();
    }

    private static void dispatch(Consumer<Runnable> queue, CompletableFuture<?> result, Runnable action) {
        try {
            queue.accept(() -> {
                try { action.run(); }
                catch (RuntimeException error) { result.completeExceptionally(error); }
            });
        } catch (RuntimeException error) { result.completeExceptionally(error); }
    }

    @Override public void close() {
        if (closed) return;
        clearSnapshots();
        closed = true;
        connection = null;
        namespace = null;
        cache.close();
        registration.close();
    }
}
