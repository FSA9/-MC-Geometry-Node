package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.network.packet.worldquery.PacketWorldObjectQuery;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Server-thread queue. The wakeup must defer onto that server thread, including while simulation is paused. */
final class WorldObjectQueryScheduler<K> implements AutoCloseable {
    static final int BATCH_INTERVAL_MILLIS = 50, MAX_QUERIES_PER_BATCH = 2, MAX_PENDING_PER_CONNECTION = 16;
    private final Map<K, ArrayDeque<PacketWorldObjectQuery>> pending = new LinkedHashMap<>();
    private final Consumer<Runnable> wakeup;
    private final BiConsumer<K, PacketWorldObjectQuery> execute;
    private boolean scheduled, closed;

    WorldObjectQueryScheduler(Consumer<Runnable> wakeup, BiConsumer<K, PacketWorldObjectQuery> execute) {
        this.wakeup = wakeup;
        this.execute = execute;
    }

    boolean offer(K connection, PacketWorldObjectQuery request) {
        if (closed) return false;
        var requests = pending.computeIfAbsent(connection, ignored -> new ArrayDeque<>());
        if (requests.size() >= MAX_PENDING_PER_CONNECTION) return false;
        requests.addLast(request);
        arm();
        return true;
    }

    void remove(K connection) { pending.remove(connection); }

    private void arm() {
        if (scheduled || closed || pending.isEmpty()) return;
        scheduled = true;
        wakeup.accept(this::drain);
    }

    private void drain() {
        if (closed) return;
        // Keep scheduled set during execution so reentrant offers cannot create a second wakeup.
        try {
            for (int processed = 0; processed < MAX_QUERIES_PER_BATCH && !pending.isEmpty(); processed++) {
                var iterator = pending.entrySet().iterator();
                var entry = iterator.next();
                K connection = entry.getKey();
                var requests = entry.getValue();
                var request = requests.removeFirst();
                iterator.remove();
                if (!requests.isEmpty()) pending.put(connection, requests);
                execute.accept(connection, request);
            }
        } finally {
            scheduled = false;
            arm();
        }
    }

    @Override public void close() {
        closed = true;
        pending.clear();
    }
}
