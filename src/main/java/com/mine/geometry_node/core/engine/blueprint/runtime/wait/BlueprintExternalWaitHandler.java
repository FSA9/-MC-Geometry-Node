package com.mine.geometry_node.core.engine.blueprint.runtime.wait;

import org.jetbrains.annotations.Nullable;

/** Asynchronous service capable of suspending and resuming a Blueprint execution. */
public interface BlueprintExternalWaitHandler {
    String externalWaitId();

    boolean beginExternalWait(BlueprintExecutionHandle handle, BlueprintExternalWaitRequest request);

    void completeExternalWait(BlueprintExecutionHandle handle, String outputPortName,
                              Completion completion);

    void endExternalWait(BlueprintExecutionHandle handle, @Nullable String reason);

    /**
     * Output used when a transient external operation cannot survive an entity,
     * level, or server reload. Returning {@code null} terminates the waiting branch.
     */
    @Nullable
    default String interruptionOutputPort(BlueprintExternalWaitRequest request) {
        return null;
    }

    enum Completion {
        RESUMED,
        NO_TARGET
    }
}
