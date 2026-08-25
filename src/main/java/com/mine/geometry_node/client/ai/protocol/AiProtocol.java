package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Provider-neutral wire contracts used by chat and agent implementations. */
public final class AiProtocol {
    public static final int VERSION = 1;
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private AiProtocol() {}

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public enum FinishReason { STOP, TOOL_CALLS, LENGTH, CANCELLED, ERROR, UNKNOWN }

    public enum ErrorCode {
        INVALID_REQUEST,
        AUTH,
        QUOTA,
        RATE_LIMIT,
        MODEL_NOT_FOUND,
        CONTEXT_LENGTH,
        CONTENT_FILTER,
        NETWORK,
        TIMEOUT,
        PROTOCOL,
        TOOL_NOT_FOUND,
        ARGUMENT_INVALID,
        PERMISSION_DENIED,
        REVISION_CONFLICT,
        SCOPE_CONFLICT,
        VALIDATION_FAILED,
        COMMIT_FAILED,
        CANCELLED,
        UNKNOWN
    }

    public sealed interface ContentPart permits TextPart, ToolCallPart, ToolResultPart {}

    public record TextPart(String text) implements ContentPart {
        public TextPart { text = Objects.requireNonNull(text, "text"); }
    }

    public record ToolCallPart(String callId, String toolName, JsonElement arguments) implements ContentPart {
        public ToolCallPart {
            callId = requireNonBlank(callId, "callId");
            toolName = requireToolName(toolName);
            arguments = copyJson(arguments);
        }

        @Override public JsonElement arguments() { return copyJson(arguments); }
    }

    public record ToolResultPart(String callId, String toolName, boolean success, JsonElement content,
                                 Error error) implements ContentPart {
        public ToolResultPart {
            callId = requireNonBlank(callId, "callId");
            toolName = requireToolName(toolName);
            content = copyJson(content);
            if (success && error != null) throw new IllegalArgumentException("successful result cannot contain an error");
            if (!success && error == null) throw new IllegalArgumentException("failed result requires an error");
        }

        @Override public JsonElement content() { return copyJson(content); }
    }

    public record Message(int protocolVersion, Role role, List<ContentPart> content) {
        public Message {
            protocolVersion = requireVersion(protocolVersion);
            role = Objects.requireNonNull(role, "role");
            content = List.copyOf(Objects.requireNonNull(content, "content"));
            if (content.isEmpty()) throw new IllegalArgumentException("message content cannot be empty");
        }

        public static Message of(Role role, ContentPart... content) {
            return new Message(VERSION, role, List.of(content));
        }
    }

    public record ToolDefinition(int protocolVersion, String name, String description,
                                 JsonElement inputSchema) {
        public ToolDefinition {
            protocolVersion = requireVersion(protocolVersion);
            name = requireToolName(name);
            description = requireNonBlank(description, "description");
            inputSchema = copyJson(inputSchema);
        }

        @Override public JsonElement inputSchema() { return copyJson(inputSchema); }
    }

    public record Usage(long inputTokens, long outputTokens, long totalTokens) {
        public Usage {
            if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
                throw new IllegalArgumentException("token counts cannot be negative");
            }
        }
    }

    public record ContinuationMetadata(String providerId, JsonElement value) {
        public ContinuationMetadata {
            providerId = requireNonBlank(providerId, "providerId");
            value = copyJson(value);
        }

        @Override public JsonElement value() { return copyJson(value); }
    }

    public record Turn(int protocolVersion, String turnId, Message message, FinishReason finishReason,
                       Usage usage, ContinuationMetadata continuation) {
        public Turn {
            protocolVersion = requireVersion(protocolVersion);
            turnId = requireNonBlank(turnId, "turnId");
            message = Objects.requireNonNull(message, "message");
            finishReason = Objects.requireNonNull(finishReason, "finishReason");
        }
    }

    public record Error(ErrorCode code, String message, boolean retryable, String providerRequestId,
                        String providerCode, Map<String, String> details) {
        public Error {
            code = Objects.requireNonNull(code, "code");
            message = requireNonBlank(message, "message");
            providerRequestId = normalizeOptional(providerRequestId);
            providerCode = normalizeOptional(providerCode);
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public sealed interface StreamEvent permits TurnStarted, TextDelta, ReasoningDelta, ToolCallStarted,
            ToolArgumentsDelta, ToolCallCompleted, UsageUpdated, TurnCompleted, ProviderError {}

    public record TurnStarted(String turnId) implements StreamEvent {
        public TurnStarted { turnId = requireNonBlank(turnId, "turnId"); }
    }

    public record TextDelta(String turnId, String delta) implements StreamEvent {
        public TextDelta {
            turnId = requireNonBlank(turnId, "turnId");
            delta = Objects.requireNonNull(delta, "delta");
        }
    }

    /** May be stored for continuation but must not be rendered as hidden chain-of-thought. */
    public record ReasoningDelta(String turnId, String delta) implements StreamEvent {
        public ReasoningDelta {
            turnId = requireNonBlank(turnId, "turnId");
            delta = Objects.requireNonNull(delta, "delta");
        }
    }

    public record ToolCallStarted(String turnId, String callId, String toolName) implements StreamEvent {
        public ToolCallStarted {
            turnId = requireNonBlank(turnId, "turnId");
            callId = requireNonBlank(callId, "callId");
            toolName = requireToolName(toolName);
        }
    }

    public record ToolArgumentsDelta(String turnId, String callId, String delta) implements StreamEvent {
        public ToolArgumentsDelta {
            turnId = requireNonBlank(turnId, "turnId");
            callId = requireNonBlank(callId, "callId");
            delta = Objects.requireNonNull(delta, "delta");
        }
    }

    public record ToolCallCompleted(String turnId, ToolCallPart toolCall) implements StreamEvent {
        public ToolCallCompleted {
            turnId = requireNonBlank(turnId, "turnId");
            toolCall = Objects.requireNonNull(toolCall, "toolCall");
        }
    }

    public record UsageUpdated(String turnId, Usage usage) implements StreamEvent {
        public UsageUpdated {
            turnId = requireNonBlank(turnId, "turnId");
            usage = Objects.requireNonNull(usage, "usage");
        }
    }

    public record TurnCompleted(Turn turn) implements StreamEvent {
        public TurnCompleted { turn = Objects.requireNonNull(turn, "turn"); }
    }

    public record ProviderError(String turnId, Error error) implements StreamEvent {
        public ProviderError {
            turnId = requireNonBlank(turnId, "turnId");
            error = Objects.requireNonNull(error, "error");
        }
    }

    static int requireVersion(int version) {
        if (VERSION != version) throw new IllegalArgumentException("unsupported AI protocol version: " + version);
        return version;
    }

    static String requireToolName(String name) {
        name = requireNonBlank(name, "name");
        if (!TOOL_NAME_PATTERN.matcher(name).matches()) throw new IllegalArgumentException("invalid tool name: " + name);
        return name;
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    static String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value; }

    static JsonElement copyJson(JsonElement value) {
        return value == null ? JsonNull.INSTANCE : value.deepCopy();
    }
}
