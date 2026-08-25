package com.mine.geometry_node.client.ai.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Validates argument values against an already validated tool schema. */
final class ToolArgumentValidator {
    private ToolArgumentValidator() {}

    static List<ToolSchemaValidator.Violation> validate(JsonObject schema, JsonElement arguments) {
        List<ToolSchemaValidator.Violation> violations = new ArrayList<>();
        validateValue(schema, arguments, "$", violations);
        return List.copyOf(violations);
    }

    private static void validateValue(JsonObject schema, JsonElement value, String path,
                                      List<ToolSchemaValidator.Violation> out) {
        String type = schema.get("type").getAsString();
        if (!ToolSchemaValidator.matchesType(type, value)) {
            add(out, path, "arguments.type_mismatch", "expected " + type);
            return;
        }
        if (schema.has("enum") && !contains(schema.getAsJsonArray("enum"), value)) {
            add(out, path, "arguments.not_in_enum", "value is not an allowed enum member");
        }
        if ("object".equals(type)) {
            validateObject(schema, value.getAsJsonObject(), path, out);
        } else if ("array".equals(type)) {
            JsonArray array = value.getAsJsonArray();
            checkSize(schema, array.size(), path, "Items", out);
            for (int i = 0; i < array.size(); i++) {
                validateValue(schema.getAsJsonObject("items"), array.get(i), path + "[" + i + "]", out);
            }
        } else if ("string".equals(type)) {
            checkSize(schema, value.getAsString().length(), path, "Length", out);
        } else if ("number".equals(type) || "integer".equals(type)) {
            validateNumberBounds(schema, value.getAsBigDecimal(), path, out);
        }
    }

    private static void validateObject(JsonObject schema, JsonObject object, String path,
                                       List<ToolSchemaValidator.Violation> out) {
        JsonObject properties = schema.getAsJsonObject("properties");
        if (schema.has("required")) {
            for (JsonElement item : schema.getAsJsonArray("required")) {
                String name = item.getAsString();
                if (!object.has(name)) {
                    add(out, path + "." + name, "arguments.required", "required property is missing");
                }
            }
        }
        for (String name : object.keySet()) {
            if (!properties.has(name)) {
                add(out, path + "." + name, "arguments.additional_property", "property is not declared");
            } else {
                validateValue(properties.getAsJsonObject(name), object.get(name), path + "." + name, out);
            }
        }
    }

    private static void validateNumberBounds(JsonObject schema, BigDecimal number, String path,
                                             List<ToolSchemaValidator.Violation> out) {
        if (schema.has("minimum") && number.compareTo(schema.get("minimum").getAsBigDecimal()) < 0) {
            add(out, path, "arguments.below_minimum", "number is below minimum");
        }
        if (schema.has("maximum") && number.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) {
            add(out, path, "arguments.above_maximum", "number is above maximum");
        }
    }

    private static void checkSize(JsonObject schema, int size, String path, String suffix,
                                  List<ToolSchemaValidator.Violation> out) {
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
        for (JsonElement item : array) {
            if (item.equals(value)) return true;
        }
        return false;
    }

    private static void add(List<ToolSchemaValidator.Violation> out, String path, String code, String message) {
        out.add(new ToolSchemaValidator.Violation(path, code, message));
    }
}
