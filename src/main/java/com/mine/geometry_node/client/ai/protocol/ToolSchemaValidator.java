package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict common subset of JSON Schema suitable for cross-provider tool definitions. */
public final class ToolSchemaValidator {
    private static final Set<String> KEYWORDS = Set.of(
            "type", "description", "properties", "required", "additionalProperties", "items", "enum",
            "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"
    );
    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean");

    private ToolSchemaValidator() {}

    public record Violation(String path, String code, String message) {}

    public static List<Violation> validateToolSchema(JsonElement schema) {
        List<Violation> violations = new ArrayList<>();
        validateSchemaNode(schema, "$", true, violations);
        return List.copyOf(violations);
    }

    public static List<Violation> validateArguments(JsonElement schema, JsonElement arguments) {
        List<Violation> schemaErrors = validateToolSchema(schema);
        if (!schemaErrors.isEmpty()) return schemaErrors;
        List<Violation> violations = new ArrayList<>();
        validateValue(schema.getAsJsonObject(), arguments, "$", violations);
        return List.copyOf(violations);
    }

    private static void validateSchemaNode(JsonElement element, String path, boolean root, List<Violation> out) {
        if (element == null || !element.isJsonObject()) {
            add(out, path, "schema.object_required", "schema node must be an object");
            return;
        }
        JsonObject schema = element.getAsJsonObject();
        for (String keyword : schema.keySet()) {
            if (!KEYWORDS.contains(keyword)) add(out, path + "." + keyword, "schema.unknown_keyword", "unsupported schema keyword");
        }
        String type = stringValue(schema.get("type"));
        if (type == null || !TYPES.contains(type)) {
            add(out, path + ".type", "schema.invalid_type", "type must be one supported scalar type");
            return;
        }
        if (root && !"object".equals(type)) add(out, path + ".type", "schema.root_object_required", "tool schema root must be object");
        if (schema.has("description") && stringValue(schema.get("description")) == null) {
            add(out, path + ".description", "schema.invalid_description", "description must be a string");
        }
        validateKeywordApplicability(schema, path, type, out);
        validateBounds(schema, path, out);
        validateEnum(schema, path, type, out);

        if ("object".equals(type)) {
            JsonElement additional = schema.get("additionalProperties");
            if (additional == null || !additional.isJsonPrimitive() || !additional.getAsJsonPrimitive().isBoolean()
                    || additional.getAsBoolean()) {
                add(out, path + ".additionalProperties", "schema.closed_object_required", "additionalProperties must be false");
            }
            JsonObject properties = schema.has("properties") && schema.get("properties").isJsonObject()
                    ? schema.getAsJsonObject("properties") : null;
            if (properties == null) {
                add(out, path + ".properties", "schema.properties_required", "object schema requires properties");
            } else {
                for (String name : properties.keySet()) validateSchemaNode(properties.get(name), path + ".properties." + name, false, out);
            }
            if (schema.has("required")) {
                if (!schema.get("required").isJsonArray()) {
                    add(out, path + ".required", "schema.invalid_required", "required must be an array");
                } else {
                    for (JsonElement item : schema.getAsJsonArray("required")) {
                        String name = stringValue(item);
                        if (name == null || properties == null || !properties.has(name)) {
                            add(out, path + ".required", "schema.unknown_required", "required entries must name declared properties");
                        }
                    }
                }
            }
        } else if ("array".equals(type)) {
            if (!schema.has("items")) add(out, path + ".items", "schema.items_required", "array schema requires items");
            else validateSchemaNode(schema.get("items"), path + ".items", false, out);
        }
    }

    private static void validateBounds(JsonObject schema, String path, List<Violation> out) {
        checkNumber(schema, "minimum", path, out);
        checkNumber(schema, "maximum", path, out);
        checkNonNegativeInteger(schema, "minLength", path, out);
        checkNonNegativeInteger(schema, "maxLength", path, out);
        checkNonNegativeInteger(schema, "minItems", path, out);
        checkNonNegativeInteger(schema, "maxItems", path, out);
        compareBounds(schema, "minimum", "maximum", path, out);
        compareBounds(schema, "minLength", "maxLength", path, out);
        compareBounds(schema, "minItems", "maxItems", path, out);
    }

    private static void validateEnum(JsonObject schema, String path, String type, List<Violation> out) {
        if (!schema.has("enum")) return;
        if (!schema.get("enum").isJsonArray() || schema.getAsJsonArray("enum").isEmpty()) {
            add(out, path + ".enum", "schema.invalid_enum", "enum must be a non-empty array");
            return;
        }
        for (JsonElement value : schema.getAsJsonArray("enum")) {
            if (!matchesType(type, value)) add(out, path + ".enum", "schema.enum_type_mismatch", "enum value does not match type");
        }
    }

