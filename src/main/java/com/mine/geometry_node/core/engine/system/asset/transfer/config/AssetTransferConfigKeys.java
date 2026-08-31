package com.mine.geometry_node.core.engine.system.asset.transfer.config;

/** Stable field names shared by client preferences and authoritative server policy. */
public final class AssetTransferConfigKeys {
    public static final String SECTION = "networkTransfer";
    public static final String MAX_UPLOAD_FILE_SIZE_MIB = "maxUploadFileSizeMiB";
    public static final String MAX_DOWNLOAD_FILE_SIZE_MIB = "maxDownloadFileSizeMiB";
    public static final String CHUNK_SIZE_KIB = "chunkSizeKiB";
    public static final String UPLOAD_RATE_LIMIT_KIBPS = "uploadRateLimitKiBps";
    public static final String DOWNLOAD_RATE_LIMIT_KIBPS = "downloadRateLimitKiBps";

    public static final String MAX_CONCURRENT_UPLOADS_PER_PLAYER = "maxConcurrentUploadsPerPlayer";
    public static final String MAX_CONCURRENT_DOWNLOADS_PER_PLAYER = "maxConcurrentDownloadsPerPlayer";
    public static final String MAX_CONCURRENT_UPLOADS_GLOBAL = "maxConcurrentUploadsGlobal";
    public static final String MAX_CONCURRENT_DOWNLOADS_GLOBAL = "maxConcurrentDownloadsGlobal";
    public static final String MAX_QUEUED_TRANSFERS = "maxQueuedTransfers";
    public static final String IDLE_TIMEOUT_SECONDS = "idleTimeoutSeconds";

    private AssetTransferConfigKeys() {
    }

    public static String clientId(String key) {
        return SECTION + "." + key;
    }
}
