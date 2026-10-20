package com.mine.geometry_node.client.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewCachePaths;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.client.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.config.ConfigManager;
import dev.architectury.platform.Platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class ClientAssetPreviewPaths {
    private ClientAssetPreviewPaths() {
    }

    public static Path root() {
        String configured = ConfigManager.INSTANCE.getConfig().previewCache.location;
        var resolved = AssetBrowserPathPolicy.resolveConfigPath(configured);
        if (resolved != null) return resolved.toPath().toAbsolutePath().normalize();
        return Platform.getGameFolder().resolve(AppConfig.PreviewCacheConfig.DEFAULT_LOCATION).toAbsolutePath().normalize();
    }

    public static Path serverRoot(String stableServerIdentity) {
        if (stableServerIdentity == null || stableServerIdentity.isBlank()) {
            throw new IllegalArgumentException("stableServerIdentity must not be blank");
        }
        var digest = AssetTransferHashing.newSha256();
        String key = AssetTransferHashing.toHex(
                digest.digest(stableServerIdentity.trim().getBytes(StandardCharsets.UTF_8)));
        return root().resolve(AssetPreviewCachePaths.validateCacheKey(key)).normalize();
    }
}
