package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/** Stable fingerprint of the normalized JSON representation of a graph document. */
public final class GraphAssetFingerprint {
    private GraphAssetFingerprint() {
    }

    public static String of(JsonElement document) {
        if (document == null) throw new IllegalArgumentException("Graph document cannot be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(document).toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonElement normalize(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject source = element.getAsJsonObject();
            JsonObject normalized = new JsonObject();
            List<String> keys = new ArrayList<>(source.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                normalized.add(key, normalize(source.get(key)));
            }
            return normalized;
        }
        if (element.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                normalized.add(normalize(child));
            }
            return normalized;
        }
        return element.deepCopy();
    }
}
