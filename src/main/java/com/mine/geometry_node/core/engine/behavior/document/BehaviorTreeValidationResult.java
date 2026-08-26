package com.mine.geometry_node.core.engine.behavior.document;

import java.util.List;

/** Immutable validation report used by both editor diagnostics and publishing. */
public record BehaviorTreeValidationResult(List<BehaviorTreeDiagnostic> diagnostics) {
    public BehaviorTreeValidationResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public boolean isValid() {
        return diagnostics.isEmpty();
    }

    public void requirePublishable() {
        if (!isValid()) {
            throw new IllegalStateException("Behavior tree is not publishable: "
                    + diagnostics.getFirst().code() + " - " + diagnostics.getFirst().message());
        }
    }
}
