package com.mine.geometry_node.core.engine.system.entity.appearance;

/** Bounds shared by entity appearance capture, indexed snapshots and wire encoding. */
public final class EntityAppearanceLimits {
    public static final int MAX_APPEARANCE_BYTES = 48 * 1024;
    public static final int MAX_TYPE_LENGTH = 256;
    public static final int MAX_NBT_DEPTH = 16;
    public static final int MAX_NBT_NODES = 2048;
    public static final int MAX_COLLECTION_LENGTH = 256;
    public static final int MAX_STRING_LENGTH = 2048;
    public static final int MAX_NBT_HEAP_BYTES = 256 * 1024;
    private EntityAppearanceLimits() {}
}
