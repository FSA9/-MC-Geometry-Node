package com.mine.geometry_node.client.model.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Size/index rules shared by the resident Iris vertex buffer and its two winding orders. */
final class ModelIrisGeometryLayout {
    private ModelIrisGeometryLayout() {}

    static long uploadBytes(int vertexCount, int stride) {
        if (vertexCount <= 0 || vertexCount % 3 != 0 || stride <= 0)
            throw new IllegalArgumentException("invalid Iris triangle layout");
        if ((long) vertexCount * stride > Integer.MAX_VALUE || (long) vertexCount * 4 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Iris primitive exceeds direct buffer limit");
        return Math.multiplyExact((long) vertexCount, (long) stride + 8);
    }

    /** Reset baked shaderpack entity IDs without disturbing unrelated vertex attributes or buffer state. */
    static void neutralEntityIds(ByteBuffer vertices, int vertexCount, int stride, int entityOffset) {
        if (entityOffset < 0) return;
        if (vertexCount <= 0 || stride < 8 || entityOffset > stride - 8
                || (long) vertexCount * stride != vertices.remaining())
            throw new IllegalArgumentException("invalid Iris entity ID layout");
        ByteBuffer data = vertices.duplicate().order(ByteOrder.nativeOrder());
        int start = data.position();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int offset = start + vertex * stride + entityOffset;
            // CapturedRenderingState defaults: entity=-1, blockEntity=0, item=-1.
            data.putShort(offset, (short) -1).putShort(offset + 2, (short) 0)
                    .putShort(offset + 4, (short) -1).putShort(offset + 6, (short) 0);
        }
    }

    static ByteBuffer indices(int vertexCount, boolean mirrored) {
        uploadBytes(vertexCount, 1);
        ByteBuffer data = ByteBuffer.allocateDirect(Math.multiplyExact(vertexCount, 4)).order(ByteOrder.nativeOrder());
        for (int i = 0; i < vertexCount; i += 3) {
            data.putInt(i).putInt(i + (mirrored ? 2 : 1)).putInt(i + (mirrored ? 1 : 2));
        }
        return data.flip();
    }
}
