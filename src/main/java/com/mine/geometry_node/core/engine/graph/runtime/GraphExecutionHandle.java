package com.mine.geometry_node.core.engine.graph.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime-neutral handle for an active graph execution.
 */
public interface GraphExecutionHandle {
    boolean isActive();

    default void close() {
    }

    @Nullable
    default Object unwrap() {
        return null;
    }
}
