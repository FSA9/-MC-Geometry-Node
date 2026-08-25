package com.mine.geometry_node.client.ai.command;

import com.mine.geometry_node.client.ai.graph.GraphPatch;

/** Graph target capable of running the approved GraphPatch transaction pipeline. */
public interface GraphPatchCommandTarget extends CommandInvocationContext.CommandTarget {
    CommandResult applyGraphPatch(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation);
}
