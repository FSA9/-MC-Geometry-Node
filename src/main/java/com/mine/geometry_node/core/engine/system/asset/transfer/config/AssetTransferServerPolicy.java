package com.mine.geometry_node.core.engine.system.asset.transfer.config;

import java.time.Duration;

public record AssetTransferServerPolicy(
        long maxUploadFileBytes,
        long maxDownloadFileBytes,
        int maxChunkBytes,
        long uploadRateBytesPerSecond,
        long downloadRateBytesPerSecond,
        int maxConcurrentUploadsPerPlayer,
        int maxConcurrentDownloadsPerPlayer,
        Duration idleTimeout
) {
    public AssetTransferServerPolicy {
        maxUploadFileBytes = AssetTransferProtocolLimits.clampFileBytes(maxUploadFileBytes);
        maxDownloadFileBytes = AssetTransferProtocolLimits.clampFileBytes(maxDownloadFileBytes);
        maxChunkBytes = AssetTransferProtocolLimits.clampChunkBytes(maxChunkBytes);
        uploadRateBytesPerSecond = Math.max(1L, uploadRateBytesPerSecond);
        downloadRateBytesPerSecond = Math.max(1L, downloadRateBytesPerSecond);
        maxConcurrentUploadsPerPlayer = Math.max(1, maxConcurrentUploadsPerPlayer);
        maxConcurrentDownloadsPerPlayer = Math.max(1, maxConcurrentDownloadsPerPlayer);
        idleTimeout = idleTimeout != null && !idleTimeout.isNegative() && !idleTimeout.isZero()
                ? idleTimeout : Duration.ofSeconds(30);
    }

    public static AssetTransferServerPolicy defaults() {
        long mebibyte = 1024L * 1024L;
        return new AssetTransferServerPolicy(
                32L * mebibyte, 32L * mebibyte, 24 * 1024,
                mebibyte, mebibyte, 2, 2, Duration.ofSeconds(30));
    }
}
