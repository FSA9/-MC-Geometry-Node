package com.mine.geometry_node.core.engine.system.asset.transfer.config;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.Duration;

/** NeoForge server configuration backing the authoritative transfer policy. */
public final class AssetTransferServerConfig {
    private static final long KIBIBYTE = 1024L;
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int MAX_FILE_MIB = Math.toIntExact(AssetTransferProtocolLimits.MAX_FILE_BYTES / MEBIBYTE);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_UPLOAD_FILE_SIZE_MIB;
    public static final ModConfigSpec.IntValue MAX_DOWNLOAD_FILE_SIZE_MIB;
    public static final ModConfigSpec.IntValue CHUNK_SIZE_KIB;
    public static final ModConfigSpec.LongValue UPLOAD_RATE_LIMIT_KIBPS;
    public static final ModConfigSpec.LongValue DOWNLOAD_RATE_LIMIT_KIBPS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_UPLOADS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_DOWNLOADS_PER_PLAYER;
    public static final ModConfigSpec.IntValue IDLE_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue PREVIEW_CACHE_MAX_SIZE_MIB;
    public static final ModConfigSpec SPEC;

    static {
        AssetTransferServerPolicy defaults = AssetTransferServerPolicy.defaults();
        BUILDER.push(AssetTransferConfigKeys.SECTION);
        MAX_UPLOAD_FILE_SIZE_MIB = BUILDER
                .comment("Maximum size in MiB of one file uploaded to this server.")
                .defineInRange(AssetTransferConfigKeys.MAX_UPLOAD_FILE_SIZE_MIB,
                        toMiB(defaults.maxUploadFileBytes()), 1, MAX_FILE_MIB);
        MAX_DOWNLOAD_FILE_SIZE_MIB = BUILDER
                .comment("Maximum size in MiB of one file downloaded from this server.")
                .defineInRange(AssetTransferConfigKeys.MAX_DOWNLOAD_FILE_SIZE_MIB,
                        toMiB(defaults.maxDownloadFileBytes()), 1, MAX_FILE_MIB);
        CHUNK_SIZE_KIB = BUILDER
                .comment("Maximum transfer chunk size in KiB. Protocol limits still apply.")
                .defineInRange(AssetTransferConfigKeys.CHUNK_SIZE_KIB,
                        Math.toIntExact(defaults.maxChunkBytes() / KIBIBYTE),
                        AssetTransferProtocolLimits.MIN_CHUNK_BYTES / 1024,
                        AssetTransferProtocolLimits.MAX_CHUNK_BYTES / 1024);
        UPLOAD_RATE_LIMIT_KIBPS = BUILDER
                .comment("Maximum upload rate per player in KiB/s.")
                .defineInRange(AssetTransferConfigKeys.UPLOAD_RATE_LIMIT_KIBPS,
                        defaults.uploadRateBytesPerSecond() / KIBIBYTE, 1L, 1_048_576L);
        DOWNLOAD_RATE_LIMIT_KIBPS = BUILDER
                .comment("Maximum download rate per player in KiB/s.")
                .defineInRange(AssetTransferConfigKeys.DOWNLOAD_RATE_LIMIT_KIBPS,
                        defaults.downloadRateBytesPerSecond() / KIBIBYTE, 1L, 1_048_576L);
        MAX_CONCURRENT_UPLOADS_PER_PLAYER = BUILDER
                .comment("Maximum simultaneous uploads for one player.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_UPLOADS_PER_PLAYER,
                        defaults.maxConcurrentUploadsPerPlayer(), 1, 64);
        MAX_CONCURRENT_DOWNLOADS_PER_PLAYER = BUILDER
                .comment("Maximum simultaneous downloads for one player.")
                .defineInRange(AssetTransferConfigKeys.MAX_CONCURRENT_DOWNLOADS_PER_PLAYER,
                        defaults.maxConcurrentDownloadsPerPlayer(), 1, 64);
        IDLE_TIMEOUT_SECONDS = BUILDER
                .comment("Seconds before an inactive transfer document is closed.")
                .defineInRange(AssetTransferConfigKeys.IDLE_TIMEOUT_SECONDS,
                        Math.toIntExact(defaults.idleTimeout().toSeconds()), 1, 3600);
        PREVIEW_CACHE_MAX_SIZE_MIB = BUILDER
                .comment("Maximum persistent nativepreview-cache size in MiB. The cache directory is the sibling "
                        + "'.geometrynode-nativepreview-cache' beside the server geometry-nodes asset root.")
                .defineInRange("previewCacheMaxSizeMiB", 512, 64, 65_536);
        BUILDER.pop();
        ScopedStateServerConfig.register(BUILDER);
        SPEC = BUILDER.build();
    }

    public static long previewCacheMaxBytes() {
        return PREVIEW_CACHE_MAX_SIZE_MIB.getAsInt() * MEBIBYTE;
    }

    private AssetTransferServerConfig() {
    }

    public static AssetTransferServerPolicy policy() {
        return new AssetTransferServerPolicy(
                MAX_UPLOAD_FILE_SIZE_MIB.getAsInt() * MEBIBYTE,
                MAX_DOWNLOAD_FILE_SIZE_MIB.getAsInt() * MEBIBYTE,
                Math.toIntExact(CHUNK_SIZE_KIB.getAsInt() * KIBIBYTE),
                UPLOAD_RATE_LIMIT_KIBPS.getAsLong() * KIBIBYTE,
                DOWNLOAD_RATE_LIMIT_KIBPS.getAsLong() * KIBIBYTE,
                MAX_CONCURRENT_UPLOADS_PER_PLAYER.getAsInt(),
                MAX_CONCURRENT_DOWNLOADS_PER_PLAYER.getAsInt(),
                Duration.ofSeconds(IDLE_TIMEOUT_SECONDS.getAsInt()));
    }

    private static int toMiB(long bytes) {
        return Math.toIntExact(bytes / MEBIBYTE);
    }
}
