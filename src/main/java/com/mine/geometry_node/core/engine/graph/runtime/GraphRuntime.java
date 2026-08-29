package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;

/**
 * Runtime semantics for a graph family.
 * Implementations should keep execution rules isolated from graph storage.
 */
public interface GraphRuntime extends ServerEngine {
    GraphKind kind();
}
