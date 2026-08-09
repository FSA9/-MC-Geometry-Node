package com.mine.geometry_node.core.engine.system.asset.transfer.config;

public final class AssetTransferProtocolLimits {
    public static final int MIN_CHUNK_BYTES = 4 * 1024;
    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024;
    private AssetTransferProtocolLimits() {
    }

    public static int clampChunkBytes(int value) {
        return Math.clamp(value, MIN_CHUNK_BYTES, MAX_CHUNK_BYTES);
    }

    public static long clampFileBytes(long value) {
        return Math.clamp(value, 1L, MAX_FILE_BYTES);
    }
}
