package com.mine.geometry_node.core.engine.graph.runtime;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-neutral handle for an active graph execution.
 */
public interface GraphExecutionHandle {
    boolean isActive();

    default boolean resume(String outputPortName) {
        return false;
    }

    default String graphId() {
        return "";
    }

    @Nullable
    default ServerLevel level() {
        return null;
    }

    default void close() {
    }

    @Nullable
    default Object unwrap() {
        return null;
    }
}
