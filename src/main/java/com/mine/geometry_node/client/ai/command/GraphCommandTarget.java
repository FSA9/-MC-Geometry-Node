package com.mine.geometry_node.client.ai.command;

import java.util.Collection;

/** Graph operations required by the P1 built-ins, independent of UI/document implementations. */
public interface GraphCommandTarget extends CommandInvocationContext.CommandTarget {
    enum PortDirection { INPUT, OUTPUT }

    Collection<String> registeredNodeTypeIds();
    Collection<String> nodeIds();
    Collection<String> portIds(String nodeId, PortDirection direction);

    CommandResult addNode(String typeId, double x, double y, String requestedNodeId);
    CommandResult deleteNode(String nodeId);
    CommandResult connect(String outputNodeId, String outputPortId, String inputNodeId, String inputPortId);
}
