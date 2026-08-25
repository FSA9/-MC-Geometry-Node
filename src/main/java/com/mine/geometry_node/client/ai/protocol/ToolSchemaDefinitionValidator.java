package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates the supported JSON Schema vocabulary without inspecting argument values. */
final class ToolSchemaDefinitionValidator {
    private static final Set<String> KEYWORDS = Set.of(
            "type", "description", "properties", "required", "additionalProperties", "items", "enum",
            "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"
    );
    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean");
    private static final Set<String> OBJECT_KEYWORDS = Set.of(
            "type", "description", "enum", "properties", "required", "additionalProperties");
    private static final Set<String> ARRAY_KEYWORDS = Set.of(
            "type", "description", "enum", "items", "minItems", "maxItems");
    private static final Set<String> STRING_KEYWORDS = Set.of(
            "type", "description", "enum", "minLength", "maxLength");
    private static final Set<String> NUMBER_KEYWORDS = Set.of(
            "type", "description", "enum", "minimum", "maximum");
    private static final Set<String> BOOLEAN_KEYWORDS = Set.of("type", "description", "enum");
    private static final Set<String> TYPE_ONLY_KEYWORDS = Set.of("type");

    private ToolSchemaDefinitionValidator() {}

    static List<ToolSchemaValidator.Violation> validate(JsonElement schema) {
        List<ToolSchemaValidator.Violation> violations = new ArrayList<>();
        validateNode(schema, "$", true, violations);
        return List.copyOf(violations);
    }

    private static void validateNode(JsonElement element, String path, boolean root,
                                     List<ToolSchemaValidator.Violation> out) {
        if (element == null || !element.isJsonObject()) {
            add(out, path, "schema.object_required", "schema node must be an object");
            return;
        }
        JsonObject schema = element.getAsJsonObject();
        for (String keyword : schema.keySet()) {
            if (!KEYWORDS.contains(keyword)) {
                add(out, path + "." + keyword, "schema.unknown_keyword", "unsupported schema keyword");
            }
        }
        String type = stringValue(schema.get("type"));
        if (type == null || !TYPES.contains(type)) {
            add(out, path + ".type", "schema.invalid_type", "type must be one supported scalar type");
            return;
        }
        if (root && !"object".equals(type)) {
            add(out, path + ".type", "schema.root_object_required", "tool schema root must be object");
        }
        if (schema.has("description") && stringValue(schema.get("description")) == null) {
            add(out, path + ".description", "schema.invalid_description", "description must be a string");
        }
        validateKeywordApplicability(schema, path, type, out);
        validateBounds(schema, path, out);
        validateEnum(schema, path, type, out);

        if ("object".equals(type)) {
            validateObject(schema, path, out);
        } else if ("array".equals(type)) {
            if (!schema.has("items")) {
                add(out, path + ".items", "schema.items_required", "array schema requires items");
            } else {
                validateNode(schema.get("items"), path + ".items", false, out);
            }
        }
    }

    private static void validateObject(JsonObject schema, String path,
                                       List<ToolSchemaValidator.Violation> out) {
        JsonElement additional = schema.get("additionalProperties");
        if (additional == null || !additional.isJsonPrimitive()
                || !additional.getAsJsonPrimitive().isBoolean() || additional.getAsBoolean()) {
            add(out, path + ".additionalProperties", "schema.closed_object_required",
                    "additionalProperties must be false");
        }
        JsonObject properties = schema.has("properties") && schema.get("properties").isJsonObject()
                ? schema.getAsJsonObject("properties") : null;
        if (properties == null) {
            add(out, path + ".properties", "schema.properties_required", "object schema requires properties");
        } else {
            for (String name : properties.keySet()) {
                validateNode(properties.get(name), path + ".properties." + name, false, out);
            }
        }
        if (!schema.has("required")) return;
        if (!schema.get("required").isJsonArray()) {
            add(out, path + ".required", "schema.invalid_required", "required must be an array");
            return;
        }
        for (JsonElement item : schema.getAsJsonArray("required")) {
            String name = stringValue(item);
            if (name == null || properties == null || !properties.has(name)) {
                add(out, path + ".required", "schema.unknown_required",
                        "required entries must name declared properties");
            }
        }
    }

    private static void validateKeywordApplicability(JsonObject schema, String path, String type,
                                                     List<ToolSchemaValidator.Violation> out) {
        Set<String> allowed = switch (type) {
            case "object" -> OBJECT_KEYWORDS;
            case "array" -> ARRAY_KEYWORDS;
            case "string" -> STRING_KEYWORDS;
            case "number", "integer" -> NUMBER_KEYWORDS;
            case "boolean" -> BOOLEAN_KEYWORDS;
            default -> TYPE_ONLY_KEYWORDS;
        };
        for (String keyword : schema.keySet()) {
            if (KEYWORDS.contains(keyword) && !allowed.contains(keyword)) {
                add(out, path + "." + keyword, "schema.keyword_not_applicable",
                        "keyword is not valid for type " + type);
            }
        }
    }

    private static void validateBounds(JsonObject schema, String path,
                                       List<ToolSchemaValidator.Violation> out) {
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

    private static void validateEnum(JsonObject schema, String path, String type,
                                     List<ToolSchemaValidator.Violation> out) {
        if (!schema.has("enum")) return;
        if (!schema.get("enum").isJsonArray() || schema.getAsJsonArray("enum").isEmpty()) {
            add(out, path + ".enum", "schema.invalid_enum", "enum must be a non-empty array");
            return;
        }
        for (JsonElement value : schema.getAsJsonArray("enum")) {
            if (!ToolSchemaValidator.matchesType(type, value)) {
                add(out, path + ".enum", "schema.enum_type_mismatch", "enum value does not match type");
            }
        }
    }

    private static void checkNumber(JsonObject schema, String name, String path,
                                    List<ToolSchemaValidator.Violation> out) {
        if (schema.has(name) && !ToolSchemaValidator.isNumber(schema.get(name))) {
            add(out, path + "." + name, "schema.invalid_bound", "bound must be a finite number");
        }
    }

    private static void checkNonNegativeInteger(JsonObject schema, String name, String path,
                                                List<ToolSchemaValidator.Violation> out) {
        if (!schema.has(name)) return;
        JsonElement value = schema.get(name);
        if (!ToolSchemaValidator.isInteger(value) || value.getAsBigDecimal().signum() < 0) {
            add(out, path + "." + name, "schema.invalid_bound", "bound must be a non-negative integer");
        }
    }

    private static void compareBounds(JsonObject schema, String min, String max, String path,
                                      List<ToolSchemaValidator.Violation> out) {
        if (schema.has(min) && schema.has(max)
                && ToolSchemaValidator.isNumber(schema.get(min))
                && ToolSchemaValidator.isNumber(schema.get(max))
                && schema.get(min).getAsBigDecimal().compareTo(schema.get(max).getAsBigDecimal()) > 0) {
            add(out, path, "schema.inverted_bounds", min + " cannot exceed " + max);
        }
    }

    private static String stringValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static void add(List<ToolSchemaValidator.Violation> out, String path, String code, String message) {
        out.add(new ToolSchemaValidator.Violation(path, code, message));
    }
}
