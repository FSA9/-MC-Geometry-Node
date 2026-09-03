package com.mine.geometry_node.core.engine.graph.data;

import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Owns one runtime instance's epoch cache and data-cycle guard. */
public final class GraphDataEvaluationSession {
    private final CompiledDataIndex index;
    private final GraphValueCache cache;

    public GraphDataEvaluationSession(CompiledDataIndex index) {
        this.index = Objects.requireNonNull(index, "index");
        this.cache = new GraphValueCache(index.getNodeCount());
    }

    public void reset() {
        cache.reset();
    }

    public void beginEpoch() {
        cache.beginEpoch();
    }

    public void clearValues() {
        cache.clearValues();
    }

    @Nullable
    public Object evaluate(int nodeId, String portName, NodeEvaluator evaluator) {
        if (nodeId < 0 || nodeId >= index.getNodeCount() || cache.isRecursing(nodeId)) {
            return null;
        }

        int portId = index.getPortKey(portName);
        Object cached = cache.get(nodeId, portName, portId);
        if (!GraphValueCache.isCacheMiss(cached)) {
            return cached;
        }

        cache.enterNode(nodeId);
        Object value;
        try {
            value = evaluator.compute(nodeId, portName);
        } finally {
            cache.exitNode(nodeId);
        }
        cache.put(nodeId, portName, portId, value);
        return cache.get(nodeId, portName, portId);
    }

    @FunctionalInterface
    public interface NodeEvaluator {
        @Nullable Object compute(int nodeId, String portName);
    }
}
