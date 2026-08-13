package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexLayout;

public record ModelGpuLayoutGroup(
        ModelVertexLayout layout,
        int vertexStride,
        int vertexCount,
        int indexCount,
        ModelGpuBuffer vertexBuffer,
        ModelGpuBuffer indexBuffer
) {
    public ModelGpuLayoutGroup {
        if (layout == null || vertexStride < 1 || vertexCount < 0 || indexCount < 0
                || vertexBuffer == null || indexBuffer == null) {
            throw new IllegalArgumentException("invalid GPU layout group");
        }
    }
}
