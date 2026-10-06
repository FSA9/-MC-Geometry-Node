package com.mine.geometry_node.client.model.render.bvh;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded reusable light values; the caller supplies freshly observed world brightness. */
final class ModelLightSamples {
    static final int MAX_SAMPLES = 8192;
    private record Position(int x, int y, int z) {}
    private final LinkedHashMap<Position, int[]> samples = new LinkedHashMap<>(256, 0.75f, true);
    private long revision = 1, hits, misses, invalidations, fullInvalidations, changes, evictions;
    private String lastInvalidation = "none";

    int[] sample(int x, int y, int z, int block, int sky) {
        var position = new Position(x, y, z);
        int[] values = samples.get(position);
        if (values != null && values[0] == block && values[1] == sky) {
            hits++;
            return values;
        }
        if (values != null) {
            changes++;
            invalidations++;
            lastInvalidation = "observed:" + x + "," + y + "," + z;
        } else {
            if (samples.size() >= MAX_SAMPLES) { samples.pollFirstEntry(); evictions++; }
            values = new int[2];
            samples.put(position, values);
        }
        values[0] = block;
        values[1] = sky;
        misses++;
        return values;
    }

    void invalidateRegion(int x, int y, int z, int radius, String reason) {
        if (radius < 0) throw new IllegalArgumentException("negative light invalidation radius");
        samples.keySet().removeIf(position -> Math.abs((long) position.x - x) <= radius
                && Math.abs((long) position.y - y) <= radius
                && Math.abs((long) position.z - z) <= radius);
        revision++;
        invalidations++;
        lastInvalidation = (reason == null ? "manual" : reason) + "/r=" + radius;
    }

    void invalidateAll(String reason) {
        samples.clear();
        revision++;
        fullInvalidations++;
        lastInvalidation = reason == null ? "manual" : reason;
    }

    int size() { return samples.size(); }

    Map<String, Object> report() {
        return Map.of("lightRevision", revision, "cachedSamples", samples.size(),
                "cacheHits", hits, "cacheMisses", misses, "maxCachedSamples", MAX_SAMPLES,
                "cacheEvictions", evictions, "invalidations", invalidations,
                "fullInvalidations", fullInvalidations, "detectedChanges", changes,
                "lastInvalidation", lastInvalidation);
    }
}
