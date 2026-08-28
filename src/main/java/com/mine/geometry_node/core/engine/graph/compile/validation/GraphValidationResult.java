package com.mine.geometry_node.core.engine.graph.compile.validation;

import java.util.List;

/** Immutable result of the validation shared by all executable graph families. */
public record GraphValidationResult(List<GraphDiagnostic> diagnostics) {
    public GraphValidationResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public boolean isValid() {
        return diagnostics.isEmpty();
    }
}
