package com.mine.geometry_node.client.runtime.model.render.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** Identity-based use history; keep both the current and previous render frame protected. */
public final class ModelGeometryRetention<T> {
    private final IdentityHashMap<T, Long> uses = new IdentityHashMap<>();

    public void touch(T geometry, long frame) {
        uses.put(Objects.requireNonNull(geometry), frame);
    }

    public List<T> candidates(long frame) {
        if (frame < 2) return List.of();
        List<T> candidates = new ArrayList<>();
        for (var entry : uses.entrySet()) if (entry.getValue() >= 0 && entry.getValue() < frame - 1)
            candidates.add(entry.getKey());
        candidates.sort(Comparator.comparingLong(uses::get));
        return candidates;
    }

    public void forget(T geometry) { uses.remove(geometry); }
    public void clear() { uses.clear(); }
}
