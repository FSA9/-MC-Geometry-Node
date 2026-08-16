package com.mine.geometry_node.core.engine.system.model.identity;

public record ModelAssetRevision(long sourceSize, long sourceLastModified, String contentHash) {
    public ModelAssetRevision {
        if (sourceSize < 0L || sourceLastModified < 0L) {
            throw new IllegalArgumentException("source revision values must not be negative");
        }
        contentHash = contentHash == null ? "" : contentHash.trim().toLowerCase(java.util.Locale.ROOT);
        if (!contentHash.isEmpty() && !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be an empty value or a SHA-256 hex string");
        }
    }
}
