package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonElement;

import java.util.List;

/** Strict common subset of JSON Schema suitable for cross-provider tool definitions. */
public final class ToolSchemaValidator {
    private ToolSchemaValidator() {}

    public record Violation(String path, String code, String message) {}

    public static List<Violation> validateToolSchema(JsonElement schema) {
        return ToolSchemaDefinitionValidator.validate(schema);
    }

    public static List<Violation> validateArguments(JsonElement schema, JsonElement arguments) {
        List<Violation> schemaErrors = validateToolSchema(schema);
        if (!schemaErrors.isEmpty()) return schemaErrors;
        return ToolArgumentValidator.validate(schema.getAsJsonObject(), arguments);
    }

    static boolean matchesType(String type, JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        return switch (type) {
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "number" -> isNumber(value);
            case "integer" -> isInteger(value);
            default -> false;
        };
    }

    static boolean isNumber(JsonElement value) {
        return value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isNumber()
                && Double.isFinite(value.getAsDouble());
    }

    static boolean isInteger(JsonElement value) {
        if (!isNumber(value)) return false;
        try {
            return value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
