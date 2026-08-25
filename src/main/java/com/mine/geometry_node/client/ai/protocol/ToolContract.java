package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonElement;

import java.util.Objects;

/** Metadata shared by CLI commands and model-visible tools. */
public final class ToolContract {
    private ToolContract() {}

    public enum CommandEffect { READ_ONLY, GRAPH_WRITE, EXTERNAL_SIDE_EFFECT }
    public enum RiskLevel { READ_ONLY, REVERSIBLE_EDIT, WORLD_MUTATION, PRIVILEGED_COMMAND, EXTERNAL_IO, UNKNOWN_PLUGIN }
    public enum PermissionMode { CHAT_ONLY, ASK_BEFORE_EDITS, AUTO_APPLY_REVERSIBLE }
    public enum AuthorizationDecision { ALLOW, REQUIRE_CONFIRMATION, DENY }

    public record ToolSpec(AiProtocol.ToolDefinition definition, CommandEffect effect, RiskLevel riskLevel) {
        public ToolSpec {
            definition = Objects.requireNonNull(definition, "definition");
            effect = Objects.requireNonNull(effect, "effect");
            riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
            if (effect == CommandEffect.READ_ONLY && riskLevel != RiskLevel.READ_ONLY) {
                throw new IllegalArgumentException("read-only effects require READ_ONLY risk");
            }
            if (effect != CommandEffect.READ_ONLY && riskLevel == RiskLevel.READ_ONLY) {
                throw new IllegalArgumentException("side-effecting tools cannot declare READ_ONLY risk");
            }
            var schemaViolations = ToolSchemaValidator.validateToolSchema(definition.inputSchema());
            if (!schemaViolations.isEmpty()) {
                throw new IllegalArgumentException("invalid tool input schema: " + schemaViolations);
            }
        }

        public JsonElement inputSchema() { return definition.inputSchema(); }
        public String name() { return definition.name(); }
        public String description() { return definition.description(); }
    }

    public static AuthorizationDecision authorize(PermissionMode mode, ToolSpec tool) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(tool, "tool");
        if (mode == PermissionMode.CHAT_ONLY) return AuthorizationDecision.DENY;
        if (tool.effect() == CommandEffect.EXTERNAL_SIDE_EFFECT) return AuthorizationDecision.DENY;
        if (tool.effect() == CommandEffect.READ_ONLY) return AuthorizationDecision.ALLOW;
        if (mode == PermissionMode.ASK_BEFORE_EDITS) return AuthorizationDecision.REQUIRE_CONFIRMATION;
        return tool.riskLevel() == RiskLevel.REVERSIBLE_EDIT
                ? AuthorizationDecision.ALLOW
                : AuthorizationDecision.REQUIRE_CONFIRMATION;
    }
}
