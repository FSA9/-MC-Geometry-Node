package com.mine.geometry_node.core.engine.graph.compile.validation;

import java.util.Objects;

/** One editor-addressable problem found while compiling a graph document. */
public record GraphDiagnostic(String assetId, String code, String message,
                              String nodeId, String portId, String relatedNodeId) {
    public GraphDiagnostic {
        assetId = assetId != null ? assetId : "";
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        nodeId = nodeId != null ? nodeId : "";
        portId = portId != null ? portId : "";
        relatedNodeId = relatedNodeId != null ? relatedNodeId : "";
    }
}
