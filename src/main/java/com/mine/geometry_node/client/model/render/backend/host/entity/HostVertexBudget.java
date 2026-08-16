package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.domain.ModelMesh;
import com.mine.geometry_node.core.engine.system.model.domain.ModelNode;

/** Prevents HOST_NATIVE immediate geometry from overflowing or monopolizing the shared entity buffer. */
final class HostVertexBudget {
    static final long MAX_VERTICES_PER_FRAME = 1_000_000L;
    private static final int HOST_VERTICES_PER_TRIANGLE = 4;

    private long submitted;

    long required(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        long required = 0;
        for (int nodeIndex = 0; nodeIndex < metadata.nodeCount(); nodeIndex++) {
            if (!metadata.nodeDrawable(nodeIndex)) continue;
            ModelNode node = definition.nodes().get(nodeIndex);
            if (node.meshIndex() < 0) continue;
            ModelMesh mesh = definition.meshes().get(node.meshIndex());
            for (var primitive : mesh.primitives()) {
                required = saturatedAdd(required,
                        saturatedMultiply(primitive.triangleCount(), HOST_VERTICES_PER_TRIANGLE));
            }
        }
        return required;
    }

    boolean reserve(long vertices) {
        if (vertices < 0 || vertices > MAX_VERTICES_PER_FRAME - submitted) return false;
        submitted += vertices;
        return true;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
