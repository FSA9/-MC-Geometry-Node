package com.mine.geometry_node.core.engine.graph;

import java.util.Locale;
import java.util.Objects;

/**
 * Registered definition of a graph family. The id is serialized in
 * {@code graph_kind}; the translation key provides its editor-facing name and
 * the icon color identifies graph files in the asset browser.
 */
public record GraphType(String id, String translationKey, int assetIconColor) {
    public GraphType {
        id = normalizeId(id);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Graph type id cannot be empty");
        }
        translationKey = Objects.requireNonNullElse(translationKey, "").trim();
        if (translationKey.isEmpty()) {
            throw new IllegalArgumentException("Graph type translation key cannot be empty");
        }
    }

    public static String normalizeId(String id) {
        return id != null ? id.trim().toLowerCase(Locale.ROOT) : "";
    }
}
