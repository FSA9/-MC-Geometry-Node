package com.mine.geometry_node.client.integration.ai.graph;

import com.mine.geometry_node.client.feature.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.feature.ai.command.CommandResult;
import com.mine.geometry_node.client.feature.ai.command.GraphPatchCommandTarget;
import com.mine.geometry_node.client.feature.ai.graph.GraphPatch;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Worker-facing patch capability captured on the UI thread. The captured presence is only an
 * admission check, not authorization to commit: the transaction revalidates the live target,
 * session, scope and revision on the UI thread when capturing and committing its plan.
 */
public final class PreparedGraphPatchTarget implements GraphPatchCommandTarget {
    private final boolean graphPresent;
    private final BiFunction<GraphPatch, CommandInvocationContext.CancellationToken, CommandResult> apply;

    PreparedGraphPatchTarget(boolean graphPresent,
                            BiFunction<GraphPatch, CommandInvocationContext.CancellationToken, CommandResult> apply) {
        this.graphPresent = graphPresent;
        this.apply = Objects.requireNonNull(apply, "apply");
    }

    @Override
    public boolean hasGraph() {
        return graphPresent;
    }

    @Override
    public CommandResult applyGraphPatch(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        return apply.apply(patch, cancellation);
    }
}
