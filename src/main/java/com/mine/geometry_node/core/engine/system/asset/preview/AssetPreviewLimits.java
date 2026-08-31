package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;

public final class AssetPreviewLimits {
    public static final int FORMAT_VERSION = 4;
    public static final int TARGET_WIDTH = 256;
    public static final int TARGET_HEIGHT = 256;
    public static final int MAX_WIDTH = 512;
    public static final int MAX_HEIGHT = 512;
    public static final int MAX_PIXELS = MAX_WIDTH * MAX_HEIGHT;
    public static final int MAX_ENCODED_BYTES = 512 * 1024;
    public static final long MAX_IMAGE_SOURCE_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_SCHEMATIC_SOURCE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_IMAGE_SOURCE_WIDTH = 8192;
    public static final int MAX_IMAGE_SOURCE_HEIGHT = 8192;
    public static final long MAX_IMAGE_SOURCE_PIXELS = 16L * 1024L * 1024L;
    public static final int MAX_PATH_LENGTH = 1024;
    public static final int MAX_CHUNK_BYTES = AssetTransferProtocolLimits.MAX_CHUNK_BYTES;
    public static final int CACHE_KEY_HEX_LENGTH = 64;

    private AssetPreviewLimits() {
    }

    public static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_WIDTH && height <= MAX_HEIGHT
                && (long) width * height <= MAX_PIXELS;
    }

    public static boolean validEncodedSize(long encodedBytes) {
        return encodedBytes > 0 && encodedBytes <= MAX_ENCODED_BYTES;
    }

    public static boolean validImageSourceDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_IMAGE_SOURCE_WIDTH
                && height <= MAX_IMAGE_SOURCE_HEIGHT
                && (long) width * height <= MAX_IMAGE_SOURCE_PIXELS;
    }
}
