package com.mine.geometry_node.client.model.render.pipeline;

import java.util.Arrays;

/** Stable back-to-front order without allocating one object per triangle. */
final class ModelTransparentOrder {
    static long key(float eyeDepth, int triangle) {
        if (!Float.isFinite(eyeDepth) || triangle < 0) throw new IllegalArgumentException("invalid transparent sort key");
        int bits = Float.floatToIntBits(eyeDepth == 0 ? 0 : eyeDepth);
        int ordered = bits ^ ((bits >> 31) & 0x7fffffff);
        return ((long) ordered << 32) | (triangle & 0xffffffffL);
    }
    static void sort(long[] keys, int count) { Arrays.sort(keys, 0, count); }
    static int triangle(long key) { return (int) key; }
    private ModelTransparentOrder() {}
}
