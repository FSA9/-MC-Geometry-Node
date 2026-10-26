package com.mine.geometry_node.client.feature.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewCachePaths;
import com.mine.geometry_node.core.engine.system.asset.AssetHashing;
import dev.architectury.platform.Platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class ClientAssetPreviewPaths {
    private ClientAssetPreviewPaths() {
    }

    public static Path root() {
        return Platform.getGameFolder().resolve("geometry_nodes/caches/asset_previews").toAbsolutePath().normalize();
    }

    public static Path serverRoot(String stableServerIdentity) {
        if (stableServerIdentity == null || stableServerIdentity.isBlank()) {
            throw new IllegalArgumentException("stableServerIdentity must not be blank");
        }
        var digest = AssetHashing.newSha256();
        String key = AssetHashing.toHex(
                digest.digest(stableServerIdentity.trim().getBytes(StandardCharsets.UTF_8)));
        return root().resolve(AssetPreviewCachePaths.validateCacheKey(key)).normalize();
    }
}
