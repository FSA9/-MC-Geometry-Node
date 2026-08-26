package com.mine.geometry_node.core.engine.graph.binding;

import com.mine.geometry_node.core.engine.graph.GraphKind;

import java.util.Objects;

/** Identifies a bound graph without allowing graph ids from different runtimes to collide. */
public record GraphBindingKey(GraphKind kind, String graphId) {
    public GraphBindingKey {
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == GraphKind.UNKNOWN) {
            throw new IllegalArgumentException("Binding kind cannot be unknown");
        }
        graphId = graphId != null ? graphId.trim() : "";
        if (graphId.isEmpty()) {
            throw new IllegalArgumentException("Graph id cannot be empty");
        }
    }

    public static GraphBindingKey blueprint(String graphId) {
        return new GraphBindingKey(GraphKind.BLUEPRINT, graphId);
    }
}
