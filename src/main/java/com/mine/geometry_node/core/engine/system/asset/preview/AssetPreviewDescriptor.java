package com.mine.geometry_node.core.engine.system.asset.preview;

import java.util.Locale;
import java.util.Objects;

public record AssetPreviewDescriptor(
        AssetPreviewRevision revision,
        AssetPreviewFormat format,
        int width,
        int height,
        int encodedBytes,
        String sha256
) {
    public AssetPreviewDescriptor {
        revision = Objects.requireNonNull(revision, "revision");
        format = Objects.requireNonNull(format, "format");
        if (!AssetPreviewLimits.validDimensions(width, height)) {
            throw new IllegalArgumentException("Invalid nativepreview dimensions: " + width + "x" + height);
        }
        if (!AssetPreviewLimits.validEncodedSize(encodedBytes)) {
            throw new IllegalArgumentException("Invalid nativepreview encoded size: " + encodedBytes);
        }
        sha256 = Objects.requireNonNullElse(sha256, "").toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid nativepreview SHA-256");
    }
}
