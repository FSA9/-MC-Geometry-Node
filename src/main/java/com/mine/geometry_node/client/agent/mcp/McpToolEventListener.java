package com.mine.geometry_node.client.agent.mcp;

@FunctionalInterface
public interface McpToolEventListener {
    McpToolEventListener NOOP = event -> { };

    void onToolEvent(McpToolEvent event);
}
