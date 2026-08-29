package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CmdDissolveNodeGroup implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String mGroupNodeId;

    private boolean mPrepared;
    private boolean mCanExecute;
    private Map<String, NodeData> mBeforeNodes;
    private Map<String, NodeData> mAfterNodes;

    public CmdDissolveNodeGroup(GraphController controller, NodeGraph graph, String groupNodeId) {
        this.mController = controller;
        this.mGraph = graph;
        this.mGroupNodeId = groupNodeId;
    }

    public boolean canExecute() {
        prepare();
        return mCanExecute;
    }

    @Override
    public void execute() {
        prepare();
        if (!mCanExecute) return;
        replaceNodes(mAfterNodes);
    }

    @Override
    public void undo() {
        if (!mCanExecute || mBeforeNodes == null) return;
        replaceNodes(mBeforeNodes);
    }

    private void prepare() {
        if (mPrepared) return;
        mPrepared = true;

        if (mGraph == null || mGraph.nodes == null || mGroupNodeId == null) {
            return;
        }

        mBeforeNodes = deepCopyNodes(mGraph.nodes);
        mAfterNodes = buildDissolvedNodes(deepCopyNodes(mBeforeNodes));
        mCanExecute = mAfterNodes != null;
    }

    private Map<String, NodeData> buildDissolvedNodes(Map<String, NodeData> scopeNodes) {
        NodeData groupNode = scopeNodes.get(mGroupNodeId);
        if (groupNode == null || !groupNode.isGroupNode()) return null;

        GroupNodeFactory.ensureBoundaryNodes(groupNode);
        NodeData groupInputNode = groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID);

        Map<String, List<Connection>> dataInputBridges = groupInputNode != null && groupInputNode.outputs != null
                ? groupInputNode.outputs
                : Map.of();
        Map<String, Connection> flowInputBridges = groupInputNode != null && groupInputNode.execOutputs != null
                ? groupInputNode.execOutputs
                : Map.of();
        Map<String, List<Connection>> dataOutputBridges = groupNode.outputs != null ? groupNode.outputs : Map.of();
        Map<String, Connection> flowOutputBridges = groupNode.execOutputs != null ? groupNode.execOutputs : Map.of();

        Set<String> groupDataInputsWithExternalConnections = new HashSet<>();
        Map<String, NodeData> after = new LinkedHashMap<>();

        for (Map.Entry<String, NodeData> entry : scopeNodes.entrySet()) {
            if (mGroupNodeId.equals(entry.getKey())) continue;

            NodeData node = entry.getValue();
            rewriteExternalDataInputConnections(node, groupNode.id, dataInputBridges, groupDataInputsWithExternalConnections);
            rewriteExternalFlowInputConnections(node, groupNode.id, flowInputBridges);
            after.put(entry.getKey(), node);
        }

        for (Map.Entry<String, NodeData> entry : groupNode.ensureSubNodes().entrySet()) {
            if (isGroupBoundaryId(entry.getKey())) continue;

            NodeData node = entry.getValue();
            node.parentFrame = groupNode.parentFrame;
            rewritePromotedDataOutputConnections(node, dataOutputBridges);
            rewritePromotedFlowOutputConnections(node, flowOutputBridges);
            after.put(entry.getKey(), node);
        }

        applyGroupInputDefaults(groupNode, groupInputNode, groupDataInputsWithExternalConnections);
        return deepCopyNodes(after);
    }

    private void rewriteExternalDataInputConnections(
            NodeData node,
            String groupNodeId,
            Map<String, List<Connection>> inputBridges,
            Set<String> groupInputsWithExternalConnections
    ) {
        if (node == null || node.outputs == null) return;

        Map<String, List<Connection>> originalOutputs = node.outputs;
        node.outputs = new LinkedHashMap<>();
        for (Map.Entry<String, List<Connection>> entry : originalOutputs.entrySet()) {
            if (entry.getValue() == null) continue;
            for (Connection link : entry.getValue()) {
                if (link == null) continue;

                if (groupNodeId.equals(link.targetNodeId())) {
                    groupInputsWithExternalConnections.add(link.targetPortName());
                    List<Connection> bridges = inputBridges.get(link.targetPortName());
                    if (bridges == null) continue;
                    for (Connection bridge : bridges) {
                        if (bridge != null && !isGroupBoundaryId(bridge.targetNodeId())) {
                            addDataConnection(node, entry.getKey(), bridge.targetNodeId(), bridge.targetPortName());
                        }
                    }
                } else {
                    addDataConnection(node, entry.getKey(), link.targetNodeId(), link.targetPortName());
                }
            }
        }
    }

    private void rewriteExternalFlowInputConnections(
            NodeData node,
            String groupNodeId,
            Map<String, Connection> inputBridges
    ) {
        if (node == null || node.execOutputs == null) return;

        Map<String, Connection> originalOutputs = node.execOutputs;
        node.execOutputs = new LinkedHashMap<>();
        for (Map.Entry<String, Connection> entry : originalOutputs.entrySet()) {
            Connection link = entry.getValue();
            if (link == null) continue;

            if (groupNodeId.equals(link.targetNodeId())) {
                Connection bridge = inputBridges.get(link.targetPortName());
                if (bridge != null && !isGroupBoundaryId(bridge.targetNodeId())) {
                    node.addExecutionConnection(entry.getKey(), bridge.targetNodeId(), bridge.targetPortName());
                }
            } else {
                node.addExecutionConnection(entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }
    }

    private void rewritePromotedDataOutputConnections(
            NodeData node,
            Map<String, List<Connection>> outputBridges
    ) {
        if (node == null || node.outputs == null) return;

        Map<String, List<Connection>> originalOutputs = node.outputs;
        node.outputs = new LinkedHashMap<>();
        for (Map.Entry<String, List<Connection>> entry : originalOutputs.entrySet()) {
            if (entry.getValue() == null) continue;
            for (Connection link : entry.getValue()) {
                if (link == null) continue;

                if (GroupNodeTypes.GROUP_OUT_ID.equals(link.targetNodeId())) {
                    List<Connection> bridges = outputBridges.get(link.targetPortName());
                    if (bridges == null) continue;
                    for (Connection bridge : bridges) {
                        if (bridge != null && !mGroupNodeId.equals(bridge.targetNodeId())) {
                            addDataConnection(node, entry.getKey(), bridge.targetNodeId(), bridge.targetPortName());
                        }
                    }
                } else if (!isGroupBoundaryId(link.targetNodeId())) {
                    addDataConnection(node, entry.getKey(), link.targetNodeId(), link.targetPortName());
                }
            }
        }
    }

    private void rewritePromotedFlowOutputConnections(
            NodeData node,
            Map<String, Connection> outputBridges
    ) {
        if (node == null || node.execOutputs == null) return;

        Map<String, Connection> originalOutputs = node.execOutputs;
        node.execOutputs = new LinkedHashMap<>();
        for (Map.Entry<String, Connection> entry : originalOutputs.entrySet()) {
            Connection link = entry.getValue();
            if (link == null) continue;

            if (GroupNodeTypes.GROUP_OUT_ID.equals(link.targetNodeId())) {
                Connection bridge = outputBridges.get(link.targetPortName());
                if (bridge != null && !mGroupNodeId.equals(bridge.targetNodeId())) {
                    node.addExecutionConnection(entry.getKey(), bridge.targetNodeId(), bridge.targetPortName());
                }
            } else if (!isGroupBoundaryId(link.targetNodeId())) {
                node.addExecutionConnection(entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }
    }

    private void applyGroupInputDefaults(
            NodeData groupNode,
            NodeData groupInputNode,
            Set<String> groupInputsWithExternalConnections
    ) {
        if (groupNode == null || groupInputNode == null || groupInputNode.outputs == null || groupNode.inputs == null) {
            return;
        }

        for (Map.Entry<String, List<Connection>> entry : groupInputNode.outputs.entrySet()) {
            String portId = entry.getKey();
            if (groupInputsWithExternalConnections.contains(portId) || !groupNode.inputs.containsKey(portId)) {
                continue;
            }

            Object value = groupNode.inputs.get(portId);
            if (entry.getValue() == null) continue;
            for (Connection link : entry.getValue()) {
                if (link == null || isGroupBoundaryId(link.targetNodeId())) continue;
                NodeData targetNode = groupNode.subNodes.get(link.targetNodeId());
                if (targetNode != null) {
                    targetNode.inputs.put(link.targetPortName(), value);
                }
            }
        }
    }

    private void addDataConnection(NodeData node, String outPortId, String targetNodeId, String targetPortId) {
        if (node == null || outPortId == null || targetNodeId == null || targetPortId == null) return;
        if (node.outputs == null) {
            node.outputs = new LinkedHashMap<>();
        }

        List<Connection> links = node.outputs.computeIfAbsent(outPortId, ignored -> new ArrayList<>());
        for (Connection link : links) {
            if (targetNodeId.equals(link.targetNodeId()) && targetPortId.equals(link.targetPortName())) {
                return;
            }
        }
        links.add(new Connection(targetNodeId, targetPortId));
    }

    private boolean isGroupBoundaryId(String nodeId) {
        return GroupNodeTypes.GROUP_IN_ID.equals(nodeId) || GroupNodeTypes.GROUP_OUT_ID.equals(nodeId);
    }

    private void replaceNodes(Map<String, NodeData> replacement) {
        Map<String, NodeData> replacementCopy = deepCopyNodes(replacement);
        Set<String> existingIds = new HashSet<>(mGraph.nodes.keySet());
        for (String id : existingIds) {
            mController.removeNode(id);
        }

        mGraph.nodes.clear();
        for (NodeData node : replacementCopy.values()) {
            mController.addNode(node);
        }
        refreshFrameBounds();
        mController.getContext().notifyGraphConnectionsRebuildRequested();
    }

    private void refreshFrameBounds() {
        if (mGraph.frames == null) return;
        for (String frameId : new ArrayList<>(mGraph.frames.keySet())) {
            mController.updateFrameBounds(frameId);
        }
    }

    private Map<String, NodeData> deepCopyNodes(Map<String, NodeData> nodes) {
        NodeGraph tempGraph = new NodeGraph();
        tempGraph.nodes = new LinkedHashMap<>();
        if (nodes != null) {
            tempGraph.nodes.putAll(nodes);
        }
        return new LinkedHashMap<>(GraphJsonIO.fromJson(GraphJsonIO.toJson(tempGraph)).nodes);
    }
}