    private static void validateValue(JsonObject schema, JsonElement value, String path, List<Violation> out) {
        String type = schema.get("type").getAsString();
        if (!matchesType(type, value)) {
            add(out, path, "arguments.type_mismatch", "expected " + type);
            return;
        }
        if (schema.has("enum") && !contains(schema.getAsJsonArray("enum"), value)) {
            add(out, path, "arguments.not_in_enum", "value is not an allowed enum member");
        }
        if ("object".equals(type)) {
            JsonObject object = value.getAsJsonObject();
            JsonObject properties = schema.getAsJsonObject("properties");
            if (schema.has("required")) for (JsonElement item : schema.getAsJsonArray("required")) {
                String name = item.getAsString();
                if (!object.has(name)) add(out, path + "." + name, "arguments.required", "required property is missing");
            }
            for (String name : object.keySet()) {
                if (!properties.has(name)) add(out, path + "." + name, "arguments.additional_property", "property is not declared");
                else validateValue(properties.getAsJsonObject(name), object.get(name), path + "." + name, out);
            }
        } else if ("array".equals(type)) {
            JsonArray array = value.getAsJsonArray();
            checkSize(schema, array.size(), path, "Items", out);
            for (int i = 0; i < array.size(); i++) validateValue(schema.getAsJsonObject("items"), array.get(i), path + "[" + i + "]", out);
        } else if ("string".equals(type)) {
            checkSize(schema, value.getAsString().length(), path, "Length", out);
        } else if ("number".equals(type) || "integer".equals(type)) {
            BigDecimal number = value.getAsBigDecimal();
            if (schema.has("minimum") && number.compareTo(schema.get("minimum").getAsBigDecimal()) < 0) add(out, path, "arguments.below_minimum", "number is below minimum");
            if (schema.has("maximum") && number.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) add(out, path, "arguments.above_maximum", "number is above maximum");
        }
    }

    private static boolean matchesType(String type, JsonElement value) {
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

    private static boolean isNumber(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && Double.isFinite(value.getAsDouble());
    }

    private static boolean isInteger(JsonElement value) {
        if (!isNumber(value)) return false;
        try {
            return value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void validateKeywordApplicability(JsonObject schema, String path, String type, List<Violation> out) {
        Set<String> allowed = switch (type) {
            case "object" -> Set.of("type", "description", "enum", "properties", "required", "additionalProperties");
            case "array" -> Set.of("type", "description", "enum", "items", "minItems", "maxItems");
            case "string" -> Set.of("type", "description", "enum", "minLength", "maxLength");
            case "number", "integer" -> Set.of("type", "description", "enum", "minimum", "maximum");
            case "boolean" -> Set.of("type", "description", "enum");
            default -> Set.of("type");
        };
        for (String keyword : schema.keySet()) {
            if (KEYWORDS.contains(keyword) && !allowed.contains(keyword)) {
                add(out, path + "." + keyword, "schema.keyword_not_applicable", "keyword is not valid for type " + type);
            }
        }
    }

    private static void checkNumber(JsonObject schema, String name, String path, List<Violation> out) {
        if (schema.has(name) && !isNumber(schema.get(name))) add(out, path + "." + name, "schema.invalid_bound", "bound must be a finite number");
    }

    private static void checkNonNegativeInteger(JsonObject schema, String name, String path, List<Violation> out) {
        if (!schema.has(name)) return;
        JsonElement value = schema.get(name);
        if (!isInteger(value) || value.getAsBigDecimal().signum() < 0) {
            add(out, path + "." + name, "schema.invalid_bound", "bound must be a non-negative integer");
        }
    }

    private static void compareBounds(JsonObject schema, String min, String max, String path, List<Violation> out) {
        if (schema.has(min) && schema.has(max) && isNumber(schema.get(min)) && isNumber(schema.get(max))
                && schema.get(min).getAsBigDecimal().compareTo(schema.get(max).getAsBigDecimal()) > 0) {
            add(out, path, "schema.inverted_bounds", min + " cannot exceed " + max);
        }
    }

    private static void checkSize(JsonObject schema, int size, String path, String suffix, List<Violation> out) {
        String min = "min" + suffix;
        String max = "max" + suffix;
        BigDecimal actual = BigDecimal.valueOf(size);
        if (schema.has(min) && actual.compareTo(schema.get(min).getAsBigDecimal()) < 0) {
            add(out, path, "arguments.too_short", "value is smaller than " + min);
        }
        if (schema.has(max) && actual.compareTo(schema.get(max).getAsBigDecimal()) > 0) {
            add(out, path, "arguments.too_long", "value is larger than " + max);
        }
    }

    private static boolean contains(JsonArray array, JsonElement value) {
        for (JsonElement item : array) if (item.equals(value)) return true;
        return false;
    }

    private static String stringValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static void add(List<Violation> out, String path, String code, String message) {
        out.add(new Violation(path, code, message));
    }
}
