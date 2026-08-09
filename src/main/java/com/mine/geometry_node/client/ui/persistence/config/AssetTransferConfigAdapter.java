package com.mine.geometry_node.client.ui.persistence.config;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferClientPreferences;

public final class AssetTransferConfigAdapter {
    private static final long KIBIBYTE = 1024L;
    private static final long MEBIBYTE = 1024L * 1024L;

    private AssetTransferConfigAdapter() {
    }

    public static AssetTransferClientPreferences from(AppConfig.NetworkTransferConfig config) {
        return new AssetTransferClientPreferences(
                config.maxUploadFileSizeMiB * MEBIBYTE,
                config.maxDownloadFileSizeMiB * MEBIBYTE,
                Math.toIntExact(config.chunkSizeKiB * KIBIBYTE),
                config.uploadRateLimitKiBps * KIBIBYTE,
                config.downloadRateLimitKiBps * KIBIBYTE,
                config.completedHistoryLimit,
                config.failedHistoryLimit);
    }
}
