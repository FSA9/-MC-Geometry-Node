package com.mine.geometry_node.client.ai.mcp;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded, sanitized audit owned by one MCP-enabled PowerShell run. */
public final class McpRunAudit implements McpToolEventListener, AutoCloseable {
    public static final int MAX_EVENTS = 256;

    private final ArrayDeque<McpToolEvent> events = new ArrayDeque<>(MAX_EVENTS);
    private boolean closed;

    @Override
    public synchronized void onToolEvent(McpToolEvent event) {
        if (closed || event == null) return;
        if (events.size() == MAX_EVENTS) events.removeFirst();
        events.addLast(event);
    }

    public synchronized List<McpToolEvent> snapshot() {
        return List.copyOf(events);
    }

    @Override
    public synchronized void close() {
        closed = true;
        events.clear();
    }
}
