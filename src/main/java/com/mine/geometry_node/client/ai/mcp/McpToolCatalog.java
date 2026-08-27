package com.mine.geometry_node.client.ai.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandSpec;
import com.mine.geometry_node.client.ai.protocol.ToolContract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic projection of read tools and dry-run-approved reversible writes. */
public final class McpToolCatalog {
    private static final int MAX_TOOLS = 128;

    private final Map<String, CommandSpec> tools;
    private final List<CommandSpec> commands;
    private final JsonArray json;

    public McpToolCatalog(CommandRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Map<String, CommandSpec> selected = new LinkedHashMap<>();
        for (CommandSpec command : registry.commands()) {
            boolean readOnly = command.effect() == ToolContract.CommandEffect.READ_ONLY
                    && command.riskLevel() == ToolContract.RiskLevel.READ_ONLY;
            boolean approvedWrite = command.effect() == ToolContract.CommandEffect.GRAPH_WRITE
                    && command.riskLevel() == ToolContract.RiskLevel.REVERSIBLE_EDIT
                    && command.supportsDryRun();
            if (command.exposure() != CommandSpec.Exposure.MODEL_VISIBLE || !readOnly && !approvedWrite) {
                continue;
            }
            if (selected.size() >= MAX_TOOLS) throw new IllegalArgumentException("MCP tool catalog is too large");
            selected.put(command.name(), command);
        }
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(selected));
        commands = List.copyOf(tools.values());
        json = projectTools(commands);
    }

    public List<CommandSpec> commands() { return commands; }

    public Optional<CommandSpec> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(tools.get(name));
    }

    public JsonArray toJson() {
        return json.deepCopy();
    }

    private static JsonArray projectTools(List<CommandSpec> commands) {
        JsonArray result = new JsonArray();
        for (CommandSpec command : commands) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", command.name());
            tool.addProperty("description", command.description());
            tool.add("inputSchema", command.inputSchema());
            JsonObject annotations = new JsonObject();
            boolean readOnly = command.effect() == ToolContract.CommandEffect.READ_ONLY;
            annotations.addProperty("readOnlyHint", readOnly);
            annotations.addProperty("destructiveHint", !readOnly);
            annotations.addProperty("idempotentHint", readOnly || command.supportsDryRun());
            annotations.addProperty("openWorldHint", false);
            tool.add("annotations", annotations);
            result.add(tool);
        }
        return result;
    }
}
