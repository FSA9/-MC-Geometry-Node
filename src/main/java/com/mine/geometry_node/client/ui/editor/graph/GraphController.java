package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.editor.graph.frame.FrameBoundsCalculator;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.behavior.entity.BehaviorMoveToNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphController {
    private final EditorContext mContext;

    public enum ConnectionChannel { DATA, EXECUTION }

    public record ScopedConnectionSnapshot(
            boolean external,
            ConnectionChannel channel,
            String outNodeId,
            String outPortId,
            String inNodeId,
            String inPortId
    ) {}

    public GraphController(EditorContext context) {
        this.mContext = context;
    }

    public EditorContext getContext() {
        return mContext;
    }

    public NodeGraph getCurrentGraph() {
        return mContext.getCurrentGraph();
    }

    public void setGraphMetadata(String graphTypeId, String comment, List<String> tags,
                                 QuestDefinition questDefinition) {
        NodeGraph graph = mContext.getGraph();
        graph.graphKind = graphTypeId;
        graph.comment = comment != null ? comment : "";
        graph.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        graph.quest = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
        mContext.notifyGraphMetadataChanged();
    }

    public void addNode(NodeData node) {
        if (mContext.isInsideGroupScope() && mContext.getCurrentGroupNode() != null) {
            mContext.getCurrentGroupNode().attachSubNode(node.id, node);
        } else {
            mContext.getCurrentGraph().addNode(node);
        }
        mContext.notifyNodeAdded(node);
    }

    public boolean canAddNode(NodeData node) {
        return node != null && node.id != null && node.type != null
                && NodeRegistry.INSTANCE.get(node.type) != null;
    }

    public void removeNode(String nodeId) {
        mContext.getCurrentGraph().removeNode(nodeId);
        mContext.notifyNodeRemoved(nodeId);
    }

    public float[] getNodePosition(String nodeId) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        return node != null ? node.uiPos : null;
    }

    public void setNodePosition(String nodeId, float x, float y) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node != null) {
            node.setPosition(x, y);
            mContext.notifyNodeMoved(nodeId, x, y);

            if (node.parentFrame != null) {
                updateFrameBounds(node.parentFrame);
            }
        }
    }

    public void setGroupNodeProperty(String nodeId, String customName, Integer customColor, String comment) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node == null || !node.isGroupNode()) return;

        node.customName = customName != null && !customName.trim().isEmpty() ? customName.trim() : null;
        int normalizedColor = customColor != null ? (customColor | 0xFF000000) : NodeData.DEFAULT_GROUP_COLOR;
        node.customColor = normalizedColor != NodeData.DEFAULT_GROUP_COLOR ? normalizedColor : null;
        node.comment = comment != null && !comment.trim().isEmpty() ? comment.trim() : null;
        mContext.notifyNodeStructureChanged(node);
    }

    public void addConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        if (!canConnectPorts(outNodeId, outPortId, inNodeId, inPortId)) return;

        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.inputs.remove(inPortId);
                inNode.setInputConnected(inPortId, true);
            }

            updateVirtualGroupPortTypeAfterDataConnection(outNodeId, outPortId, inNodeId, inPortId);
            refreshRerouteTypes(outNodeId, inNodeId);
            mContext.notifyConnectionAdded(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void removeConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.removeDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.setInputConnected(inPortId, false);
            }

            refreshVirtualGroupPortTypeAfterRemoval(outNodeId, outPortId, inNodeId, inPortId);
            refreshRerouteTypes(outNodeId, inNodeId);
            mContext.notifyConnectionRemoved(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void addExecutionConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        if (!canConnectPorts(outNodeId, outPortId, inNodeId, inPortId)) return;

        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addExecutionConnection(outPortId, inNodeId, inPortId);
            updateVirtualGroupPortTypeAfterExecutionConnection(outNodeId, outPortId, inNodeId, inPortId);
            refreshRerouteTypes(outNodeId, inNodeId);
            mContext.notifyExecutionConnectionAdded(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void removeExecutionConnection(String outNodeId, String outPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        if (outNode != null) {
            Connection c = outNode.execOutputs.get(outPortId);
            if (c != null) {
                outNode.removeExecutionConnection(outPortId);
                refreshVirtualGroupPortTypeAfterRemoval(outNodeId, outPortId, c.targetNodeId(), c.targetPortName());
                refreshRerouteTypes(outNodeId, c.targetNodeId());
                mContext.notifyExecutionConnectionRemoved(outNodeId, outPortId, c.targetNodeId(), c.targetPortName());
            }
        }
    }

    public void setNodeInputValue(String nodeId, String portId, Object value) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node == null) return;

        // 1. 更新节点输入值 (不论是物理连线端口还是纯属性端口，统统存这里)
        if (value == null) {
            node.inputs.remove(portId);
        } else {
            node.inputs.put(portId, value);
        }
        if (BehaviorMoveToNode.TYPE_ID.equals(node.type)
                && StandardPorts.TARGET_MODE.getId().equals(portId)) {
            String inactivePort = BehaviorMoveToNode.TARGET_MODE_POSITION.equals(value)
                    ? StandardPorts.TARGET_ENTITY.getId() : StandardPorts.TARGET_POSITION.getId();
            node.inputs.remove(inactivePort);
        }

        // 2. 获取更新属性后的新 NodeDef (防止这个输入值是动态分支数量控制参数)
        NodeDef newDef = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (newDef == null) return;

        // 3. 提取新定义中所有【仍然合法】的端口 ID
        Set<String> validInputs = new HashSet<>();
        Set<String> validOutputs = new HashSet<>();
        for (PortRow row : newDef.rows()) {
            if (row.leftPort() != null) validInputs.add(row.leftPort().id());
            if (row.customWidgetId() != null && !row.customWidgetId().isBlank()) validInputs.add(row.customWidgetId());
            if (row.rightPort() != null) validOutputs.add(row.rightPort().id());
        }

        // 4. 清理当前节点失效的【输出】连线
        List<String> invalidOutPorts = new ArrayList<>();
        for (String outPort : node.outputs.keySet()) {
            if (!validOutputs.contains(outPort)) {
                invalidOutPorts.add(outPort);
            }
        }
        for (String invalidOut : invalidOutPorts) {
            List<Connection> links = new ArrayList<>(node.getConnections(invalidOut));
            for (Connection link : links) {
                removeConnection(nodeId, invalidOut, link.targetNodeId(), link.targetPortName());
            }
        }
        // 5. 清理当前节点失效的【执行流】连线
        List<String> invalidExecPorts = new ArrayList<>();
        for (String execPort : node.execOutputs.keySet()) {
            if (!validOutputs.contains(execPort)) {
                invalidExecPorts.add(execPort);
            }
        }
        for (String invalidExec : invalidExecPorts) {
            removeExecutionConnection(nodeId, invalidExec);
        }

        // 6. 清理失效的端口自定义配置
        if (!node.isGroupNode()) {
            ensurePortConfig(node);
            removeInvalidPortConfig(node.portConfig.inputs, validInputs);
            removeInvalidPortConfig(node.portConfig.execInputs, validInputs);
            removeInvalidPortConfig(node.portConfig.outputs, validOutputs);
            removeInvalidPortConfig(node.portConfig.execOutputs, validOutputs);
        }

        // 7. 清理其他节点连接到当前节点失效【输入】端口的连线
        for (NodeData otherNode : mContext.getCurrentGraph().nodes.values()) {
            for (String otherOutPort : new ArrayList<>(otherNode.outputs.keySet())) {
                List<Connection> links = new ArrayList<>(otherNode.getConnections(otherOutPort));
                for (Connection link : links) {
                    if (link.targetNodeId().equals(nodeId) && !validInputs.contains(link.targetPortName())) {
                        removeConnection(otherNode.id, otherOutPort, nodeId, link.targetPortName());
                    }
                }
            }
            for (String otherExecPort : new ArrayList<>(otherNode.execOutputs.keySet())) {
                Connection link = otherNode.execOutputs.get(otherExecPort);
                if (link != null && link.targetNodeId().equals(nodeId)
                        && !validInputs.contains(link.targetPortName())) {
                    removeExecutionConnection(otherNode.id, otherExecPort);
                }
            }
        }

        // 8. 通知 viewport 重新构建该节点的 UI
        mContext.notifyNodeStructureChanged(node);
    }

    public boolean isInputPortConnected(String targetNodeId, String targetPortId) {
        NodeData node = mContext.getCurrentGraph().getNode(targetNodeId);
        if (node == null) return false;
        return node.connectedInputs.contains(targetPortId);
    }

    public String addGroupVirtualPort(String boundaryNodeId) {
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        if (!GroupNodeFactory.isBoundaryNode(boundaryNode)) return null;

        String portId = GroupNodeFactory.addVirtualPort(boundaryNode);
        notifyGroupBoundaryPortStructureChanged(boundaryNode);
        return portId;
    }

    public void restoreGroupVirtualPort(String boundaryNodeId, String category, String portId, NodeData.PortConfig config) {
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        if (groupNode == null || category == null || portId == null || config == null) return;

        GroupNodeFactory.restorePort(groupNode, category, portId, config);
        notifyGroupBoundaryPortStructureChanged(boundaryNode);
    }

    public NodeData.PortConfig getGroupVirtualPortConfig(String boundaryNodeId, String portId) {
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        String category = GroupNodeFactory.findBoundaryPortCategory(boundaryNode, portId);
        NodeData.PortConfig config = GroupNodeFactory.getPortConfig(groupNode, category, portId);
        return copyPortConfig(config);
    }

    public String getGroupVirtualPortCategory(String boundaryNodeId, String portId) {
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        return GroupNodeFactory.findBoundaryPortCategory(boundaryNode, portId);
    }

    public void removeGroupVirtualPort(String boundaryNodeId, String portId) {
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        String category = GroupNodeFactory.findBoundaryPortCategory(boundaryNode, portId);
        if (groupNode == null || category == null) return;

        NodeData.PortConfig removedConfig = GroupNodeFactory.getPortConfig(groupNode, category, portId);
        Integer removedOrder = removedConfig != null ? removedConfig.order : null;
        boolean inputSide = GroupNodeFactory.isInputSide(category);

        removeConnectionsForPort(boundaryNodeId, portId, isBoundaryVisualInput(boundaryNode));
        removeExternalConnectionsForGroupPort(groupNode, portId);

        String currentCategory = GroupNodeFactory.findPortCategory(groupNode, portId);
        if (currentCategory != null) {
            GroupNodeFactory.removePort(groupNode, currentCategory, portId);
        } else {
            GroupNodeFactory.removePort(groupNode, category, portId);
        }
        if (removedOrder != null) {
            GroupNodeFactory.compactOrdersAfterRemoval(groupNode, inputSide, removedOrder);
        }
        notifyGroupBoundaryPortStructureChanged(boundaryNode);
    }

    public List<ScopedConnectionSnapshot> getGroupVirtualPortConnectionSnapshots(String boundaryNodeId, String portId) {
        List<ScopedConnectionSnapshot> snapshots = new ArrayList<>();
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        collectConnectionsForNodePort(snapshots, mContext.getCurrentGraph().nodes, boundaryNodeId, portId, false);

        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        Map<String, NodeData> externalScope = getContainingScopeNodes(groupNode);
        if (groupNode != null && externalScope != null && externalScope != mContext.getCurrentGraph().nodes) {
            collectConnectionsForNodePort(snapshots, externalScope, groupNode.id, portId, true);
        }
        return snapshots;
    }

    public void restoreGroupVirtualPortConnection(String boundaryNodeId, ScopedConnectionSnapshot snapshot) {
        if (snapshot == null) return;
        if (!snapshot.external()) {
            addConnectionInScope(mContext.getCurrentGraph().nodes, snapshot);
            if (snapshot.channel() == ConnectionChannel.EXECUTION) {
                mContext.notifyExecutionConnectionAdded(snapshot.outNodeId(), snapshot.outPortId(), snapshot.inNodeId(), snapshot.inPortId());
            } else {
                mContext.notifyConnectionAdded(snapshot.outNodeId(), snapshot.outPortId(), snapshot.inNodeId(), snapshot.inPortId());
            }
            return;
        }

        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        addConnectionInScope(getContainingScopeNodes(groupNode), snapshot);
    }

    public List<ScopedConnectionSnapshot> getBoundaryPortExternalConnectionSnapshots(String boundaryNodeId, String portId) {
        List<ScopedConnectionSnapshot> snapshots = new ArrayList<>();
        NodeData boundaryNode = mContext.getCurrentGraph().getNode(boundaryNodeId);
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        Map<String, NodeData> externalScope = getContainingScopeNodes(groupNode);
        if (groupNode != null && externalScope != null) {
            collectConnectionsForNodePort(snapshots, externalScope, groupNode.id, portId, true);
        }
        return snapshots;
    }

    public boolean canConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);
        if (outNode == null || inNode == null || outNode.id.equals(inNode.id)) return false;
        if (!isExternalGroupPortConnectable(outNode, outPortId) || !isExternalGroupPortConnectable(inNode, inPortId)) {
            return false;
        }

        PortType outType = getResolvedPortType(outNode, outPortId, false);
        PortType inType = getResolvedPortType(inNode, inPortId, true);
        if (isFlowToVirtualAny(outNode, outType, inNode, inType)) return true;
        if (isFlowToUntypedReroute(outNode, outType, inNode, inType)) return true;
        return PortType.isCompatible(outType, inType);
    }

    public boolean hasConnection(String outNodeId, String outPortId,
                                 String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        if (outNode == null) return false;
        PortType outType = getResolvedPortType(outNodeId, outPortId, false);
        PortType inType = getResolvedPortType(inNodeId, inPortId, true);
        if ((outType != null && outType.isFlow()) || (inType != null && inType.isFlow())) {
            Connection connection = outNode.execOutputs.get(outPortId);
            return connection != null
                    && inNodeId.equals(connection.targetNodeId())
                    && inPortId.equals(connection.targetPortName());
        }
        return outNode.getConnections(outPortId).stream().anyMatch(connection ->
                inNodeId.equals(connection.targetNodeId())
                        && inPortId.equals(connection.targetPortName()));
    }

    public void removeDynamicBranch(String nodeId, String propertyKey, int removeIndex, int totalCount) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node == null) return;

        if (removeIndex < 1 || removeIndex > totalCount) return;
        ensurePortConfig(node);

        for (int i = removeIndex; i < totalCount; i++) {
            String oldSuffix = "_" + (i + 1);
            String newSuffix = "_" + i;
            shiftMapData(node.inputs, oldSuffix, newSuffix);
            shiftMapData(node.outputs, oldSuffix, newSuffix);
            shiftMapData(node.execOutputs, oldSuffix, newSuffix);
            shiftPortConfig(node.portConfig, oldSuffix, newSuffix);
            shiftConnections(nodeId, oldSuffix, newSuffix);
        }

        String lastSuffix = "_" + totalCount;
        node.inputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.outputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.execOutputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        removePortConfigSuffix(node.portConfig, lastSuffix);
        shiftConnections(nodeId, lastSuffix, null);

        setNodeInputValue(nodeId, propertyKey, totalCount - 1);
    }

    private <V> void shiftMapData(java.util.Map<String, V> map, String oldSuffix, String newSuffix) {
        map.keySet().removeIf(k -> k.endsWith(newSuffix));

        Map<String, V> toMove = new java.util.HashMap<>();
        for (Map.Entry<String, V> entry : map.entrySet()) {
            if (entry.getKey().endsWith(oldSuffix)) {
                String newKey = entry.getKey().substring(0, entry.getKey().length() - oldSuffix.length()) + newSuffix;
                toMove.put(newKey, entry.getValue());
            }
        }

        map.keySet().removeIf(k -> k.endsWith(oldSuffix));
        map.putAll(toMove);
    }

    private void shiftPortConfig(NodeData.PortsConfig settings, String oldSuffix, String newSuffix) {
        if (settings == null) return;
        shiftMapData(settings.inputs, oldSuffix, newSuffix);
        shiftMapData(settings.execInputs, oldSuffix, newSuffix);
        shiftMapData(settings.outputs, oldSuffix, newSuffix);
        shiftMapData(settings.execOutputs, oldSuffix, newSuffix);
    }

    private void removePortConfigSuffix(NodeData.PortsConfig settings, String suffix) {
        if (settings == null) return;
        settings.inputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.execInputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.outputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.execOutputs.keySet().removeIf(k -> k.endsWith(suffix));
    }

    private void removeInvalidPortConfig(Map<String, NodeData.PortConfig> settings, Set<String> validPorts) {
        if (settings == null) return;
        settings.keySet().removeIf(portId -> !validPorts.contains(portId));
    }

    private void ensurePortConfig(NodeData node) {
        node.ensurePortConfig();
    }

    private void shiftConnections(String targetNodeId, String oldSuffix, String newSuffix) {
        List<Runnable> connectionUpdates = new ArrayList<>();

        for (NodeData otherNode : mContext.getCurrentGraph().nodes.values()) {
            for (String outPort : otherNode.outputs.keySet()) {
                for (Connection link : otherNode.getConnections(outPort)) {
                    if (link.targetNodeId().equals(targetNodeId) && link.targetPortName().endsWith(oldSuffix)) {
                        connectionUpdates.add(() -> {
                            removeConnection(otherNode.id, outPort, targetNodeId, link.targetPortName());
                            if (newSuffix != null) {
                                String newPortName = link.targetPortName().substring(0, link.targetPortName().length() - oldSuffix.length()) + newSuffix;
                                addConnection(otherNode.id, outPort, targetNodeId, newPortName);
                            }
                        });
                    }
                }
            }
            for (Map.Entry<String, Connection> entry : otherNode.execOutputs.entrySet()) {
                String outPort = entry.getKey();
                Connection link = entry.getValue();
                if (link.targetNodeId().equals(targetNodeId) && link.targetPortName().endsWith(oldSuffix)) {
                    connectionUpdates.add(() -> {
                        removeExecutionConnection(otherNode.id, outPort);
                        if (newSuffix != null) {
                            String newPortName = link.targetPortName().substring(
                                    0, link.targetPortName().length() - oldSuffix.length()) + newSuffix;
                            addExecutionConnection(otherNode.id, outPort, targetNodeId, newPortName);
                        }
                    });
                }
            }
        }

        for (Runnable r : connectionUpdates) {
            r.run();
        }
    }

    public PortType getResolvedPortType(String nodeId, String portId, boolean inputSide) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        return getResolvedPortType(node, portId, inputSide);
    }

    private PortType getResolvedPortType(NodeData node, String portId, boolean inputSide) {
        return RerouteNodeSupport.resolvePortType(mContext.getCurrentGraph().nodes, node, portId, inputSide);
    }

    private String getResolvedPortDisplayName(NodeData node, String portId, boolean inputSide) {
        if (node == null || portId == null) return "";
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (def == null) return "";
        for (PortRow row : def.rows()) {
            if (inputSide && row.leftPort() != null && row.leftPort().id().equals(portId)) {
                String category = row.leftPort().type().isFlow() ? "exec_inputs" : "inputs";
                return node.getEffectivePortName(category, portId, row.leftPort().displayName().getString());
            }
            if (!inputSide && row.rightPort() != null && row.rightPort().id().equals(portId)) {
                String category = row.rightPort().type().isFlow() ? "exec_outputs" : "outputs";
                return node.getEffectivePortName(category, portId, row.rightPort().displayName().getString());
            }
        }
        return "";
    }

    private void updateVirtualGroupPortTypeAfterDataConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);

        if (outNode != null && outNode.isGroupInputNode()) {
            PortType type = getResolvedPortType(inNode, inPortId, true);
            String name = getResolvedPortDisplayName(inNode, inPortId, true);
            setVirtualGroupPortBinding(outNode, outPortId, type, name);
        }
        if (inNode != null && inNode.isGroupOutputNode()) {
            PortType type = getResolvedPortType(outNode, outPortId, false);
            String name = getResolvedPortDisplayName(outNode, outPortId, false);
            setVirtualGroupPortBinding(inNode, inPortId, type, name);
        }
    }

    private void updateVirtualGroupPortTypeAfterExecutionConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);

        if (outNode != null && outNode.isGroupInputNode()) {
            String name = getResolvedPortDisplayName(inNode, inPortId, true);
            setVirtualGroupPortBinding(outNode, outPortId,
                    getResolvedPortType(inNode, inPortId, true), name);
        }
        if (inNode != null && inNode.isGroupOutputNode()) {
            String name = getResolvedPortDisplayName(outNode, outPortId, false);
            setVirtualGroupPortBinding(inNode, inPortId,
                    getResolvedPortType(outNode, outPortId, false), name);
        }
    }

    private void refreshVirtualGroupPortTypeAfterRemoval(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getCurrentGraph().getNode(outNodeId);
        NodeData inNode = mContext.getCurrentGraph().getNode(inNodeId);

        if (outNode != null && outNode.isGroupInputNode()) {
            refreshGroupInputPortType(outNode, outPortId);
        }
        if (inNode != null && inNode.isGroupOutputNode()) {
            refreshGroupOutputPortType(inNode, inPortId);
        }
    }

    private void refreshGroupInputPortType(NodeData groupInputNode, String portId) {
        PortType inferred = null;
        String inferredName = "";
        if (groupInputNode.outputs != null) {
            for (Connection link : groupInputNode.getConnections(portId)) {
                NodeData targetNode = mContext.getCurrentGraph().getNode(link.targetNodeId());
                inferred = getResolvedPortType(targetNode, link.targetPortName(), true);
                inferredName = getResolvedPortDisplayName(targetNode, link.targetPortName(), true);
                if (inferred != null) break;
            }
        }
        if (groupInputNode.execOutputs != null && groupInputNode.execOutputs.containsKey(portId)) {
            Connection link = groupInputNode.execOutputs.get(portId);
            NodeData targetNode = link != null ? mContext.getCurrentGraph().getNode(link.targetNodeId()) : null;
            inferred = link != null ? getResolvedPortType(targetNode, link.targetPortName(), true) : null;
            inferredName = link != null ? getResolvedPortDisplayName(targetNode, link.targetPortName(), true) : "";
        }
        setVirtualGroupPortBinding(groupInputNode, portId, inferred != null ? inferred : PortType.ANY, inferred != null ? inferredName : "");
        if (inferred == null) {
            removeExternalConnectionsForBoundaryPort(groupInputNode, portId);
        }
    }

    private void refreshGroupOutputPortType(NodeData groupOutputNode, String portId) {
        PortType inferred = null;
        String inferredName = "";
        for (NodeData node : mContext.getCurrentGraph().nodes.values()) {
            if (node == null || node.id.equals(groupOutputNode.id)) continue;
            if (node.outputs != null) {
                for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                    if (entry.getValue() == null) continue;
                    for (Connection link : new ArrayList<>(entry.getValue())) {
                        if (groupOutputNode.id.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                            inferred = getResolvedPortType(node, entry.getKey(), false);
                            inferredName = getResolvedPortDisplayName(node, entry.getKey(), false);
                            break;
                        }
                    }
                    if (inferred != null) break;
                }
            }
            if (inferred == null && node.execOutputs != null) {
                for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                    Connection link = entry.getValue();
                    if (link != null && groupOutputNode.id.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                        inferred = getResolvedPortType(node, entry.getKey(), false);
                        inferredName = getResolvedPortDisplayName(node, entry.getKey(), false);
                        break;
                    }
                }
            }
            if (inferred != null) break;
        }
        setVirtualGroupPortBinding(groupOutputNode, portId, inferred != null ? inferred : PortType.ANY, inferred != null ? inferredName : "");
        if (inferred == null) {
            removeExternalConnectionsForBoundaryPort(groupOutputNode, portId);
        }
    }

    private boolean isFlowToVirtualAny(NodeData outNode, PortType outType, NodeData inNode, PortType inType) {
        return (outType != null && outType.isFlow() && inType == PortType.ANY && isInsideBoundaryNode(inNode))
                || (inType != null && inType.isFlow() && outType == PortType.ANY && isInsideBoundaryNode(outNode));
    }

    private boolean isFlowToUntypedReroute(NodeData outNode, PortType outType, NodeData inNode, PortType inType) {
        return outType != null && outType.isFlow() && inType == PortType.ANY && RerouteNodeSupport.isReroute(inNode)
                || inType != null && inType.isFlow() && outType == PortType.ANY && RerouteNodeSupport.isReroute(outNode);
    }

    private void refreshRerouteTypes(String outNodeId, String inNodeId) {
        for (NodeData changed : RerouteNodeSupport.refreshLockedTypes(mContext.getCurrentGraph().nodes)) {
            mContext.notifyNodeStructureChanged(changed);
        }
    }

    private boolean isInsideBoundaryNode(NodeData node) {
        return node != null && (node.isGroupInputNode() || node.isGroupOutputNode());
    }

    private boolean isExternalGroupPortConnectable(NodeData node, String portId) {
        if (node == null || !node.isGroupNode() || portId == null) return true;

        String category = GroupNodeFactory.findPortCategory(node, portId);
        NodeData.PortConfig config = GroupNodeFactory.getPortConfig(node, category, portId);
        return config != null && config.type != null && isGroupPortInternallyBound(node, category, portId);
    }

    private boolean isGroupPortInternallyBound(NodeData groupNode, String category, String portId) {
        if (groupNode == null || category == null || portId == null || groupNode.subNodes == null) return false;

        if (GroupNodeFactory.isInputSide(category)) {
            NodeData groupInputNode = groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID);
            return groupInputNode != null
                    && (!groupInputNode.getConnections(portId).isEmpty()
                    || groupInputNode.execOutputs != null && groupInputNode.execOutputs.containsKey(portId));
        }

        NodeData groupOutputNode = groupNode.subNodes.get(GroupNodeTypes.GROUP_OUT_ID);
        if (groupOutputNode == null) return false;
        for (NodeData innerNode : groupNode.subNodes.values()) {
            if (innerNode == null) continue;
            if (innerNode.outputs != null) {
                for (List<Connection> links : innerNode.outputs.values()) {
                    if (links == null) continue;
                    for (Connection link : links) {
                        if (link != null && groupOutputNode.id.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                            return true;
                        }
                    }
                }
            }
            if (innerNode.execOutputs != null) {
                for (Connection link : innerNode.execOutputs.values()) {
                    if (link != null && groupOutputNode.id.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setVirtualGroupPortBinding(NodeData boundaryNode, String portId, PortType type, String customName) {
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        String category = GroupNodeFactory.findBoundaryPortCategory(boundaryNode, portId);
        if (groupNode == null || category == null || type == null) return;

        if (customName == null) {
            GroupNodeFactory.setPortType(groupNode, category, portId, type);
        } else {
            GroupNodeFactory.setPortBinding(groupNode, category, portId, type, customName);
        }
        removeIncompatibleExternalConnectionsForBoundaryPort(boundaryNode, portId);
        notifyGroupBoundaryPortStructureChanged(boundaryNode);
    }

    private void removeConnectionsForPort(String nodeId, String portId, boolean inputSide) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node == null) return;

        if (!inputSide) {
            for (Connection link : new ArrayList<>(node.getConnections(portId))) {
                removeConnection(nodeId, portId, link.targetNodeId(), link.targetPortName());
            }
            if (node.execOutputs != null && node.execOutputs.containsKey(portId)) {
                removeExecutionConnection(nodeId, portId);
            }
        }

        for (NodeData otherNode : mContext.getCurrentGraph().nodes.values()) {
            if (otherNode == null) continue;
            if (otherNode.outputs != null) {
                for (String outPort : new ArrayList<>(otherNode.outputs.keySet())) {
                    for (Connection link : new ArrayList<>(otherNode.getConnections(outPort))) {
                        if (nodeId.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                            removeConnection(otherNode.id, outPort, nodeId, portId);
                        }
                    }
                }
            }
            if (otherNode.execOutputs != null) {
                for (Map.Entry<String, Connection> entry : new ArrayList<>(otherNode.execOutputs.entrySet())) {
                    Connection link = entry.getValue();
                    if (link != null && nodeId.equals(link.targetNodeId()) && portId.equals(link.targetPortName())) {
                        removeExecutionConnection(otherNode.id, entry.getKey());
                    }
                }
            }
        }
    }

    private void removeExternalConnectionsForGroupPort(NodeData groupNode, String portId) {
        Map<String, NodeData> externalScope = getContainingScopeNodes(groupNode);
        if (externalScope == null) return;

        List<ScopedConnectionSnapshot> snapshots = new ArrayList<>();
        collectConnectionsForNodePort(snapshots, externalScope, groupNode.id, portId, true);
        for (ScopedConnectionSnapshot snapshot : snapshots) {
            removeConnectionInScope(externalScope, snapshot);
        }
        if (!snapshots.isEmpty()) {
            mContext.notifyGraphConnectionsRebuildRequested();
        }
    }

    private void removeExternalConnectionsForBoundaryPort(NodeData boundaryNode, String portId) {
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        if (groupNode == null) return;
        removeExternalConnectionsForGroupPort(groupNode, portId);
    }

    private void removeIncompatibleExternalConnectionsForBoundaryPort(NodeData boundaryNode, String portId) {
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        Map<String, NodeData> externalScope = getContainingScopeNodes(groupNode);
        if (groupNode == null || externalScope == null) return;

        List<ScopedConnectionSnapshot> snapshots = new ArrayList<>();
        collectConnectionsForNodePort(snapshots, externalScope, groupNode.id, portId, true);
        boolean changed = false;
        for (ScopedConnectionSnapshot snapshot : snapshots) {
            if (!isScopedConnectionValid(externalScope, snapshot)) {
                removeConnectionInScope(externalScope, snapshot);
                changed = true;
            }
        }
        if (changed) {
            mContext.notifyGraphConnectionsRebuildRequested();
        }
    }

    private boolean isScopedConnectionValid(Map<String, NodeData> scopeNodes, ScopedConnectionSnapshot snapshot) {
        NodeData outNode = scopeNodes.get(snapshot.outNodeId());
        NodeData inNode = scopeNodes.get(snapshot.inNodeId());
        if (outNode == null || inNode == null) return false;
        if (!isExternalGroupPortConnectable(outNode, snapshot.outPortId())
                || !isExternalGroupPortConnectable(inNode, snapshot.inPortId())) {
            return false;
        }

        PortType outType = getResolvedPortType(outNode, snapshot.outPortId(), false);
        PortType inType = getResolvedPortType(inNode, snapshot.inPortId(), true);
        return switch (snapshot.channel()) {
            case EXECUTION -> outType != null && inType != null
                    && outType.isFlow() && inType.isFlow()
                    && PortType.isCompatible(outType, inType);
            case DATA -> PortType.isCompatible(outType, inType);
        };
    }

    private void collectConnectionsForNodePort(
            List<ScopedConnectionSnapshot> snapshots,
            Map<String, NodeData> scopeNodes,
            String nodeId,
            String portId,
            boolean external
    ) {
        if (snapshots == null || scopeNodes == null || nodeId == null || portId == null) return;

        for (NodeData node : scopeNodes.values()) {
            if (node == null) continue;
            if (node.outputs != null) {
                for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                    if (entry.getValue() == null) continue;
                    for (Connection link : entry.getValue()) {
                        if (link == null) continue;
                        if (isConnectionForPort(node.id, entry.getKey(), link, nodeId, portId)) {
                            snapshots.add(new ScopedConnectionSnapshot(
                                    external,
                                    ConnectionChannel.DATA,
                                    node.id,
                                    entry.getKey(),
                                    link.targetNodeId(),
                                    link.targetPortName()
                            ));
                        }
                    }
                }
            }
            if (node.execOutputs != null) {
                for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                    Connection link = entry.getValue();
                    if (link == null) continue;
                    if (isConnectionForPort(node.id, entry.getKey(), link, nodeId, portId)) {
                        snapshots.add(new ScopedConnectionSnapshot(
                                external,
                                ConnectionChannel.EXECUTION,
                                node.id,
                                entry.getKey(),
                                link.targetNodeId(),
                                link.targetPortName()
                        ));
                    }
                }
            }
        }
    }

    private boolean isConnectionForPort(String outNodeId, String outPortId, Connection link, String nodeId, String portId) {
        return nodeId.equals(outNodeId) && portId.equals(outPortId)
                || nodeId.equals(link.targetNodeId()) && portId.equals(link.targetPortName());
    }

    private Map<String, NodeData> getContainingScopeNodes(NodeData groupNode) {
        if (groupNode == null) return null;
        NodeData parentGroup = groupNode.parentGroupNode;
        return parentGroup != null ? parentGroup.ensureSubNodes() : mContext.getGraph().nodes;
    }

    private void addConnectionInScope(Map<String, NodeData> scopeNodes, ScopedConnectionSnapshot snapshot) {
        if (scopeNodes == null || snapshot == null) return;
        NodeData outNode = scopeNodes.get(snapshot.outNodeId());
        if (outNode == null) return;

        if (snapshot.channel() == ConnectionChannel.EXECUTION) {
            outNode.addExecutionConnection(snapshot.outPortId(), snapshot.inNodeId(), snapshot.inPortId());
            return;
        }
        outNode.addDataConnection(snapshot.outPortId(), snapshot.inNodeId(), snapshot.inPortId());
        NodeData inNode = scopeNodes.get(snapshot.inNodeId());
        if (inNode != null) {
            inNode.inputs.remove(snapshot.inPortId());
            inNode.setInputConnected(snapshot.inPortId(), true);
        }
    }

    private void removeConnectionInScope(Map<String, NodeData> scopeNodes, ScopedConnectionSnapshot snapshot) {
        if (scopeNodes == null || snapshot == null) return;
        NodeData outNode = scopeNodes.get(snapshot.outNodeId());
        if (outNode == null) return;

        if (snapshot.channel() == ConnectionChannel.EXECUTION) {
            outNode.removeExecutionConnection(snapshot.outPortId());
            return;
        }
        outNode.removeDataConnection(snapshot.outPortId(), snapshot.inNodeId(), snapshot.inPortId());
        NodeData inNode = scopeNodes.get(snapshot.inNodeId());
        if (inNode != null) {
            inNode.setInputConnected(snapshot.inPortId(), false);
        }
    }

    private boolean isBoundaryVisualInput(NodeData boundaryNode) {
        return boundaryNode != null && boundaryNode.isGroupOutputNode();
    }

    private void notifyGroupBoundaryPortStructureChanged(NodeData boundaryNode) {
        NodeGraph currentGraph = mContext.getCurrentGraph();
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        if (boundaryNode != null && currentGraph.getNode(boundaryNode.id) == boundaryNode) {
            mContext.notifyNodeStructureChanged(boundaryNode);
        }
        if (groupNode != null && currentGraph.getNode(groupNode.id) == groupNode) {
            mContext.notifyNodeStructureChanged(groupNode);
        }
    }

    private NodeData.PortConfig copyPortConfig(NodeData.PortConfig source) {
        if (source == null) return null;
        NodeData.PortConfig copy = new NodeData.PortConfig();
        copy.customName = source.customName;
        copy.hidden = source.hidden;
        copy.type = source.type;
        copy.order = source.order;
        return copy;
    }

    // --- 新增：修改端口自定义名称 ---
    public void setPortCustomName(String nodeId, String category, String portId, String newName) {
        NodeData node = mContext.getCurrentGraph().getNode(nodeId);
        if (node == null) return;

        if (GroupNodeFactory.isBoundaryNode(node)) {
            setBoundaryPortCustomName(node, category, portId, newName);
            return;
        }

        ensurePortConfig(node);

        // 根据 category 获取对应的 Map
        Map<String, NodeData.PortConfig> targetMap = switch (category) {
            case "inputs" -> node.portConfig.inputs;
            case "exec_inputs" -> node.portConfig.execInputs;
            case "exec_outputs" -> node.portConfig.execOutputs;
            case "outputs" -> node.portConfig.outputs;
            default -> null;
        };

        if (targetMap != null) {
            NodeData.PortConfig config = targetMap.get(portId);
            if (config == null) {
                config = new NodeData.PortConfig();
                targetMap.put(portId, config);
            }

            config.customName = normalizeCustomName(newName);
            if (isEmptyPortConfig(config)) {
                targetMap.remove(portId);
            }

            // 通知重新构建节点结构，这会自动刷新 UI 上的文字并重新计算排版宽度
            mContext.notifyNodeStructureChanged(node);
        }
    }

    private void setBoundaryPortCustomName(NodeData boundaryNode, String visualCategory, String portId, String newName) {
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundaryNode);
        String category = GroupNodeFactory.mapBoundaryCategory(boundaryNode, visualCategory);
        if (category == null) {
            category = GroupNodeFactory.findBoundaryPortCategory(boundaryNode, portId);
        }
        if (groupNode == null || category == null) return;

        Map<String, NodeData.PortConfig> targetMap = groupNode.getPortConfigMap(category);
        NodeData.PortConfig config = targetMap.get(portId);
        if (config == null) {
            config = new NodeData.PortConfig();
            config.type = category.contains("exec") ? PortType.EXECUTION : PortType.ANY;
            config.order = GroupNodeFactory.nextFreeOrder(groupNode, GroupNodeFactory.isInputSide(category));
            targetMap.put(portId, config);
        }
        config.customName = normalizeCustomName(newName);
        notifyGroupBoundaryPortStructureChanged(boundaryNode);
    }

    private String normalizeCustomName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isEmptyPortConfig(NodeData.PortConfig config) {
        return config != null
                && config.customName == null
                && config.hidden == null
                && config.type == null
                && config.order == null;
    }

    public void addFrame(FrameData frame) {
        if (mContext.isInsideGroupScope()) return;
        mContext.getGraph().addFrame(frame);
        mContext.notifyFrameAdded(frame);
    }

    public void removeFrame(String frameId) {
        if (mContext.isInsideGroupScope()) return;
        mContext.getGraph().removeFrame(frameId);
        mContext.notifyFrameRemoved(frameId);
    }

    /**
     * Updates an element's parent frame and refreshes affected frame bounds.
     */
    public void setElementParentFrame(String elementId, boolean isNode, String newParentFrameId) {
        String oldParentId = null;
        if (isNode) {
            NodeData node = mContext.getCurrentGraph().getNode(elementId);
            if (node != null) {
                oldParentId = node.parentFrame;
                node.parentFrame = newParentFrameId;
            }
        } else {
            FrameData frame = mContext.getGraph().getFrame(elementId);
            if (frame != null) {
                oldParentId = frame.parentFrame;
                frame.parentFrame = newParentFrameId;
            }
        }

        // 重新计算受影响图框的边界
        if (oldParentId != null && !oldParentId.equals(newParentFrameId)) {
            updateFrameBounds(oldParentId);
        }
        if (newParentFrameId != null) {
            updateFrameBounds(newParentFrameId);
        }
    }

    /**
     * Recomputes a frame's auto bounds from committed graph data.
     */
    public void updateFrameBounds(String frameId) {
        updateFrameBounds(frameId, new HashSet<>());
    }

    private void updateFrameBounds(String frameId, Set<String> visitedFrameIds) {
        if (frameId == null || !visitedFrameIds.add(frameId)) return;
        FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame == null) return;

        FrameBoundsCalculator.Result bounds = FrameBoundsCalculator.computeCommittedBounds(
                frameId,
                mContext.getCurrentGraph().nodes.values(),
                mContext.getGraph().frames.values(),
                null
        );

        // 3. 应用计算结果
        if (bounds.hasChildren()) {
            frame.setPosition(bounds.x(), bounds.y());
            frame.setSize(bounds.width(), bounds.height());
        } else {
            // 如果内部被清空了，维持最后的状态或重置为默认大小，这里选择不强制缩回
            // frame.setSize(200f, 200f);
        }

        // 4. 通知 UI 层重绘该框
        mContext.notifyFrameBoundsUpdated(
                frameId, frame.uiPos[0], frame.uiPos[1], frame.uiSize[0], frame.uiSize[1]);

        // 5. 递归：如果当前图框本身也被包在另一个图框里，大图框也要跟着变大
        if (frame.parentFrame != null) {
            updateFrameBounds(frame.parentFrame, visitedFrameIds);
        }
    }

    public void setFrameProperty(String frameId, String title, int color) {
        FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame != null) {
            frame.title = title;
            frame.color = color;

            // 通知 UI 层
            mContext.notifyFrameTitleChanged(frameId, title);
        }
    }

    /**
     * 设置图框的位置（主要用于移动空图框）
     */
    public void setFramePosition(String frameId, float x, float y) {
        FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame != null) {
            frame.setPosition(x, y);

            // 通知 UI 层该框位置已改变
            mContext.notifyFrameBoundsUpdated(
                    frameId, frame.uiPos[0], frame.uiPos[1], frame.uiSize[0], frame.uiSize[1]);

            // 联动：如果这个空框自己是被包在一个大图框里的，它移动了，外层大图框也得跟着重算
            if (frame.parentFrame != null) {
                updateFrameBounds(frame.parentFrame);
            }
        }
    }
}
