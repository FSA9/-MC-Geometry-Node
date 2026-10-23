package com.mine.geometry_node.core.engine.system.model.gltf;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.util.*;

/** Strict, pointer-aware structural access shared by importer stages. */
final class GltfJson {
    private GltfJson() { }
    static ModelProblem invalid(String path, String detail) { return new ModelProblem(ModelProblem.Code.INVALID_SCENE, path, detail); }
    static JsonArray array(JsonObject o, String key, String path) throws ModelProblem {
        if (!o.has(key)) return new JsonArray();
        if (!o.get(key).isJsonArray()) throw invalid(path + "/" + key, "array expected");
        return o.getAsJsonArray(key);
    }
    static JsonObject object(JsonElement e, String path) throws ModelProblem {
        if (e == null || !e.isJsonObject()) throw invalid(path, "object expected");
        return e.getAsJsonObject();
    }
    static JsonObject optionalObject(JsonObject o, String key, String path) throws ModelProblem {
        return o.has(key) ? object(o.get(key), path + "/" + key) : new JsonObject();
    }
    static JsonObject at(JsonArray a, int i, String path) throws ModelProblem {
        if (i < 0 || i >= a.size()) throw invalid(path + "/" + i, "index out of range");
        return object(a.get(i), path + "/" + i);
    }
    static String string(JsonObject o, String key, String fallback, String path) throws ModelProblem {
        if (!o.has(key)) return fallback;
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid(path + "/" + key, "string expected");
        return e.getAsString();
    }
    static long integer(JsonElement e, String path) throws ModelProblem {
        try {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            return e.getAsBigDecimal().longValueExact();
        } catch (RuntimeException ex) { throw invalid(path, "exact integer expected"); }
    }
    static long number(JsonObject o, String key, long fallback, String path) throws ModelProblem {
        return o.has(key) ? integer(o.get(key), path + "/" + key) : fallback;
    }
    static int index(JsonElement e, String path) throws ModelProblem {
        long n = integer(e, path);
        if (n < 0 || n > Integer.MAX_VALUE) throw invalid(path, "nonnegative index expected");
        return (int)n;
    }
    static int index(JsonObject o, String key, int fallback, String path) throws ModelProblem {
        return o.has(key) ? index(o.get(key), path + "/" + key) : fallback;
    }
    static float finite(JsonElement e, String path) throws ModelProblem {
        try {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            float f = e.getAsFloat(); if (!Float.isFinite(f)) throw new ArithmeticException(); return f;
        } catch (RuntimeException ex) { throw invalid(path, "finite float expected"); }
    }
    static float scalar(JsonObject o, String key, float fallback, String path) throws ModelProblem {
        return o.has(key) ? finite(o.get(key), path + "/" + key) : fallback;
    }
    static float[] vector(JsonObject o, String key, float[] fallback, String path) throws ModelProblem {
        if (!o.has(key)) return fallback.clone();
        JsonArray a = array(o, key, path);
        if (a.size() != fallback.length) throw invalid(path + "/" + key, "expected " + fallback.length + " components");
        float[] r = new float[a.size()]; for (int i = 0; i < r.length; i++) r[i] = finite(a.get(i), path + "/" + key + "/" + i); return r;
    }
    static boolean bool(JsonObject o, String key, boolean fallback, String path) throws ModelProblem {
        if (!o.has(key)) return fallback;
        JsonElement e = o.get(key); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw invalid(path + "/" + key, "boolean expected");
        return e.getAsBoolean();
    }
    static List<String> strings(JsonObject o, String key) throws ModelProblem {
        JsonArray a = array(o, key, ""); ArrayList<String> r = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            JsonElement e = a.get(i); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid("/" + key + "/" + i, "string expected");
            if (r.contains(e.getAsString())) throw invalid("/" + key + "/" + i, "duplicate extension"); r.add(e.getAsString());
        }
        return List.copyOf(r);
    }
}
