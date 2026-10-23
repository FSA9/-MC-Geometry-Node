package com.mine.geometry_node.core.engine.system.entitypreview.protocol;

public final class EntityPreviewLimits {
    public static final int MAX_TARGETS = 8;
    public static final long REQUEST_INTERVAL_NANOS = 250_000_000L;
    public static final int MAX_APPEARANCE_BYTES = 48 * 1024;
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;
    public static final int MAX_REQUEST_BYTES = 30 * 1024;
    public static final int MAX_MESSAGE_LENGTH = 128;
    public static final int MAX_TYPE_LENGTH = 256;
    public static final int MAX_NBT_DEPTH = 16;
    public static final int MAX_NBT_NODES = 2048;
    public static final int MAX_COLLECTION_LENGTH = 256;
    public static final int MAX_STRING_LENGTH = 2048;
    public static final int MAX_NBT_HEAP_BYTES = 256 * 1024;
    private EntityPreviewLimits() {}
}
