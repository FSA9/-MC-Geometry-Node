package com.mine.geometry_node.core.engine.system.asset.transfer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.Duration;

/** NeoForge server configuration backing the authoritative transfer policy. */
public final class AssetTransferServerConfig {
    private static final long KIBIBYTE = 1024L;
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int MAX_FILE_MIB = Math.toIntExact(AssetTransferProtocolLimits.MAX_FILE_BYTES / MEBIBYTE);

    private static ModConfigSpec.IntValue maxUploadFileSizeMiB;
    private static ModConfigSpec.IntValue maxDownloadFileSizeMiB;
    private static ModConfigSpec.IntValue chunkSizeKiB;
    private static ModConfigSpec.LongValue uploadRateLimitKiBps;
    private static ModConfigSpec.LongValue downloadRateLimitKiBps;
    private static ModConfigSpec.IntValue maxConcurrentUploadsPerPlayer;
    private static ModConfigSpec.IntValue maxConcurrentDownloadsPerPlayer;
    private static ModConfigSpec.IntValue maxConcurrentUploadsGlobal;
    private static ModConfigSpec.IntValue maxConcurrentDownloadsGlobal;
    private static ModConfigSpec.IntValue maxQueuedTransfers;
    private static ModConfigSpec.IntValue idleTimeoutSeconds;

    public static void register(ModConfigSpec.Builder builder) {
        AssetTransferServerPolicy defaults = AssetTransferServerPolicy.defaults();
        builder.push(AssetTransferConfigKeys.SECTION);
        maxUploadFileSizeMiB = builder
                .comment("Maximum size in MiB of one file uploaded to this server.")
                .defineInRange(AssetTransferConfigKeys.MAX_UPLOAD_FILE_SIZE_MIB,
                        toMiB(defaults.maxUploadFileBytes()), 1, MAX_FILE_MIB);
        maxDownloadFileSizeMiB = builder
                .comment("Maximum size in MiB of one file downloaded from this server.")
                .defineInRange(AssetTransferConfigKeys.MAX_DOWNLOAD_FILE_SIZE_MIB,
                        toMiB(defaults.maxDownloadFileBytes()), 1, MAX_FILE_MIB);
        chunkSizeKiB = builder
                .comment("Maximum transfer chunk size in KiB. Protocol limits still apply.")
                .defineInRange(AssetTransferConfigKeys.CHUNK_SIZE_KIB,
                        Math.toIntExact(defaults.maxChunkBytes() / KIBIBYTE),
                        AssetTransferProtocolLimits.MIN_CHUNK_BYTES / 1024,
                        AssetTransferProtocolLimits.MAX_CHUNK_BYTES / 1024);
        uploadRateLimitKiBps = builder
                .comment("Maximum upload rate per player in KiB/s.")
                .defineInRange(AssetTransferConfigKeys.UPLOAD_RATE_LIMIT_KIBPS,
                        defaults.uploadRateBytesPerSecond() / KIBIBYTE, 1L, 1_048_576L);
        downloadRateLimitKiBps = builder
                .comment("Maximum download rate per player in KiB/s.")
                .defineInRange(AssetTransferConfigKeys.DOWNLOAD_RATE_LIMIT_KIBPS,
                        defaults.downloadRateBytesPerSecond() / KIBIBYTE, 1L, 1_048_576L);
        maxConcurrentUploadsPerPlayer = builder
                .comment("Maximum simultaneous uploads for one player.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_UPLOADS_PER_PLAYER,
                        defaults.maxConcurrentUploadsPerPlayer(), 1, 64);
        maxConcurrentDownloadsPerPlayer = builder
                .comment("Maximum simultaneous downloads for one player.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_DOWNLOADS_PER_PLAYER,
                        defaults.maxConcurrentDownloadsPerPlayer(), 1, 64);
        maxConcurrentUploadsGlobal = builder
                .comment("Maximum simultaneous uploads across all players.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_UPLOADS_GLOBAL,
                        defaults.maxConcurrentUploadsGlobal(), 1, 1024);
        maxConcurrentDownloadsGlobal = builder
                .comment("Maximum simultaneous downloads across all players.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_DOWNLOADS_GLOBAL,
                        defaults.maxConcurrentDownloadsGlobal(), 1, 1024);
        maxQueuedTransfers = builder
                .comment("Maximum number of server-side transfers waiting for a slot across both directions.")
                .defineInRange(AssetTransferConfigKeys.MAX_QUEUED_TRANSFERS,
                        defaults.maxQueuedTransfers(), 1, 65536);
        idleTimeoutSeconds = builder
                .comment("Seconds before an inactive transfer document is closed.")
                .defineInRange(AssetTransferConfigKeys.IDLE_TIMEOUT_SECONDS,
                        Math.toIntExact(defaults.idleTimeout().toSeconds()), 1, 3600);
        builder.pop();
    }

    private AssetTransferServerConfig() {
    }

    public static AssetTransferServerPolicy policy() {
        return new AssetTransferServerPolicy(
                maxUploadFileSizeMiB.getAsInt() * MEBIBYTE,
                maxDownloadFileSizeMiB.getAsInt() * MEBIBYTE,
                Math.toIntExact(chunkSizeKiB.getAsInt() * KIBIBYTE),
                uploadRateLimitKiBps.getAsLong() * KIBIBYTE,
                downloadRateLimitKiBps.getAsLong() * KIBIBYTE,
                maxConcurrentUploadsPerPlayer.getAsInt(),
                maxConcurrentDownloadsPerPlayer.getAsInt(),
                maxConcurrentUploadsGlobal.getAsInt(),
                maxConcurrentDownloadsGlobal.getAsInt(),
                maxQueuedTransfers.getAsInt(),
                Duration.ofSeconds(idleTimeoutSeconds.getAsInt()));
    }

    private static int toMiB(long bytes) {
        return Math.toIntExact(bytes / MEBIBYTE);
    }
}
