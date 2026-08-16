package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class GlbJson {
    private GlbJson() {
    }

    static JsonObject parse(byte[] encoded) throws ModelImportException {
        int length = encoded.length;
        while (length > 0 && (encoded[length - 1] == 0x20 || encoded[length - 1] == 0)) length--;
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded, 0, length)).toString();
        } catch (CharacterCodingException exception) {
            throw GlbFailures.invalid("glb.json", "JSON chunk is not valid UTF-8");
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) throw GlbFailures.invalid("glb.json", "GLB JSON root must be an object");
            return root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw GlbFailures.invalid("glb.json", "GLB JSON is malformed");
        }
    }

    static JsonArray array(JsonObject object, String key) throws ModelImportException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return new JsonArray();
        if (!value.isJsonArray()) throw GlbFailures.invalid(key, key + " must be an array");
        return value.getAsJsonArray();
    }

    static JsonObject object(JsonElement value, String location) throws ModelImportException {
        if (value == null || !value.isJsonObject()) throw GlbFailures.invalid(location, location + " must be an object");
        return value.getAsJsonObject();
    }

    static int requiredInt(JsonObject object, String key, String location) throws ModelImportException {
        if (!object.has(key)) throw GlbFailures.invalid(location + "." + key, "required integer is missing");
        return integer(object.get(key), location + "." + key);
    }

    static int optionalInt(JsonObject object, String key, int fallback, String location) throws ModelImportException {
        return object.has(key) ? integer(object.get(key), location + "." + key) : fallback;
    }

    static int nonNegativeInt(JsonObject object, String key, int fallback, String location) throws ModelImportException {
        int value = optionalInt(object, key, fallback, location);
        if (value < 0) throw GlbFailures.invalid(location + "." + key, "value must not be negative");
        return value;
    }

    static float number(JsonElement element, String location) throws ModelImportException {
        try {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw GlbFailures.invalid(location, "value must be a number");
            }
            float value = element.getAsFloat();
            if (!Float.isFinite(value)) throw GlbFailures.invalid(location, "number must be finite");
            return value;
        } catch (NumberFormatException exception) {
            throw GlbFailures.invalid(location, "number is invalid");
        }
    }

    static float[] floatArray(JsonObject object, String key, int size, float[] fallback,
                              String location) throws ModelImportException {
        if (!object.has(key)) return fallback.clone();
        JsonElement element = object.get(key);
        if (!element.isJsonArray() || element.getAsJsonArray().size() != size) {
            throw GlbFailures.invalid(location + "." + key, "array has an invalid length");
        }
        float[] values = new float[size];
        for (int i = 0; i < size; i++) values[i] = number(element.getAsJsonArray().get(i), location + "." + key + "[" + i + "]");
        return values;
    }

    static String string(JsonObject object, String key, String fallback, String location) throws ModelImportException {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw GlbFailures.invalid(location + "." + key, "value must be a string");
        }
        return value.getAsString();
    }

    static boolean bool(JsonObject object, String key, boolean fallback, String location) throws ModelImportException {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw GlbFailures.invalid(location + "." + key, "value must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonElement element, String location) throws ModelImportException {
        try {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw GlbFailures.invalid(location, "value must be an integer");
            }
            java.math.BigDecimal decimal = element.getAsBigDecimal();
            return decimal.intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw GlbFailures.invalid(location, "integer is invalid or out of range");
        }
    }
}
