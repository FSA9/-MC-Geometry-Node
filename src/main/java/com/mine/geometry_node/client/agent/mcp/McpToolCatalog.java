package com.mine.geometry_node.client.agent.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandSpec;
import com.mine.geometry_node.client.ai.protocol.ToolContract;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic P4 projection of the production CommandRegistry into read-only MCP tools. */
public final class McpToolCatalog {
    private static final int MAX_TOOLS = 128;

    private final Map<String, CommandSpec> tools;

    public McpToolCatalog(CommandRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Map<String, CommandSpec> selected = new LinkedHashMap<>();
        for (CommandSpec command : registry.commands()) {
            if (command.exposure() != CommandSpec.Exposure.MODEL_VISIBLE
                    || command.effect() != ToolContract.CommandEffect.READ_ONLY
                    || command.riskLevel() != ToolContract.RiskLevel.READ_ONLY) {
                continue;
            }
            if (selected.size() >= MAX_TOOLS) throw new IllegalArgumentException("MCP tool catalog is too large");
            selected.put(command.name(), command);
        }
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(selected));
    }

    public List<CommandSpec> commands() { return List.copyOf(tools.values()); }

    public Optional<CommandSpec> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(tools.get(name));
    }

    public JsonArray toJson() {
        JsonArray result = new JsonArray();
        for (CommandSpec command : tools.values()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", command.name());
            tool.addProperty("description", command.description());
            tool.add("inputSchema", command.inputSchema());
            JsonObject annotations = new JsonObject();
            annotations.addProperty("readOnlyHint", true);
            annotations.addProperty("destructiveHint", false);
            annotations.addProperty("idempotentHint", true);
            annotations.addProperty("openWorldHint", false);
            tool.add("annotations", annotations);
            result.add(tool);
        }
        return result;
    }
}
