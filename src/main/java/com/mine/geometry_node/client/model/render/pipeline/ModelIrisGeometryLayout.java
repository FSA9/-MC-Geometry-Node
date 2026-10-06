package com.mine.geometry_node.client.model.render.pipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Neutral entity attributes for resident Iris vertex buffers. */
public final class ModelIrisGeometryLayout {
    private ModelIrisGeometryLayout() {}

    /** Reset baked shaderpack entity IDs without disturbing unrelated vertex attributes or buffer state. */
    public static void neutralEntityIds(ByteBuffer vertices, int vertexCount, int stride, int entityOffset) {
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

}
