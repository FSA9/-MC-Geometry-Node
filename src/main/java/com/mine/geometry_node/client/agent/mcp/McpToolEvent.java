package com.mine.geometry_node.client.agent.mcp;

import java.time.Instant;
import java.util.Objects;

/** Sanitized event emitted by the trusted MCP control plane, never inferred from PTY output. */
public record McpToolEvent(Instant timestamp, String toolName, State state, String code, String message) {
    public enum State { STARTED, SUCCEEDED, FAILED }

    public McpToolEvent {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        toolName = requireText(toolName, "toolName", 64);
        state = Objects.requireNonNull(state, "state");
        code = normalize(code, 96);
        message = normalize(message, 512);
    }

    private static String requireText(String value, String name, int limit) {
        String normalized = normalize(value, limit);
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }

    private static String normalize(String value, int limit) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
