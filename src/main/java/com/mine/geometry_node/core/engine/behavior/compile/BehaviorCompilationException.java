package com.mine.geometry_node.core.engine.behavior.compile;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeDiagnostic;

import java.util.List;

/** Aggregates deterministic diagnostics instead of failing on traversal order. */
public final class BehaviorCompilationException extends IllegalArgumentException {
    private final List<BehaviorTreeDiagnostic> diagnostics;

    public BehaviorCompilationException(List<BehaviorTreeDiagnostic> diagnostics) {
        super(message(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<BehaviorTreeDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String message(List<BehaviorTreeDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return "Behavior tree compilation failed";
        BehaviorTreeDiagnostic first = diagnostics.getFirst();
        return "Behavior tree compilation failed: asset=" + first.assetId()
                + ", code=" + first.code() + ", node=" + first.nodeId()
                + ", port=" + first.portId() + ", message=" + first.message()
                + (diagnostics.size() > 1 ? " (and " + (diagnostics.size() - 1) + " more)" : "");
    }
}
