package com.mine.geometry_node.core.engine.system.asset.transfer.config;

public record AssetTransferClientPreferences(
        long maxUploadFileBytes,
        long maxDownloadFileBytes,
        int preferredChunkBytes,
        long uploadRateBytesPerSecond,
        long downloadRateBytesPerSecond,
        int completedHistoryLimit,
        int failedHistoryLimit
) {
    public AssetTransferClientPreferences {
        maxUploadFileBytes = AssetTransferProtocolLimits.clampFileBytes(maxUploadFileBytes);
        maxDownloadFileBytes = AssetTransferProtocolLimits.clampFileBytes(maxDownloadFileBytes);
        preferredChunkBytes = AssetTransferProtocolLimits.clampChunkBytes(preferredChunkBytes);
        uploadRateBytesPerSecond = Math.max(0L, uploadRateBytesPerSecond);
        downloadRateBytesPerSecond = Math.max(0L, downloadRateBytesPerSecond);
        completedHistoryLimit = Math.max(0, completedHistoryLimit);
        failedHistoryLimit = Math.max(0, failedHistoryLimit);
    }
}
