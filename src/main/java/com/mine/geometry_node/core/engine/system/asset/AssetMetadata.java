package com.mine.geometry_node.core.engine.system.asset;

import java.util.Objects;

/** Content-aware identity used by storage, transfer and client presentation. */
public record AssetMetadata(String typeId, String variantId) {
    public static final AssetMetadata UNKNOWN = new AssetMetadata("", "");

    public AssetMetadata {
        typeId = normalize(typeId);
        variantId = normalize(variantId);
    }

    public boolean isKnown() {
        return !typeId.isEmpty();
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
