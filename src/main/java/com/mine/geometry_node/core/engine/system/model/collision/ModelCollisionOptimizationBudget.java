package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import java.io.IOException;

/** Optional simplification may stop early; cancellation and invalid input must still fail. */
final class ModelCollisionOptimizationBudget {
    static void check(ModelCancellation token, long deadline) throws IOException {
        token.check();
        if (System.nanoTime() > deadline) throw new Exhausted();
    }

    static final class Exhausted extends IOException {
        Exhausted() { super("collision geometry processing time budget exhausted"); }
    }

    private ModelCollisionOptimizationBudget() {}
}
