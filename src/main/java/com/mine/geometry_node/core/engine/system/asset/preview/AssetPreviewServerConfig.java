package com.mine.geometry_node.core.engine.system.asset.preview;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AssetPreviewServerConfig {
    private static final long MEBIBYTE = 1024L * 1024L;
    private static ModConfigSpec.IntValue cacheMaxSizeMiB;

    private AssetPreviewServerConfig() {
    }

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("previewCache");
        cacheMaxSizeMiB = builder
                .comment("Maximum persistent preview-cache size in MiB. The cache directory is the sibling "
                        + "'.geometrynode-nativepreview-cache' beside the server geometry-nodes asset root.")
                .defineInRange("maxSizeMiB", 512, 64, 65_536);
        builder.pop();
    }

    public static long cacheMaxBytes() {
        if (cacheMaxSizeMiB == null) throw new IllegalStateException("Preview server config is not registered");
        return cacheMaxSizeMiB.getAsInt() * MEBIBYTE;
    }
}
