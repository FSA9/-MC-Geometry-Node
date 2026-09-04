package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable fingerprint of the normalized JSON representation of a graph document. */
public final class GraphAssetFingerprint {
    private GraphAssetFingerprint() {
    }

    public static String of(JsonElement document) {
        if (document == null) throw new IllegalArgumentException("Graph document cannot be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(document.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
