package com.mine.geometry_node.client.ai.command;

/** Runtime-neutral UI context queries implemented by the client UI adapter. */
public interface UiSurfaceQueryTarget extends CommandInvocationContext.CommandTarget {
    CommandResult getUiContext();

    CommandResult getSurfaceContext(String surfaceRef);
}
