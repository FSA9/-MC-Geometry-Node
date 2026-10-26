package com.mine.geometry_node.core.engine.system.scopeddata;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Server-thread, connection-identity rate limits. A short burst allows catalog plus initial read. */
final class ScopedDataConnections<C> {
    static final int BURST = 4;
    static final long REFILL_NANOS = 100_000_000L;
    private static final class Bucket { long updated; long tokens = BURST; Bucket(long now) { updated = now; } }
    private final Map<C, Bucket> connections = new IdentityHashMap<>();
    private final LongSupplier clock;
    ScopedDataConnections() { this(System::nanoTime); }
    ScopedDataConnections(LongSupplier clock) { this.clock = clock; }
    boolean acquire(C connection) {
        long now = clock.getAsLong();
        Bucket bucket = connections.computeIfAbsent(connection, ignored -> new Bucket(now));
        long refill = (now - bucket.updated) / REFILL_NANOS;
        if (refill > 0) {
            bucket.tokens = Math.min(BURST, bucket.tokens + refill);
            bucket.updated = now - (now - bucket.updated) % REFILL_NANOS;
        }
        if (bucket.tokens == 0) return false;
        bucket.tokens--; return true;
    }
    void remove(C connection) { connections.remove(connection); }
    void removeIf(Predicate<C> predicate) { connections.keySet().removeIf(predicate); }
}
