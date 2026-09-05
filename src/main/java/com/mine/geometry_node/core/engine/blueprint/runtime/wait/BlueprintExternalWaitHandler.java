package com.mine.geometry_node.core.engine.blueprint.runtime.wait;

import org.jetbrains.annotations.Nullable;

/** Asynchronous service capable of suspending and resuming a Blueprint execution. */
public interface BlueprintExternalWaitHandler {
    String externalWaitId();

    boolean beginExternalWait(BlueprintExecutionHandle handle, BlueprintExternalWaitRequest request);

    void completeExternalWait(BlueprintExecutionHandle handle, String outputPortName,
                              Completion completion);

    void endExternalWait(BlueprintExecutionHandle handle, @Nullable String reason);

    enum Completion {
        RESUMED,
        NO_TARGET
    }
}
