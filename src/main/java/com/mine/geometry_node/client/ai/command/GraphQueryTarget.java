package com.mine.geometry_node.client.ai.command;

/** Runtime-neutral read-only graph and node-catalog queries used by P2 tools. */
public interface GraphQueryTarget extends CommandInvocationContext.CommandTarget {
    CommandResult searchNodeTypes(String query, int offset, int limit);

    CommandResult getNodeTypeDetails(String typeId);

    CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit);

    CommandResult searchGraphNodes(String query, String typeId, String category, String commentFilter,
                                   String connectionState, int offset, int limit);

    CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit);

    CommandResult getNodeDetails(String nodeId);

    CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit);

    CommandResult getGraphContext(String focusNodeId, int depth, int offset, int limit);

    CommandResult validateGraph(int offset, int limit);

    CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit);
}
