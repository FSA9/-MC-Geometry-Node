package com.mine.geometry_node.core.engine.graph.compile.dependency;

import java.util.Set;

/** Optional dependency view for compiled graph families that reference other assets. */
public interface CompiledGraphDependencies {
    Set<String> graphDependencies();

    /** Whether missing, invalid, or cyclic dependencies invalidate this artifact. */
    default boolean requiresAvailableDependencies() {
        return true;
    }
}
