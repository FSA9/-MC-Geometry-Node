package com.mine.geometry_node.core.engine.graph.compile;

import java.util.Set;

/** Optional dependency view for compiled graph families that reference other assets. */
public interface CompiledGraphDependencies {
    Set<String> graphDependencies();
}
