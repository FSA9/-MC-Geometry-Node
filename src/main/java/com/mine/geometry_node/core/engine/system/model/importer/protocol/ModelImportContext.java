package com.mine.geometry_node.core.engine.system.model.importer.protocol;

import java.util.function.Consumer;

public record ModelImportContext(ModelImportBudget budget, ModelCancellationToken cancellation,
                                 Consumer<ModelImportDiagnostic> diagnostics) {
    public ModelImportContext {
        budget = budget == null ? ModelImportBudget.DEFAULT : budget;
        cancellation = cancellation == null ? ModelCancellationToken.NONE : cancellation;
        diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public static ModelImportContext defaults() {
        return new ModelImportContext(ModelImportBudget.DEFAULT, ModelCancellationToken.NONE, null);
    }
}
