package com.mine.geometry_node.core.engine.system.model.importer;

public final class ModelImportSession {
    private final ModelImportContext context;
    private final ModelImportBudgetTracker tracker;

    ModelImportSession(ModelImportContext context) {
        this.context = context;
        this.tracker = new ModelImportBudgetTracker(context.budget());
    }

    public ModelImportBudget budget() { return context.budget(); }
    public ModelCancellationToken cancellation() { return context.cancellation(); }
    public ModelImportBudgetTracker budgetTracker() { return tracker; }
    public void diagnose(ModelImportDiagnostic diagnostic) { context.diagnostics().accept(diagnostic); }

    public void checkpoint(String location) throws ModelImportException {
        cancellation().throwIfCancelled(location);
    }
}
