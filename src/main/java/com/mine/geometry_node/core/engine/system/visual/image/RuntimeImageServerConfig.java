package com.mine.geometry_node.core.engine.system.visual.image;

import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative limits for runtime image decoding and caching. */
public final class RuntimeImageServerConfig {
    private static final int MEBIBYTE = 1024 * 1024;
    private static final int DEFAULT_MAX_DIMENSION = 8_192;
    private static final long DEFAULT_MAX_PIXELS = 16L * 1024L * 1024L;

    private static ModConfigSpec.IntValue maxEncodedSizeMiB;
    private static ModConfigSpec.IntValue maxDimension;
    private static ModConfigSpec.LongValue maxPixels;
    private static ModConfigSpec.IntValue cacheMaxEntries;
    private static ModConfigSpec.IntValue cacheMaxSizeMiB;

    private RuntimeImageServerConfig() {
    }

    public static synchronized void register(ModConfigSpec.Builder builder) {
        if (maxEncodedSizeMiB != null) {
            throw new IllegalStateException("Runtime image server settings are already registered");
        }
        builder.push("runtimeImages");
        maxEncodedSizeMiB = builder
                .comment("Maximum encoded size of one server runtime image in MiB.")
                .defineInRange("maxEncodedSizeMiB", AssetTransferLimits.MAX_FILE_BYTES / MEBIBYTE,
                        1, AssetTransferLimits.MAX_FILE_BYTES / MEBIBYTE);
        maxDimension = builder
                .comment("Maximum width or height of one decoded server runtime image.")
                .defineInRange("maxDimension", DEFAULT_MAX_DIMENSION, 1, 65_536);
        maxPixels = builder
                .comment("Maximum decoded pixel count of one server runtime image.")
                .defineInRange("maxPixels", DEFAULT_MAX_PIXELS, 1L, 1L << 32);
        cacheMaxEntries = builder
                .comment("Maximum number of decoded runtime image sources retained by the server cache.")
                .defineInRange("cacheMaxEntries", 128, 1, 65_536);
        cacheMaxSizeMiB = builder
                .comment("Maximum encoded size of the server runtime image cache in MiB.")
                .defineInRange("cacheMaxSizeMiB", 64, 1, 65_536);
        builder.pop();
    }

    public static int maxEncodedBytes() {
        return Math.multiplyExact(requireRegistered(maxEncodedSizeMiB).getAsInt(), MEBIBYTE);
    }

    public static int maxDimension() {
        return requireRegistered(maxDimension).getAsInt();
    }

    public static long maxPixels() {
        return requireRegistered(maxPixels).getAsLong();
    }

    public static int cacheMaxEntries() {
        return requireRegistered(cacheMaxEntries).getAsInt();
    }

    public static long cacheMaxBytes() {
        return Math.multiplyExact(
                (long) requireRegistered(cacheMaxSizeMiB).getAsInt(), MEBIBYTE);
    }

    private static <T extends ModConfigSpec.ConfigValue<?>> T requireRegistered(T value) {
        if (value == null) throw new IllegalStateException("Runtime image server settings are not registered");
        return value;
    }
}
