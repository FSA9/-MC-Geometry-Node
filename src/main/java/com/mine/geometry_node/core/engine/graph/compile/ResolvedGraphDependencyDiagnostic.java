package com.mine.geometry_node.core.engine.graph.compile;

import java.util.Objects;

/** Runtime-family-neutral dependency diagnostic retained by the effective asset view. */
public record ResolvedGraphDependencyDiagnostic(String assetId, String code, String message,
                                                String nodeId, String relatedAssetId) {
    public ResolvedGraphDependencyDiagnostic {
        assetId = Objects.requireNonNullElse(assetId, "");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        nodeId = Objects.requireNonNullElse(nodeId, "");
        relatedAssetId = Objects.requireNonNullElse(relatedAssetId, "");
    }
}
