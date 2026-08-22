package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.protocol.AiProtocol;
import com.mine.geometry_node.client.ai.protocol.ToolContract;
import com.mine.geometry_node.client.ai.protocol.ToolSchemaValidator;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Single source for CLI metadata, validation, completion, handlers, and model tool projection. */
public record CommandSpec(
        String name,
        int version,
        List<String> aliases,
        String description,
        String usage,
        List<CommandArgumentSpec> arguments,
        JsonObject outputSchema,
        ToolContract.CommandEffect effect,
        ToolContract.RiskLevel riskLevel,
        boolean requiresGraph,
        boolean supportsDryRun,
        Exposure exposure,
        CommandHandler handler
) {
    public enum Exposure { MODEL_VISIBLE, CLI_ONLY }

    @FunctionalInterface
    public interface CommandHandler {
        CommandResult execute(CommandInvocationContext context, JsonObject arguments);
    }

    public CommandSpec {
        name = normalizeName(name);
        if (version < 1) throw new IllegalArgumentException("command version must be positive");
        aliases = normalizeAliases(aliases, name);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description cannot be blank");
        if (usage == null || usage.isBlank()) throw new IllegalArgumentException("usage cannot be blank");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        validateArgumentOrder(arguments);
        outputSchema = Objects.requireNonNull(outputSchema, "outputSchema").deepCopy();
        effect = Objects.requireNonNull(effect, "effect");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        exposure = Objects.requireNonNull(exposure, "exposure");
        handler = Objects.requireNonNull(handler, "handler");

        requireValidSchema(buildInputSchema(arguments), "input");
        requireValidSchema(outputSchema, "output");
        new ToolContract.ToolSpec(toolDefinition(name, description, buildInputSchema(arguments)), effect, riskLevel);
    }

    @Override public JsonObject outputSchema() { return outputSchema.deepCopy(); }

    public JsonObject inputSchema() { return buildInputSchema(arguments); }

    public ToolContract.ToolSpec toToolSpec() {
        return new ToolContract.ToolSpec(toolDefinition(name, description, inputSchema()), effect, riskLevel);
    }

    private static AiProtocol.ToolDefinition toolDefinition(String name, String description, JsonObject schema) {
        return new AiProtocol.ToolDefinition(AiProtocol.VERSION, name, description, schema);
    }

    private static JsonObject buildInputSchema(List<CommandArgumentSpec> arguments) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (CommandArgumentSpec argument : arguments) {
            JsonObject property = argument.schema();
            property.addProperty("description", argument.description());
            properties.add(argument.name(), property);
            if (argument.required()) required.add(argument.name());
        }
        root.add("properties", properties);
        if (!required.isEmpty()) root.add("required", required);
        root.addProperty("additionalProperties", false);
        return root;
    }

    public static JsonObject objectSchema(JsonObject properties, String... requiredNames) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties == null ? new JsonObject() : properties.deepCopy());
        if (requiredNames.length > 0) {
            JsonArray required = new JsonArray();
            for (String name : requiredNames) required.add(name);
            schema.add("required", required);
        }
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    public static JsonObject scalarSchema(String type) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        return schema;
    }

    private static void requireValidSchema(JsonElement schema, String kind) {
        var violations = ToolSchemaValidator.validateToolSchema(schema);
        if (!violations.isEmpty()) throw new IllegalArgumentException("invalid command " + kind + " schema: " + violations);
    }

    static String normalizeName(String name) {
        if (name == null) throw new IllegalArgumentException("command name cannot be null");
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("invalid command name: " + name);
        return normalized;
    }

    private static List<String> normalizeAliases(List<String> aliases, String name) {
        if (aliases == null || aliases.isEmpty()) return List.of();
        List<String> normalized = aliases.stream().map(CommandSpec::normalizeName).toList();
        Set<String> unique = new HashSet<>(normalized);
        if (unique.size() != normalized.size() || unique.contains(name)) throw new IllegalArgumentException("duplicate command alias");
        return List.copyOf(normalized);
    }

    private static void validateArgumentOrder(List<CommandArgumentSpec> arguments) {
        Set<String> names = new HashSet<>();
        boolean optionalSeen = false;
        for (CommandArgumentSpec argument : arguments) {
            if (!names.add(argument.name())) throw new IllegalArgumentException("duplicate argument: " + argument.name());
            if (!argument.required()) optionalSeen = true;
            else if (optionalSeen) throw new IllegalArgumentException("required arguments cannot follow optional arguments");
        }
    }
}
