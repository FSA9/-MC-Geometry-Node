package com.mine.geometry_node.core.engine.system.entity.chunkloading;

import java.util.function.LongConsumer;

/** Inclusive chunk square and ticket transitions, without allocating per-chunk collections. */
final class ChunkLoadingCoverage {
    private final long minX, maxX, minZ, maxZ;

    private ChunkLoadingCoverage(long minX, long maxX, long minZ, long maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    static ChunkLoadingCoverage around(int centerX, int centerZ, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Negative chunk radius");
        // Chunk positions have signed int coordinates. Never wrap into the opposite edge.
        return new ChunkLoadingCoverage(
                Math.max(Integer.MIN_VALUE, (long) centerX - radius),
                Math.min(Integer.MAX_VALUE, (long) centerX + radius),
                Math.max(Integer.MIN_VALUE, (long) centerZ - radius),
                Math.min(Integer.MAX_VALUE, (long) centerZ + radius));
    }

    boolean contains(long chunk) {
        int x = (int) chunk;
        int z = (int) (chunk >>> 32);
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    void forEach(LongConsumer action) {
        rectangle(minX, maxX, minZ, maxZ, action);
    }

    void forEachOutside(ChunkLoadingCoverage other, LongConsumer action) {
        long overlapMinX = Math.max(minX, other.minX);
        long overlapMaxX = Math.min(maxX, other.maxX);
        long overlapMinZ = Math.max(minZ, other.minZ);
        long overlapMaxZ = Math.min(maxZ, other.maxZ);
        if (overlapMinX > overlapMaxX || overlapMinZ > overlapMaxZ) {
            forEach(action);
            return;
        }
        // Four disjoint strips: only the two middle strips share the overlap's X range.
        rectangle(minX, overlapMinX - 1, minZ, maxZ, action);
        rectangle(overlapMaxX + 1, maxX, minZ, maxZ, action);
        rectangle(overlapMinX, overlapMaxX, minZ, overlapMinZ - 1, action);
        rectangle(overlapMinX, overlapMaxX, overlapMaxZ + 1, maxZ, action);
    }

    static void reconcile(ChunkLoadingCoverage previous, ChunkLoadingCoverage next,
                          boolean sameDimension, LongConsumer remove, LongConsumer add) {
        if (previous == null) {
            next.forEach(add);
        } else if (sameDimension) {
            previous.forEachOutside(next, remove);
            next.forEachOutside(previous, add);
        } else {
            previous.forEach(remove);
            next.forEach(add);
        }
    }

    private static void rectangle(long minX, long maxX, long minZ, long maxZ,
                                  LongConsumer action) {
        if (minX > maxX || minZ > maxZ) return;
        for (long x = minX; x <= maxX; x++) {
            for (long z = minZ; z <= maxZ; z++) {
                action.accept((x & 0xFFFF_FFFFL) | ((z & 0xFFFF_FFFFL) << 32));
            }
        }
    }
}
