package com.mine.geometry_node.core.engine.graph.binding;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;

import java.util.Objects;

/** Identifies a bound graph without allowing graph ids from different runtimes to collide. */
public record GraphBindingKey(GraphKind kind, String graphId) {
    public GraphBindingKey {
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == GraphKind.UNKNOWN) {
            throw new IllegalArgumentException("Binding kind cannot be unknown");
        }
        graphId = GraphAssetId.require(graphId);
    }

    public static GraphBindingKey blueprint(String graphId) {
        return new GraphBindingKey(GraphKind.BLUEPRINT, graphId);
    }

    /** Behavior-tree-only convenience factory; generic storage still uses this common key type. */
    public static GraphBindingKey behaviorTree(String graphId) {
        return new GraphBindingKey(GraphKind.BEHAVIOR_TREE, graphId);
    }
}
