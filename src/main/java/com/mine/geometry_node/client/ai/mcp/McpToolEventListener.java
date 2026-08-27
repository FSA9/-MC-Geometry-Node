package com.mine.geometry_node.client.ai.mcp;

@FunctionalInterface
public interface McpToolEventListener {
    McpToolEventListener NOOP = event -> { };

    void onToolEvent(McpToolEvent event);
}
