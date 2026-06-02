package com.mine.geometry_node.core.engine.graph.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Shared server-side runtime context for graph runtimes.
 * Runtime-specific contexts can wrap this with their own state.
 */
public record GraphRuntimeContext(
        ServerLevel level,
        @Nullable Entity owner
) {
    public GraphRuntimeContext {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
    }
}
