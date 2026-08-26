package com.mine.geometry_node.client.agent.mcp;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Sanitized event emitted by the trusted MCP control plane, never inferred from PTY output. */
public record McpToolEvent(Instant timestamp, String callId, String toolName, State state,
                           String code, String message, long elapsedMillis) {
    private static final Pattern BEARER_SECRET = Pattern.compile(
            "(?i)(bearer\\s+)[a-z0-9._~+\\-/]{16,}");
    private static final Pattern MCP_TOKEN = Pattern.compile(
            "(?i)(GEOMETRY_NODE_MCP_TOKEN\\s*[=:]\\s*)[a-z0-9._~+\\-/]{16,}");

    public enum State { STARTED, SUCCEEDED, FAILED }

    public McpToolEvent {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        callId = requireText(callId, "callId", 48);
        toolName = requireText(toolName, "toolName", 64);
        state = Objects.requireNonNull(state, "state");
        code = normalize(code, 96);
        message = normalize(message, 512);
        if (elapsedMillis < 0) throw new IllegalArgumentException("elapsedMillis cannot be negative");
    }

    public McpToolEvent(Instant timestamp, String toolName, State state, String code, String message) {
        this(timestamp, "legacy", toolName, state, code, message, 0);
    }

    private static String requireText(String value, String name, int limit) {
        String normalized = normalize(value, limit);
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }

    private static String normalize(String value, int limit) {
        if (value == null) return "";
        String bounded = value.substring(0, Math.min(value.length(), limit * 4));
        String normalized = bounded.replace('\r', ' ').replace('\n', ' ');
        normalized = BEARER_SECRET.matcher(normalized).replaceAll("$1[REDACTED]");
        normalized = MCP_TOKEN.matcher(normalized).replaceAll("$1[REDACTED]");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
