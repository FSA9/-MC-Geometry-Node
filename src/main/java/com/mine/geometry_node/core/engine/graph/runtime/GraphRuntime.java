package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime semantics for a graph family.
 * Implementations should keep execution rules isolated from graph storage.
 */
public interface GraphRuntime {
    GraphKind kind();

    String id();

    default void init() {
    }

    /** Ascending order used by the shared server-level tick driver. */
    default int tickOrder() {
        return 0;
    }

    default void tickLevel(ServerLevel level) {
    }

    default void shutdown(MinecraftServer server) {
    }

    default boolean beginExternalWait(GraphExecutionHandle handle, ExternalWaitRequest request) {
        return false;
    }

    default void completeExternalWait(GraphExecutionHandle handle, String outputPortName, ExternalWaitCompletion completion) {
    }

    default void endExternalWait(GraphExecutionHandle handle, @Nullable String reason) {
    }

    enum ExternalWaitCompletion {
        RESUMED,
        NO_TARGET
    }
}
