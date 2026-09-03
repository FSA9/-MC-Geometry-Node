package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CmdGroupIntoNodeGroup implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final List<String> mSelectedNodeIds;
    private final String mGroupId;
    private final float mGroupX;
    private final float mGroupY;

    private boolean mPrepared;
    private boolean mCanExecute;
    private Map<String, NodeData> mBeforeNodes;
    private Map<String, NodeData> mAfterNodes;

    public CmdGroupIntoNodeGroup(GraphController controller, NodeGraph graph, List<String> selectedNodeIds, float groupX, float groupY) {
        this.mController = controller;
        this.mGraph = graph;
        this.mSelectedNodeIds = selectedNodeIds != null ? new ArrayList<>(selectedNodeIds) : List.of();
        this.mGroupId = UUID.randomUUID().toString();
        this.mGroupX = groupX;
        this.mGroupY = groupY;
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

        if (mGraph == null || mGraph.nodes == null || mSelectedNodeIds.isEmpty()) {
            return;
        }

        mBeforeNodes = deepCopyNodes(mGraph.nodes);
        mAfterNodes = buildGroupedNodes(deepCopyNodes(mBeforeNodes));
        mCanExecute = mAfterNodes != null;
    }

    private Map<String, NodeData> buildGroupedNodes(Map<String, NodeData> scopeNodes) {
        Set<String> selected = existingSelectedIds(scopeNodes);
        if (selected.isEmpty()) return null;

        NodeData groupNode = GroupNodeFactory.createGroupNode(mGroupId, mGroupX, mGroupY);
        groupNode.parentFrame = commonParentFrame(scopeNodes, selected);

        Map<String, NodeData> after = new LinkedHashMap<>();
        for (Map.Entry<String, NodeData> entry : scopeNodes.entrySet()) {
            if (!selected.contains(entry.getKey())) {
                after.put(entry.getKey(), entry.getValue());
            }
        }

        for (String nodeId : mSelectedNodeIds) {
            if (!selected.contains(nodeId)) continue;
            NodeData node = scopeNodes.get(nodeId);
            node.parentFrame = null;
            groupNode.attachSubNode(nodeId, node);
        }

        BoundaryPorts boundaryPorts = new BoundaryPorts();
        rewriteFlowConnections(scopeNodes, selected, groupNode, boundaryPorts);
        rewriteDataConnections(scopeNodes, selected, groupNode, boundaryPorts);
        assignBoundaryPortOrders(scopeNodes, groupNode, boundaryPorts);

        after.put(groupNode.id, groupNode);
        return deepCopyNodes(after);
    }

    private Set<String> existingSelectedIds(Map<String, NodeData> scopeNodes) {
        Set<String> selected = new HashSet<>();
        for (String nodeId : mSelectedNodeIds) {
            if (scopeNodes.containsKey(nodeId)) {
                selected.add(nodeId);
            }
        }
        return selected;
    }

    private String commonParentFrame(Map<String, NodeData> scopeNodes, Set<String> selected) {
        boolean initialized = false;
        String commonParent = null;
        for (String nodeId : selected) {
            NodeData node = scopeNodes.get(nodeId);
            String parent = node != null ? node.parentFrame : null;
            if (!initialized) {
                initialized = true;
                commonParent = parent;
            } else if (commonParent == null ? parent != null : !commonParent.equals(parent)) {
                return null;
            }
        }
        return commonParent;
    }

    private void rewriteDataConnections(Map<String, NodeData> scopeNodes, Set<String> selected,
                                        NodeData groupNode, BoundaryPorts boundaryPorts) {
        Set<BoundaryInputKey> bridgedInputs = new HashSet<>();
        Set<BoundaryOutputKey> bridgedOutputs = new HashSet<>();
        Set<BoundaryInputKey> externallyFedInputs = findExternallyFedInputs(scopeNodes, selected);

        for (NodeData node : scopeNodes.values()) {
            if (node == null || node.outputs == null) continue;

            Map<String, List<Connection>> originalOutputs = node.outputs;
            node.outputs = new LinkedHashMap<>();

            boolean sourceInside = selected.contains(node.id);
            for (Map.Entry<String, List<Connection>> outputEntry : originalOutputs.entrySet()) {
                String outPortId = outputEntry.getKey();
                List<Connection> originalLinks = outputEntry.getValue() != null
                        ? new ArrayList<>(outputEntry.getValue())
                        : List.of();

                for (Connection link : originalLinks) {
                    if (link == null) continue;
                    boolean targetInside = selected.contains(link.targetNodeId());

                    if (sourceInside && targetInside) {
                        node.addDataConnection(outPortId, link.targetNodeId(), link.targetPortName());
                    } else if (!sourceInside && targetInside) {
                        BoundaryInputKey key = new BoundaryInputKey(link.targetNodeId(), link.targetPortName());
                        String groupInputPort = boundaryPorts.dataInputs.computeIfAbsent(
                                key,
                                ignored -> createDataInputPort(groupNode, key.targetNodeId(), key.targetPortId(), scopeNodes)
                        );
                        node.addDataConnection(outPortId, groupNode.id, groupInputPort);
                        if (bridgedInputs.add(key)) {
                            NodeData groupIn = groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID);
                            groupIn.addDataConnection(groupInputPort, link.targetNodeId(), link.targetPortName());
                        }
                    } else if (sourceInside) {
                        BoundaryInputKey passthroughInput = new BoundaryInputKey(node.id, outPortId);
                        if (externallyFedInputs.contains(passthroughInput)
                                && isDataPassthroughOutput(node, outPortId)) {
                            String groupInputPort = boundaryPorts.dataInputs.computeIfAbsent(
                                    passthroughInput,
                                    ignored -> createDataInputPort(groupNode, node.id, outPortId, scopeNodes)
                            );
                            if (bridgedInputs.add(passthroughInput)) {
                                NodeData groupIn = groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID);
                                groupIn.addDataConnection(groupInputPort, node.id, outPortId);
                            }
                            groupNode.addDataConnection(groupInputPort,
                                    link.targetNodeId(), link.targetPortName());
                            continue;
                        }
                        BoundaryOutputKey key = new BoundaryOutputKey(node.id, outPortId);
                        String groupOutputPort = boundaryPorts.dataOutputs.computeIfAbsent(
                                key,
                                ignored -> createDataOutputPort(groupNode, key.sourceNodeId(), key.sourcePortId(), scopeNodes)
                        );
                        if (bridgedOutputs.add(key)) {
                            node.addDataConnection(outPortId, GroupNodeTypes.GROUP_OUT_ID, groupOutputPort);
                        }
                        groupNode.addDataConnection(groupOutputPort, link.targetNodeId(), link.targetPortName());
                    } else {
                        node.addDataConnection(outPortId, link.targetNodeId(), link.targetPortName());
                    }
                }
            }
        }
    }

    private Set<BoundaryInputKey> findExternallyFedInputs(Map<String, NodeData> scopeNodes,
                                                           Set<String> selected) {
        Set<BoundaryInputKey> inputs = new HashSet<>();
        for (NodeData source : scopeNodes.values()) {
            if (source == null || selected.contains(source.id) || source.outputs == null) continue;
            for (List<Connection> links : source.outputs.values()) {
                if (links == null) continue;
                for (Connection link : links) {
                    if (link != null && selected.contains(link.targetNodeId())) {
                        inputs.add(new BoundaryInputKey(link.targetNodeId(), link.targetPortName()));
                    }
                }
            }
        }
        return inputs;
    }

    private boolean isDataPassthroughOutput(NodeData node, String portId) {
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (definition == null) return false;
        for (PortRow row : definition.rows()) {
            if (row.dataPassthrough() && row.rightPort().id().equals(portId)) return true;
        }
        return false;
    }

    private void rewriteFlowConnections(Map<String, NodeData> scopeNodes, Set<String> selected,
                                        NodeData groupNode, BoundaryPorts boundaryPorts) {
        for (NodeData node : scopeNodes.values()) {
            if (node == null || node.execOutputs == null) continue;

            Map<String, Connection> originalExecOutputs = node.execOutputs;
            node.execOutputs = new LinkedHashMap<>();

            boolean sourceInside = selected.contains(node.id);
            for (Map.Entry<String, Connection> outputEntry : originalExecOutputs.entrySet()) {
                String outPortId = outputEntry.getKey();
                Connection link = outputEntry.getValue();
                if (link == null) continue;

                boolean targetInside = selected.contains(link.targetNodeId());
                if (sourceInside && targetInside) {
                    node.addExecutionConnection(outPortId, link.targetNodeId(), link.targetPortName());
                } else if (!sourceInside && targetInside) {
                    BoundaryInputKey key = new BoundaryInputKey(link.targetNodeId(), link.targetPortName());
                    String flowInputPort = boundaryPorts.flowInputs.computeIfAbsent(
                            key,
                            ignored -> createFlowInputPort(groupNode, key.targetNodeId(), key.targetPortId(), scopeNodes)
                    );
                    node.addExecutionConnection(outPortId, groupNode.id, flowInputPort);
                    NodeData groupIn = groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID);
                    groupIn.addExecutionConnection(flowInputPort, link.targetNodeId(), link.targetPortName());
                } else if (sourceInside) {
                    BoundaryOutputKey key = new BoundaryOutputKey(node.id, outPortId);
                    String groupOutputPort = boundaryPorts.flowOutputs.computeIfAbsent(
                            key,
                            ignored -> createFlowOutputPort(groupNode, key.sourceNodeId(), key.sourcePortId(), scopeNodes)
                    );
                    node.addExecutionConnection(outPortId, GroupNodeTypes.GROUP_OUT_ID, groupOutputPort);
                    groupNode.addExecutionConnection(groupOutputPort, link.targetNodeId(), link.targetPortName());
                } else {
                    node.addExecutionConnection(outPortId, link.targetNodeId(), link.targetPortName());
                }
            }
        }
    }

    private void assignBoundaryPortOrders(Map<String, NodeData> scopeNodes, NodeData groupNode,
                                          BoundaryPorts ports) {
        int inputOrder = assignInputOrders(scopeNodes, groupNode, ports.flowInputs,
                GroupNodeTypes.CATEGORY_EXEC_INPUTS, 0);
        assignInputOrders(scopeNodes, groupNode, ports.dataInputs,
                GroupNodeTypes.CATEGORY_INPUTS, inputOrder);

        int outputOrder = assignOutputOrders(scopeNodes, groupNode, ports.flowOutputs,
                GroupNodeTypes.CATEGORY_EXEC_OUTPUTS, 0);
        assignOutputOrders(scopeNodes, groupNode, ports.dataOutputs,
                GroupNodeTypes.CATEGORY_OUTPUTS, outputOrder);
    }

    private int assignInputOrders(Map<String, NodeData> scopeNodes, NodeData groupNode,
                                  Map<BoundaryInputKey, String> ports, String category, int firstOrder) {
        List<Map.Entry<BoundaryInputKey, String>> ordered = new ArrayList<>(ports.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<BoundaryInputKey, String> entry) -> selectedNodeOrder(entry.getKey().targetNodeId()))
                .thenComparingInt(entry -> portOrder(scopeNodes, groupNode,
                        entry.getKey().targetNodeId(), entry.getKey().targetPortId(), true))
                .thenComparing(entry -> entry.getKey().targetPortId()));
        int order = firstOrder;
        for (Map.Entry<BoundaryInputKey, String> entry : ordered) {
            NodeData.PortConfig config = GroupNodeFactory.getPortConfig(groupNode, category, entry.getValue());
            if (config != null) config.order = order++;
        }
        return order;
    }

    private int assignOutputOrders(Map<String, NodeData> scopeNodes, NodeData groupNode,
                                   Map<BoundaryOutputKey, String> ports, String category, int firstOrder) {
        List<Map.Entry<BoundaryOutputKey, String>> ordered = new ArrayList<>(ports.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<BoundaryOutputKey, String> entry) -> selectedNodeOrder(entry.getKey().sourceNodeId()))
                .thenComparingInt(entry -> portOrder(scopeNodes, groupNode,
                        entry.getKey().sourceNodeId(), entry.getKey().sourcePortId(), false))
                .thenComparing(entry -> entry.getKey().sourcePortId()));
        int order = firstOrder;
        for (Map.Entry<BoundaryOutputKey, String> entry : ordered) {
            NodeData.PortConfig config = GroupNodeFactory.getPortConfig(groupNode, category, entry.getValue());
            if (config != null) config.order = order++;
        }
        return order;
    }

    private int selectedNodeOrder(String nodeId) {
        int index = mSelectedNodeIds.indexOf(nodeId);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private int portOrder(Map<String, NodeData> scopeNodes, NodeData groupNode,
                          String nodeId, String portId, boolean inputSide) {
        NodeData node = scopeNodes.get(nodeId);
        if (node == null && groupNode.subNodes != null) node = groupNode.subNodes.get(nodeId);
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (definition == null) return Integer.MAX_VALUE;
        for (int index = 0; index < definition.rows().size(); index++) {
            PortDef port = inputSide
                    ? definition.rows().get(index).leftPort()
                    : definition.rows().get(index).rightPort();
            if (port != null && port.id().equals(portId)) return index;
        }
        return Integer.MAX_VALUE;
    }

    private String createDataInputPort(NodeData groupNode, String targetNodeId, String targetPortId, Map<String, NodeData> scopeNodes) {
        PortDef port = findPort(scopeNodes, groupNode, targetNodeId, targetPortId, true);
        PortType type = dataPortType(port);
        String label = port != null ? port.displayName().getString() : targetPortId;
        return GroupNodeFactory.addPort(groupNode, GroupNodeTypes.CATEGORY_INPUTS, targetPortId, type, label);
    }

    private String createDataOutputPort(NodeData groupNode, String sourceNodeId, String sourcePortId, Map<String, NodeData> scopeNodes) {
        PortDef port = findPort(scopeNodes, groupNode, sourceNodeId, sourcePortId, false);
        PortType type = dataPortType(port);
        String label = port != null ? port.displayName().getString() : sourcePortId;
        return GroupNodeFactory.addPort(groupNode, GroupNodeTypes.CATEGORY_OUTPUTS, sourcePortId, type, label);
    }

    private String createFlowInputPort(NodeData groupNode, String targetNodeId, String targetPortId, Map<String, NodeData> scopeNodes) {
        PortDef port = findPort(scopeNodes, groupNode, targetNodeId, targetPortId, true);
        String label = port != null ? port.displayName().getString() : targetPortId;
        return GroupNodeFactory.addPort(groupNode, GroupNodeTypes.CATEGORY_EXEC_INPUTS,
                targetPortId, flowPortType(port), label);
    }

    private String createFlowOutputPort(NodeData groupNode, String sourceNodeId, String sourcePortId, Map<String, NodeData> scopeNodes) {
        PortDef port = findPort(scopeNodes, groupNode, sourceNodeId, sourcePortId, false);
        String label = port != null ? port.displayName().getString() : sourcePortId;
        return GroupNodeFactory.addPort(groupNode, GroupNodeTypes.CATEGORY_EXEC_OUTPUTS,
                sourcePortId, flowPortType(port), label);
    }

    private PortType flowPortType(PortDef port) {
        return port != null && port.type() != null && port.type().isFlow()
                ? port.type()
                : PortType.EXECUTION;
    }

    private PortType dataPortType(PortDef port) {
        if (port == null || port.type() == null || port.type().isFlow()) {
            return PortType.ANY;
        }
        return port.type();
    }

    private PortDef findPort(Map<String, NodeData> scopeNodes, NodeData groupNode, String nodeId, String portId, boolean inputSide) {
        NodeData node = scopeNodes.get(nodeId);
        if (node == null && groupNode.subNodes != null) {
            node = groupNode.subNodes.get(nodeId);
        }
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (def == null) return null;

        for (PortRow row : def.rows()) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null && port.id().equals(portId)) {
                return port;
            }
        }
        return null;
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

    private record BoundaryInputKey(String targetNodeId, String targetPortId) {}
    private record BoundaryOutputKey(String sourceNodeId, String sourcePortId) {}

    private static final class BoundaryPorts {
        private final Map<BoundaryInputKey, String> flowInputs = new LinkedHashMap<>();
        private final Map<BoundaryInputKey, String> dataInputs = new LinkedHashMap<>();
        private final Map<BoundaryOutputKey, String> flowOutputs = new LinkedHashMap<>();
        private final Map<BoundaryOutputKey, String> dataOutputs = new LinkedHashMap<>();
    }
}
