package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;

/**
 * Runtime semantics for a graph family.
 * Implementations should keep execution rules isolated from graph storage.
 */
public interface GraphRuntime {
    GraphKind kind();

    String id();

    default void init() {
    }
}
