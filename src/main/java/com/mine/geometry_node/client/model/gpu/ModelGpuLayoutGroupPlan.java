package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexLayout;

import java.util.Arrays;

public final class ModelGpuLayoutGroupPlan {
    private final ModelVertexLayout layout;
    private final int vertexStride;
    private final int vertexCount;
    private final byte[] vertexData;
    private final byte[] indexData;

    public ModelGpuLayoutGroupPlan(ModelVertexLayout layout, int vertexStride, int vertexCount,
                                   byte[] vertexData, byte[] indexData) {
        if (layout == null || vertexStride < 1 || vertexCount < 0 || vertexData == null || indexData == null) {
            throw new IllegalArgumentException("invalid layout group plan");
        }
        if (vertexData.length != Math.multiplyExact(vertexStride, vertexCount) || (indexData.length & 3) != 0) {
            throw new IllegalArgumentException("layout group byte lengths are inconsistent");
        }
        this.layout = layout;
        this.vertexStride = vertexStride;
        this.vertexCount = vertexCount;
        this.vertexData = Arrays.copyOf(vertexData, vertexData.length);
        this.indexData = Arrays.copyOf(indexData, indexData.length);
    }

    public ModelVertexLayout layout() { return layout; }
    public int vertexStride() { return vertexStride; }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexData.length / 4; }
    public byte[] vertexData() { return Arrays.copyOf(vertexData, vertexData.length); }
    public byte[] indexData() { return Arrays.copyOf(indexData, indexData.length); }
}
