package com.mine.geometry_node.client.model.runtime;

import java.nio.file.Path;

public record LocalModelStatus(ModelLoadState state, Path path, String failure,
                               long sourceBytes, long triangles, int drawCalls,
                               long submittedTriangles, int singularTransformSkips,
                               long loadNanos, long lastRenderCpuNanos, long lastGpuNanos) {
    public LocalModelStatus {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        failure = failure == null ? "" : failure;
    }
}
