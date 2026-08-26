package com.mine.geometry_node.core.engine.behavior.document;

import java.util.Objects;

/** One precise, editor-addressable behavior-tree document problem. */
public record BehaviorTreeDiagnostic(String code, String message, String nodeId, String relatedNodeId) {
    public BehaviorTreeDiagnostic {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        nodeId = nodeId != null ? nodeId : "";
        relatedNodeId = relatedNodeId != null ? relatedNodeId : "";
    }
}
