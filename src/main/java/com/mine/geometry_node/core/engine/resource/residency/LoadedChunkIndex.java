package com.mine.geometry_node.core.engine.resource.residency;

import java.util.NavigableSet;
import java.util.LinkedHashSet;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import java.util.TreeMap;
import java.util.TreeSet;

/** Indexes actual loaded chunks; a huge resource never allocates one entry per covered coordinate. */
public final class LoadedChunkIndex {
    private final TreeMap<Integer, NavigableSet<Integer>> columns = new TreeMap<>();
    private long revision;
    private final LinkedHashSet<Long> validationOrder = new LinkedHashSet<>();

    public synchronized void load(int x, int z) {
        if (columns.computeIfAbsent(x, ignored -> new TreeSet<>()).add(z)) {
            validationOrder.add(pack(x,z)); revision++;
        }
    }

    public synchronized void unload(int x, int z) {
        var column = columns.get(x);
        if (column != null && column.remove(z)) {
            if (column.isEmpty()) columns.remove(x);
            validationOrder.remove(pack(x,z)); revision++;
        }
    }

    public synchronized boolean intersects(ChunkCoverage bounds) {
        return firstIntersection(bounds) != null;
    }

    public synchronized Long firstIntersection(ChunkCoverage bounds) {
        return firstIntersection(bounds, ignored -> true);
    }

    /** Availability can change without a new Load event; unavailable candidates remain indexed. */
    public synchronized Long firstIntersection(ChunkCoverage bounds, LongPredicate available) {
        for (var column : columns.subMap(bounds.minX(), true, bounds.maxX(), true).entrySet()) {
            for (int z : column.getValue().subSet(bounds.minZ(), true, bounds.maxZ(), true)) {
                long candidate = pack(column.getKey(), z);
                if (available.test(candidate)) return candidate;
            }
        }
        return null;
    }

    /** Bounded cleanup for observations proven to have expired; temporary unavailability is not eviction. */
    public synchronized void reconcile(int budget, BiPredicate<Integer, Integer> loaded) {
        int count = Math.min(Math.max(0, budget), validationOrder.size());
        for (int i = 0; i < count; i++) {
            long position = validationOrder.removeFirst();
            int x = (int) position, z = (int) (position >>> 32);
            if (loaded.test(x,z)) validationOrder.add(position);
            else unload(x,z);
        }
    }

    private static long pack(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }

    public synchronized long revision() { return revision; }
    public synchronized void clear() {
        if (!columns.isEmpty()) { columns.clear(); validationOrder.clear(); revision++; }
    }
}
