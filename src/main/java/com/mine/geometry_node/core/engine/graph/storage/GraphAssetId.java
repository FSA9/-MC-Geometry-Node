package com.mine.geometry_node.core.engine.graph.storage;

import org.jetbrains.annotations.Nullable;

/** Canonical graph asset identity shared by storage, bindings and runtimes. */
public final class GraphAssetId {
    private GraphAssetId() {
    }

    public static String canonicalize(@Nullable String rawId) {
        if (rawId == null || rawId.isBlank()) return "";
        return GraphPathMapper.normalizeId(rawId.trim());
    }

    public static String require(String rawId) {
        String canonicalId = canonicalize(rawId);
        if (canonicalId.isEmpty()) {
            throw new IllegalArgumentException("Graph id cannot be empty");
        }
        return canonicalId;
    }
}
