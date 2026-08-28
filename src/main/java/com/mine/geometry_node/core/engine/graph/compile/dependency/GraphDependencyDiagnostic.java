package com.mine.geometry_node.core.engine.graph.compile.dependency;

import java.util.Objects;

/** Runtime-family-neutral dependency diagnostic retained by the effective asset view. */
public record GraphDependencyDiagnostic(String assetId, String code, String message,
                                        String nodeId, String relatedAssetId) {
    public GraphDependencyDiagnostic {
        assetId = Objects.requireNonNullElse(assetId, "");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        nodeId = Objects.requireNonNullElse(nodeId, "");
        relatedAssetId = Objects.requireNonNullElse(relatedAssetId, "");
    }
}
