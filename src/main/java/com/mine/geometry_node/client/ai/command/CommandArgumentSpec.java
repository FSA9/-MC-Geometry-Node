package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.protocol.ToolSchemaValidator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** One positional CLI argument and one property in the generated tool schema. */
public record CommandArgumentSpec(
        String name,
        String description,
        boolean required,
        JsonElement defaultValue,
        JsonObject schema,
        CompletionProvider completionProvider
) {
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> CLI_ARGUMENT_TYPES = Set.of("string", "number", "integer", "boolean", "array");

    @FunctionalInterface
    public interface CompletionProvider {
        CompletionProvider NONE = (prefix, parsedArguments, context) -> List.of();
        Collection<String> complete(String prefix, JsonObject parsedArguments, CommandInvocationContext context);
    }

    public CommandArgumentSpec {
        name = requireName(name);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description cannot be blank");
        defaultValue = defaultValue == null ? null : defaultValue.deepCopy();
        schema = schema == null ? null : schema.deepCopy();
        if (schema == null || !schema.has("type") || !schema.get("type").isJsonPrimitive()) {
            throw new IllegalArgumentException("argument schema requires a scalar type");
        }
        String type = schema.get("type").getAsString();
        if (!CLI_ARGUMENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("unsupported CLI argument type: " + type);
        }
        if (required && defaultValue != null) throw new IllegalArgumentException("required argument cannot have a default");
        if (defaultValue != null) validateDefault(name, schema, defaultValue);
        completionProvider = completionProvider == null ? CompletionProvider.NONE : completionProvider;
    }

    @Override public JsonElement defaultValue() { return defaultValue == null ? null : defaultValue.deepCopy(); }
    @Override public JsonObject schema() { return schema.deepCopy(); }

    private static String requireName(String value) {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid argument name: " + value);
        }
        return value;
    }

    private static void validateDefault(String name, JsonObject propertySchema, JsonElement value) {
        JsonObject properties = new JsonObject();
        properties.add(name, propertySchema.deepCopy());
        JsonArray required = new JsonArray();
        required.add(name);
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.add("properties", properties);
        root.add("required", required);
        root.addProperty("additionalProperties", false);
        JsonObject arguments = new JsonObject();
        arguments.add(name, value.deepCopy());
        var violations = ToolSchemaValidator.validateArguments(root, arguments);
        if (!violations.isEmpty()) throw new IllegalArgumentException("invalid default value for " + name + ": " + violations);
    }
}
