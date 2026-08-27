package com.mine.geometry_node.core.engine.graph;

import java.util.Locale;
import java.util.Objects;

/**
 * Registered definition of a graph family. The id is serialized in
 * {@code graph_kind}; the translation input provides its editor-facing name and
 * the icon color identifies graph files in the asset browser.
 */
public record GraphType(String id, String translationKey, int assetIconColor, GraphKind runtimeKind,
                        boolean authorable) {
    /** Preserves the existing extension API; custom legacy graph types use the blueprint runtime. */
    public GraphType(String id, String translationKey, int assetIconColor) {
        this(id, translationKey, assetIconColor, GraphKind.BLUEPRINT, true);
    }

    public GraphType {
        id = normalizeId(id);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Graph type id cannot be empty");
        }
        translationKey = Objects.requireNonNullElse(translationKey, "").trim();
        if (translationKey.isEmpty()) {
            throw new IllegalArgumentException("Graph type translation input cannot be empty");
        }
        runtimeKind = Objects.requireNonNull(runtimeKind, "runtimeKind");
        if (runtimeKind == GraphKind.UNKNOWN) {
            throw new IllegalArgumentException("Graph type runtime kind cannot be unknown");
        }
    }

    public static String normalizeId(String id) {
        return id != null ? id.trim().toLowerCase(Locale.ROOT) : "";
    }
}
