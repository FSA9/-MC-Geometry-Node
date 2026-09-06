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

    /** Starts a complete evaluation epoch, clearing values and cycle state. */
    public void beginEpoch() {
        cache.beginEpoch();
    }

    /** Invalidates cached values without disturbing an in-progress cycle guard. */
    public void clearValues() {
        cache.clearValues();
    }

    @Nullable
    public Object evaluate(int nodeId, int portKey, NodeEvaluator evaluator) {
        if (nodeId < 0 || nodeId >= index.getNodeCount()
                || !index.hasPort(nodeId, portKey)
                || cache.isRecursing(nodeId)) {
            return null;
        }

        Object cached = cache.get(nodeId, portKey);
        if (!GraphValueCache.isCacheMiss(cached)) {
            return cached;
        }

        cache.enterNode(nodeId);
        Object value;
        try {
            value = evaluator.compute(nodeId, portKey);
        } finally {
            cache.exitNode(nodeId);
        }
        return cache.put(nodeId, portKey, value);
    }

    @FunctionalInterface
    public interface NodeEvaluator {
        @Nullable Object compute(int nodeId, int portKey);
    }
}
