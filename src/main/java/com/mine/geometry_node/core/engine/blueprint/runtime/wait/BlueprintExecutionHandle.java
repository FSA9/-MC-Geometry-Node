package com.mine.geometry_node.core.engine.blueprint.runtime.wait;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Handle exposed by an active Blueprint execution to asynchronous services. */
public interface BlueprintExecutionHandle {
    boolean isActive();

    boolean resume(String outputPortName);

    String graphId();

    @Nullable
    ServerLevel level();

    void abort(String reason);
}
