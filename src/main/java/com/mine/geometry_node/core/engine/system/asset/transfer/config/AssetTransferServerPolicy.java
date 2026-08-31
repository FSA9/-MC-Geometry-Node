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
        int maxConcurrentUploadsGlobal,
        int maxConcurrentDownloadsGlobal,
        int maxQueuedTransfers,
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
        maxConcurrentUploadsGlobal = Math.max(1, maxConcurrentUploadsGlobal);
        maxConcurrentDownloadsGlobal = Math.max(1, maxConcurrentDownloadsGlobal);
        maxQueuedTransfers = Math.max(1, maxQueuedTransfers);
        idleTimeout = idleTimeout != null && !idleTimeout.isNegative() && !idleTimeout.isZero()
                ? idleTimeout : Duration.ofSeconds(30);
    }

    public static AssetTransferServerPolicy defaults() {
        long mebibyte = 1024L * 1024L;
        return new AssetTransferServerPolicy(
                32L * mebibyte, 32L * mebibyte, 24 * 1024,
                mebibyte, mebibyte, 2, 2, 8, 8, 256, Duration.ofSeconds(30));
    }
}
