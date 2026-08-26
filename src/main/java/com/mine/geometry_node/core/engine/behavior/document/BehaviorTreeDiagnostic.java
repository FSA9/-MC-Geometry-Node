package com.mine.geometry_node.core.engine.behavior.document;

import java.util.Objects;

/** One precise, editor-addressable behavior-tree document or compilation problem. */
public record BehaviorTreeDiagnostic(String assetId, String code, String message,
                                     String nodeId, String portId, String relatedNodeId) {
    public BehaviorTreeDiagnostic(String code, String message, String nodeId, String relatedNodeId) {
        this("", code, message, nodeId, "", relatedNodeId);
    }

    public BehaviorTreeDiagnostic {
        assetId = assetId != null ? assetId : "";
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        nodeId = nodeId != null ? nodeId : "";
        portId = portId != null ? portId : "";
        relatedNodeId = relatedNodeId != null ? relatedNodeId : "";
    }

    public BehaviorTreeDiagnostic withAssetId(String value) {
        return new BehaviorTreeDiagnostic(value, code, message, nodeId, portId, relatedNodeId);
    }
}
