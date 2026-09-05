package com.mine.geometry_node.client.ai.command;

import java.util.List;

/** Runtime-neutral read-only graph and node-catalog queries used by P2 tools. */
public interface GraphQueryTarget extends CommandInvocationContext.CommandTarget {
    CommandResult searchNodeTypes(String query, String path, boolean recursive, int offset, int limit);

    CommandResult browseNodeCatalog(String path, boolean recursive, int offset, int limit);

    CommandResult getNodeTypeDetails(String typeId);

    CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit);

    CommandResult queryGraphNodes(List<String> nodeIds, List<String> typeIds, String directory, String query,
                                  String commentFilter, String connectionState, List<String> select,
                                  String connectionDirection, List<String> connectionKinds,
                                  int offset, int limit);

    CommandResult getGraphMetadata(List<String> select);

    CommandResult queryGraphFrames(List<String> frameIds, String query, List<String> tags, String parentFrame,
                                   List<String> select, int offset, int limit);

    CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit);

    CommandResult validateGraph(int offset, int limit);

    CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit);
}
