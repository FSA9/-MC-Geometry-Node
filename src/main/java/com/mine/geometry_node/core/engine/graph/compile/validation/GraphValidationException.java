package com.mine.geometry_node.core.engine.graph.compile.validation;

import java.util.List;

/** Reports fatal graph problems without tying diagnostics to one runtime family. */
public final class GraphValidationException extends IllegalArgumentException {
    private final List<GraphDiagnostic> diagnostics;

    public GraphValidationException(List<GraphDiagnostic> diagnostics) {
        super(message(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<GraphDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String message(List<GraphDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return "Graph validation failed";
        GraphDiagnostic first = diagnostics.getFirst();
        return "Graph validation failed: asset=" + first.assetId()
                + ", code=" + first.code() + ", node=" + first.nodeId()
                + ", port=" + first.portId() + ", message=" + first.message()
                + (diagnostics.size() > 1 ? " (and " + (diagnostics.size() - 1) + " more)" : "");
    }
}
