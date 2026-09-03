package com.mine.geometry_node.core.engine.graph.data;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class GraphValueCache {
    private static final Object CACHE_MISS = new Object();
    private static final Object CACHED_NULL = new Object();

    private record CachedValue(Object value, boolean copyOnRead) {
    }

    private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Map<String, Object>> dynamicFrameCache = new Int2ObjectOpenHashMap<>();
    private final boolean[] recursionGuard;

    public GraphValueCache(int nodeCount) {
        this.recursionGuard = new boolean[nodeCount];
    }

    public void reset() {
        frameCache.clear();
        dynamicFrameCache.clear();
        Arrays.fill(recursionGuard, false);
    }

    public void beginEpoch() {
        frameCache.clear();
        Arrays.fill(recursionGuard, false);
    }

    public void clearValues() {
        frameCache.clear();
        for (Map<String, Object> map : dynamicFrameCache.values()) {
            map.clear();
        }
    }

    public boolean isRecursing(int nodeId) {
        return recursionGuard[nodeId];
    }

    public void enterNode(int nodeId) {
        recursionGuard[nodeId] = true;
    }

    public void exitNode(int nodeId) {
        recursionGuard[nodeId] = false;
    }

    public Object get(int nodeId, String portName, int portId) {
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
        if (cached == CACHED_NULL) return null;
        CachedValue value = (CachedValue) cached;
        return value.copyOnRead() ? GraphValueSnapshot.snapshot(value.value()) : value.value();
    }

    public static boolean isCacheMiss(Object value) {
        return value == CACHE_MISS;
    }

    public void put(int nodeId, String portName, int portId, Object value) {
        Object cacheValue;
        if (value == null) {
            cacheValue = CACHED_NULL;
        } else {
            Object snapshot = GraphValueSnapshot.snapshot(value);
            cacheValue = new CachedValue(snapshot, GraphValueSnapshot.requiresReadCopy(snapshot));
        }
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
