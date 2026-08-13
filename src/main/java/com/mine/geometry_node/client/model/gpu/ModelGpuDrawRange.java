package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;

public record ModelGpuDrawRange(
        int nodeIndex,
        int meshIndex,
        int primitiveIndex,
        int layoutGroupIndex,
        int firstIndex,
        int indexCount,
        int materialIndex,
        ModelBounds localBounds
) {
    public ModelGpuDrawRange {
        if (nodeIndex < 0 || meshIndex < 0 || primitiveIndex < 0 || layoutGroupIndex < 0
                || firstIndex < 0 || indexCount < 0 || materialIndex < 0) {
            throw new IllegalArgumentException("draw range values must not be negative");
        }
        if (localBounds == null) throw new IllegalArgumentException("localBounds must not be null");
    }
}
