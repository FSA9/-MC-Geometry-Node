package com.mine.geometry_node.client.runtime.model.render.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded staging data; final resident buffers and draw count are independent of chunk count. */
final class ModelGeometryChunks {
    static final int MAX_VERTICES = 1536;
    private ModelGeometryChunks() {}

    static int nextCount(int total, int completed, int stride, boolean mirroredIndices, long byteBudget) {
        if (total <= 0 || total % 3 != 0 || completed < 0 || completed >= total || completed % 3 != 0
                || stride <= 0 || byteBudget <= 0) throw new IllegalArgumentException("invalid geometry chunk range/budget");
        long triangleBytes = 3L * (stride + (mirroredIndices ? 8L : 4L));
        // A sub-triangle budget still allows a single complete triangle to make progress.
        long triangles = Math.max(1, Math.min(MAX_VERTICES / 3, byteBudget / triangleBytes));
        return (int) Math.min(total - completed, triangles * 3);
    }

    static ByteBuffer indices(int first, int count, boolean mirrored) {
        if (first < 0 || first % 3 != 0 || count <= 0 || count % 3 != 0
                || (long) first + count > Integer.MAX_VALUE || count > MAX_VERTICES)
            throw new IllegalArgumentException("invalid geometry index chunk");
        ByteBuffer data = ByteBuffer.allocateDirect(count * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int i = first; i < first + count; i += 3)
            data.putInt(i).putInt(i + (mirrored ? 2 : 1)).putInt(i + (mirrored ? 1 : 2));
        return data.flip();
    }
}
