package com.mine.geometry_node.client.ai.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;

import java.util.ArrayList;
import java.util.List;

/** Conservative JSON-to-port storage codec used by Agent writes. */
public final class InputValueCodec {
    private InputValueCodec() {}

    public static Object decode(PortRow row, JsonElement value) {
        PortEditCapabilityResolver.Capability capability = PortEditCapabilityResolver.resolve(row);
        if (!capability.writable()) throw new IllegalArgumentException(capability.reason());
        PortDef port = row.leftPort();
        PortType type = port.type() == null ? PortType.ANY : port.type();
        if (value == null || value.isJsonNull()) return null;
        Object decoded = switch (type) {
            case INTEGER -> exactInt(value);
            case LONG -> exactLong(value);
            case FLOAT -> finiteFloat(value);
            case BOOLEAN -> requireBoolean(value);
            case STRING, PATH -> requireString(value);
            case XYZ -> vector(value);
            case ANY -> primitive(value);
            default -> throw new IllegalArgumentException("port type is not writable by GraphPatch: " + type);
        };
        validateNumericBounds(row, decoded);
        return decoded;
    }

    private static int exactInt(JsonElement value) {
        long number = exactLong(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException("integer is out of range");
        return (int) number;
    }

    private static long exactLong(JsonElement value) {
        JsonPrimitive primitive = primitive(value, "integer value required");
        if (!primitive.isNumber()) throw new IllegalArgumentException("integer value required");
        try {
            return primitive.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("exact integer value required");
        }
    }

    private static float finiteFloat(JsonElement value) {
        JsonPrimitive primitive = primitive(value, "number value required");
        if (!primitive.isNumber()) throw new IllegalArgumentException("number value required");
        float number = primitive.getAsFloat();
        if (!Float.isFinite(number)) throw new IllegalArgumentException("finite float value required");
        return number;
    }

    private static boolean requireBoolean(JsonElement value) {
        JsonPrimitive primitive = primitive(value, "boolean value required");
        if (!primitive.isBoolean()) throw new IllegalArgumentException("boolean value required");
        return primitive.getAsBoolean();
    }

    private static String requireString(JsonElement value) {
        JsonPrimitive primitive = primitive(value, "string value required");
        if (!primitive.isString()) throw new IllegalArgumentException("string value required");
        return primitive.getAsString();
    }

    private static List<Float> vector(JsonElement value) {
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) throw new IllegalArgumentException("XYZ requires an array of three numbers");
        JsonArray array = value.getAsJsonArray();
        List<Float> result = new ArrayList<>(3);
        for (JsonElement component : array) result.add(finiteFloat(component));
        return List.copyOf(result);
    }

    private static Object primitive(JsonElement value) {
        JsonPrimitive primitive = primitive(value, "ANY GraphPatch values are limited to JSON primitives");
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) return primitive.getAsString();
        throw new IllegalArgumentException("numeric ANY values are not writable without an explicit storage type");
    }

    private static JsonPrimitive primitive(JsonElement value, String message) {
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException(message);
        return value.getAsJsonPrimitive();
    }

    private static void validateNumericBounds(PortRow row, Object decoded) {
        if (!(decoded instanceof Number number) || row.hintParams() == null) return;
        Number minimum = (Number) row.hintParams().get(PortMetaKeys.NUMERIC_MIN);
        Number maximum = (Number) row.hintParams().get(PortMetaKeys.NUMERIC_MAX);
        double value = number.doubleValue();
        if (minimum != null && value < minimum.doubleValue()) {
            throw new IllegalArgumentException("numeric value is below port minimum " + minimum);
        }
        if (maximum != null && value > maximum.doubleValue()) {
            throw new IllegalArgumentException("numeric value is above port maximum " + maximum);
        }
    }
}
