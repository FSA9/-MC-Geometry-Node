package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.Locale;

/** Canonical SHA-256 text validation for persistent asset metadata. */
public final class AssetContentHash {
    public static final int HEX_LENGTH = 64;

    private AssetContentHash() {
    }

    public static String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) throw new IllegalArgumentException("SHA-256 value is required");
        return normalized;
    }

    public static String normalizeOptional(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty() && !normalized.matches("[0-9a-f]{" + HEX_LENGTH + "}")) {
            throw new IllegalArgumentException("Invalid SHA-256 value");
        }
        return normalized;
    }
}
