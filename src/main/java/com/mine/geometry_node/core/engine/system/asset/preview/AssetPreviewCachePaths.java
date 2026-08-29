package com.mine.geometry_node.core.engine.system.asset.preview;

import java.nio.file.Path;

public final class AssetPreviewCachePaths {
    private AssetPreviewCachePaths() {
    }

    public static Path resolveArtifact(Path cacheRoot, String cacheKey, AssetPreviewFormat format) {
        Path root = cacheRoot.toAbsolutePath().normalize();
        String key = validateCacheKey(cacheKey);
        Path resolved = root.resolve(key.substring(0, 2))
                .resolve(key + "." + format.extension()).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Preview cache path escapes root");
        return resolved;
    }

    public static String validateCacheKey(String cacheKey) {
        String key = cacheKey != null ? cacheKey.trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (key.length() != AssetPreviewLimits.CACHE_KEY_HEX_LENGTH || !key.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException("Invalid nativepreview cache input");
        }
        return key;
    }
}
