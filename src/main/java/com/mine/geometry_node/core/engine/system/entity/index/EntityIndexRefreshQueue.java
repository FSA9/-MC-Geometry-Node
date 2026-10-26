package com.mine.geometry_node.core.engine.system.entity.index;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Server-thread, deduplicated round-robin of loaded registrations; never retains entities. */
final class EntityIndexRefreshQueue {
    private static final int INTERVAL_TICKS = 20;
    private final LinkedHashMap<UUID, Long> due = new LinkedHashMap<>();

    void schedule(UUID id, long tick) { due.putIfAbsent(id, tick + INTERVAL_TICKS); }
    void remove(UUID id) { due.remove(id); }

    UUID poll(long tick) {
        var iterator = due.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        var first = iterator.next();
        if (first.getValue() > tick) return null;
        UUID id = first.getKey();
        iterator.remove();
        return id;
    }

    int size() { return due.size(); }
}
