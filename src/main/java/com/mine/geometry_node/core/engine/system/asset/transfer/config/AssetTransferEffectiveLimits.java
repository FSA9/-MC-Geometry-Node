package com.mine.geometry_node.core.engine.system.asset.transfer.config;

public record AssetTransferEffectiveLimits(
        long maxUploadFileBytes,
        long maxDownloadFileBytes,
        int chunkBytes,
        long uploadRateBytesPerSecond,
        long downloadRateBytesPerSecond
) {
    public static AssetTransferEffectiveLimits negotiate(AssetTransferClientPreferences client,
                                                          AssetTransferServerPolicy server) {
        return new AssetTransferEffectiveLimits(
                Math.min(client.maxUploadFileBytes(), server.maxUploadFileBytes()),
                Math.min(client.maxDownloadFileBytes(), server.maxDownloadFileBytes()),
                Math.min(client.preferredChunkBytes(), server.maxChunkBytes()),
                strictestRate(client.uploadRateBytesPerSecond(), server.uploadRateBytesPerSecond()),
                strictestRate(client.downloadRateBytesPerSecond(), server.downloadRateBytesPerSecond()));
    }

    private static long strictestRate(long clientRate, long serverRate) {
        return clientRate == 0L ? serverRate : Math.min(clientRate, serverRate);
    }
}
