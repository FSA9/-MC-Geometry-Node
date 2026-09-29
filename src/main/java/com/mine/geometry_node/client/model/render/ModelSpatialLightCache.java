package com.mine.geometry_node.client.model.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import java.util.*;

/**
 * Versioned spatial light samples. Geometry and GPU residency are independent from this cache.
 * Explicit invalidation is available for block/chunk integrations; sampled values also detect
 * changes on cache hits so the current host API does not require a renderer mixin.
 */
final class ModelSpatialLightCache {
    private record Entry(BlockPos position, int[] values, long revision) {}
    private Level level;
    private final Map<Long, Entry> samples = new HashMap<>();
    private long worldGeneration = 1;
    private long lightRevision = 1;
    private long cacheHits, cacheMisses, invalidations, fullInvalidations, detectedChanges;
    private String lastInvalidation = "none";

    void beginFrame(Level next) {
        if (level != next) {
            level = next;
            samples.clear();
            worldGeneration++;
            lightRevision++;
            fullInvalidations++;
            lastInvalidation = "world_changed";
        }
    }

    int[] sample(Level world, BlockPos pos) {
        if (level != world) beginFrame(world);
        long key = pos.asLong();
        Entry cached = samples.get(key);
        int block = world.getBrightness(LightLayer.BLOCK, pos);
        int sky = world.getBrightness(LightLayer.SKY, pos);
        if (cached != null && cached.revision == lightRevision
                && cached.values[0] == block && cached.values[1] == sky) {
            cacheHits++;
            return cached.values;
        }
        if (cached != null && cached.revision == lightRevision) {
            detectedChanges++;
            invalidations++;
            lastInvalidation = "observed:" + key;
        }
        int[] values = cached == null ? new int[2] : cached.values;
        values[0] = block;
        values[1] = sky;
        samples.put(key, new Entry(pos.immutable(), values, lightRevision));
        cacheMisses++;
        return values;
    }

    /** Invalidates one sample and its immediate light-neighbor samples. */
    void invalidate(BlockPos center) {
        invalidateRegion(center, 1, "block:" + center.asLong());
    }

    /** Invalidates cached samples in a block-space cube. This is intentionally sample-only; no mesh rebuild. */
    void invalidateRegion(BlockPos center, int radius, String reason) {
        if (radius < 0) throw new IllegalArgumentException("negative light invalidation radius");
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        samples.entrySet().removeIf(entry -> {
            BlockPos p = entry.getValue().position;
            return Math.abs(p.getX() - cx) <= radius
                    && Math.abs(p.getY() - cy) <= radius
                    && Math.abs(p.getZ() - cz) <= radius;
        });
        lightRevision++;
        invalidations++;
        lastInvalidation = (reason == null ? "manual" : reason) + "/r=" + radius;
    }

    /** Invalidates every spatial sample while retaining immutable geometry/GPU resources. */
    void invalidateAll(String reason) {
        samples.clear();
        lightRevision++;
        fullInvalidations++;
        lastInvalidation = reason == null ? "manual" : reason;
    }

    int size() { return samples.size(); }

    Map<String, Object> report() {
        return Map.of(
                "worldGeneration", worldGeneration,
                "lightRevision", lightRevision,
                "cachedSamples", samples.size(),
                "cacheHits", cacheHits,
                "cacheMisses", cacheMisses,
                "invalidations", invalidations,
                "fullInvalidations", fullInvalidations,
                "detectedChanges", detectedChanges,
                "lastInvalidation", lastInvalidation
        );
    }
}
