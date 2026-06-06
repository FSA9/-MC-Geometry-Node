package com.mine.geometry_node.core.engine.blueprint.runtime;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class FrameValueCache {
    private static final Object CACHE_MISS = new Object();
    private static final Object CACHED_NULL = new Object();

    private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Map<String, Object>> dynamicFrameCache = new Int2ObjectOpenHashMap<>();
    private final boolean[] recursionGuard;

    FrameValueCache(int nodeCount) {
        this.recursionGuard = new boolean[nodeCount];
    }

    void reset() {
        frameCache.clear();
        dynamicFrameCache.clear();
        Arrays.fill(recursionGuard, false);
    }

    void beginRootRun() {
        frameCache.clear();
        Arrays.fill(recursionGuard, false);
    }

    void clearFrameValues() {
        frameCache.clear();
        for (Map<String, Object> map : dynamicFrameCache.values()) {
            map.clear();
        }
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

    Object get(int nodeId, String portName, int portId) {
        Object cached;
        if (portId != -1) {
            cached = frameCache.get(cacheKey(nodeId, portId));
        } else {
            Map<String, Object> nodeDynamicCache = dynamicFrameCache.get(nodeId);
            cached = nodeDynamicCache != null ? nodeDynamicCache.get(portName) : null;
        }
        if (cached == null) {
            return CACHE_MISS;
        }
        return cached == CACHED_NULL ? null : cached;
    }

    static boolean isCacheMiss(Object value) {
        return value == CACHE_MISS;
    }

    void put(int nodeId, String portName, int portId, Object value) {
        Object cacheValue = value == null ? CACHED_NULL : value;
        if (portId != -1) {
            frameCache.put(cacheKey(nodeId, portId), cacheValue);
            return;
        }

        Map<String, Object> nodeDynamicCache = dynamicFrameCache.get(nodeId);
        if (nodeDynamicCache == null) {
            nodeDynamicCache = new HashMap<>();
            dynamicFrameCache.put(nodeId, nodeDynamicCache);
        }
        nodeDynamicCache.put(portName, cacheValue);
    }

    private static long cacheKey(int nodeId, int portId) {
        return ((long) nodeId << 32) | (portId & 0xFFFFFFFFL);
    }
}
