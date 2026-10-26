package com.mine.geometry_node.core.engine.system.entity.preview.protocol;

public final class EntityPreviewLimits {
    public static final int MAX_TARGETS = 8;
    public static final long REQUEST_INTERVAL_NANOS = 250_000_000L;
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;
    public static final int MAX_REQUEST_BYTES = 30 * 1024;
    public static final int MAX_MESSAGE_LENGTH = 128;
    private EntityPreviewLimits() {}
}
