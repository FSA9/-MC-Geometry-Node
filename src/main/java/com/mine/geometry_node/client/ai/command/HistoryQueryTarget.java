package com.mine.geometry_node.client.ai.command;

/** History capability shared by CLI and MCP; a surface reference selects the document explicitly. */
public interface HistoryQueryTarget extends CommandInvocationContext.CommandTarget {
    CommandResult getHistoryState(String surfaceRef);
}
