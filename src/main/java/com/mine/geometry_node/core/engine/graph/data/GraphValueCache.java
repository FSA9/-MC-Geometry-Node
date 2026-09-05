package com.mine.geometry_node.core.engine.graph.data;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;

final class GraphValueCache {
    private static final Object CACHE_MISS = new Object();
    private static final Object CACHED_NULL = new Object();

    private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>();
    private final boolean[] recursionGuard;

    GraphValueCache(int nodeCount) {
        this.recursionGuard = new boolean[nodeCount];
    }

    void beginEpoch() {
        clearValues();
        Arrays.fill(recursionGuard, false);
    }

    void clearValues() {
        frameCache.clear();
    }

    boolean isRecursing(int nodeId) {
        return recursionGuard[nodeId];
    }

    void enterNode(int nodeId) {
        recursionGuard[nodeId] = true;
    }

    void exitNode(int nodeId) {
        recursionGuard[nodeId] = false;
    }

    Object get(int nodeId, int portId) {
        Object cached = frameCache.get(cacheKey(nodeId, portId));
        if (cached == null) {
            return CACHE_MISS;
        }
        return read(cached);
    }

    static boolean isCacheMiss(Object value) {
        return value == CACHE_MISS;
    }

    Object put(int nodeId, int portId, Object value) {
        Object cacheValue;
        if (value == null) {
            cacheValue = CACHED_NULL;
        } else {
            cacheValue = GraphValueSnapshot.freeze(value);
        }
        frameCache.put(cacheKey(nodeId, portId), cacheValue);
        return read(cacheValue);
    }

    private static Object read(Object cached) {
        if (cached == CACHED_NULL) return null;
        return GraphValueSnapshot.read((GraphValueSnapshot.FrozenValue) cached);
    }

    private static long cacheKey(int nodeId, int portId) {
        return ((long) nodeId << 32) | (portId & 0xFFFFFFFFL);
    }
}
