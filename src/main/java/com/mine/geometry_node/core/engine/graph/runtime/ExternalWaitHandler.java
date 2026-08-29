package com.mine.geometry_node.core.engine.graph.runtime;

import org.jetbrains.annotations.Nullable;

/** Runtime service that owns an asynchronous wait started by a graph execution. */
public interface ExternalWaitHandler {
    String externalWaitId();

    boolean beginExternalWait(GraphExecutionHandle handle, ExternalWaitRequest request);

    void completeExternalWait(GraphExecutionHandle handle, String outputPortName,
                              Completion completion);

    void endExternalWait(GraphExecutionHandle handle, @Nullable String reason);

    enum Completion {
        RESUMED,
        NO_TARGET
    }
}
