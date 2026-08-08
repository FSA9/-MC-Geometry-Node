package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdRemoveBranch implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String mNodeId;
    private final String mPropertyKey; // 现在这个 Key 对应 inputs 里的键
    private final int mOldCount;

    private final int mRemoveIndex;

    // --- 全量快照备份 ---
    private final Map<String, Object> mBackupInputs = new HashMap<>();
    private final Map<String, List<Connection>> mBackupOutputs = new HashMap<>();
    private final Map<String, Connection> mBackupExecution = new HashMap<>();
    private final Map<String, NodeData.PortConfig> mBackupPortConfigInputs = new HashMap<>();
    private final Map<String, NodeData.PortConfig> mBackupPortConfigExecInputs = new HashMap<>();
    private final Map<String, NodeData.PortConfig> mBackupPortConfigExecOutputs = new HashMap<>();
    private final Map<String, NodeData.PortConfig> mBackupPortConfigOutputs = new HashMap<>();
    private final List<InboundConnectionBackup> mBackupInbounds = new ArrayList<>();

    private record InboundConnectionBackup(
            String sourceNodeId,
            String sourcePortId,
            String targetPortId,
            boolean execution
    ) {}

    public CmdRemoveBranch(GraphController controller, NodeGraph graph, String nodeId, String propertyKey, int currentCount, int removeIndex) {
        this.mController = controller;
        this.mGraph = graph;
        this.mNodeId = nodeId;
        this.mPropertyKey = propertyKey;
        this.mOldCount = currentCount;
        this.mRemoveIndex = removeIndex;

        backupFullState();
    }

    private void backupFullState() {
        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        mBackupInputs.putAll(targetNode.inputs);

        for (Map.Entry<String, List<Connection>> entry : targetNode.outputs.entrySet()) {
            mBackupOutputs.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        // 备份 execOutputs
        if (targetNode.execOutputs != null) {
            mBackupExecution.putAll(targetNode.execOutputs);
        }

        if (targetNode.portConfig != null) {
            backupPortsConfig(targetNode.portConfig.inputs, mBackupPortConfigInputs);
            backupPortsConfig(targetNode.portConfig.execInputs, mBackupPortConfigExecInputs);
            backupPortsConfig(targetNode.portConfig.execOutputs, mBackupPortConfigExecOutputs);
            backupPortsConfig(targetNode.portConfig.outputs, mBackupPortConfigOutputs);
        }

        for (NodeData otherNode : mGraph.nodes.values()) {
            if (otherNode.id.equals(mNodeId)) continue;
            for (Map.Entry<String, List<Connection>> entry : otherNode.outputs.entrySet()) {
                String outPortId = entry.getKey();
                for (Connection link : entry.getValue()) {
                    if (link.targetNodeId().equals(mNodeId)) {
                        mBackupInbounds.add(new InboundConnectionBackup(
                                otherNode.id, outPortId, link.targetPortName(), false));
                    }
                }
            }
            for (Map.Entry<String, Connection> entry : otherNode.execOutputs.entrySet()) {
                Connection link = entry.getValue();
                if (link.targetNodeId().equals(mNodeId)) {
                    mBackupInbounds.add(new InboundConnectionBackup(
                            otherNode.id, entry.getKey(), link.targetPortName(), true));
                }
            }
        }
    }

    @Override
    public void execute() {
        mController.removeDynamicBranch(mNodeId, mPropertyKey, mRemoveIndex, mOldCount);
    }

    @Override
    public void undo() {
        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        for (NodeData otherNode : mGraph.nodes.values()) {
            if (otherNode.id.equals(mNodeId)) continue;
            for (String outPort : new ArrayList<>(otherNode.outputs.keySet())) {
                for (Connection link : new ArrayList<>(otherNode.getConnections(outPort))) {
                    if (link.targetNodeId().equals(mNodeId)) {
                        mController.removeConnection(otherNode.id, outPort, mNodeId, link.targetPortName());
                    }
                }
            }
            for (String execPort : new ArrayList<>(otherNode.execOutputs.keySet())) {
                Connection link = otherNode.execOutputs.get(execPort);
                if (link != null && link.targetNodeId().equals(mNodeId)) {
                    mController.removeExecutionConnection(otherNode.id, execPort);
                }
            }
        }

        for (String outPort : new ArrayList<>(targetNode.outputs.keySet())) {
            for (Connection link : new ArrayList<>(targetNode.getConnections(outPort))) {
                mController.removeConnection(mNodeId, outPort, link.targetNodeId(), link.targetPortName());
            }
        }

        if (targetNode.execOutputs != null) {
            for (String execPort : new ArrayList<>(targetNode.execOutputs.keySet())) {
                mController.removeExecutionConnection(mNodeId, execPort);
            }
        }

        targetNode.inputs.clear();
        targetNode.inputs.putAll(mBackupInputs);
        restorePortsConfig(targetNode);

        for (Map.Entry<String, List<Connection>> entry : mBackupOutputs.entrySet()) {
            for (Connection link : entry.getValue()) {
                mController.addConnection(mNodeId, entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }

        // 恢复执行流备份
        for (Map.Entry<String, Connection> entry : mBackupExecution.entrySet()) {
            Connection link = entry.getValue();
            mController.addExecutionConnection(mNodeId, entry.getKey(), link.targetNodeId(), link.targetPortName());
        }

        for (InboundConnectionBackup inbound : mBackupInbounds) {
            if (inbound.execution) {
                mController.addExecutionConnection(
                        inbound.sourceNodeId, inbound.sourcePortId, mNodeId, inbound.targetPortId);
            } else {
                mController.addConnection(
                        inbound.sourceNodeId, inbound.sourcePortId, mNodeId, inbound.targetPortId);
            }
        }

        mController.setNodeInputValue(mNodeId, mPropertyKey, mOldCount);
    }

    private void backupPortsConfig(Map<String, NodeData.PortConfig> source, Map<String, NodeData.PortConfig> target) {
        if (source == null) return;
        for (Map.Entry<String, NodeData.PortConfig> entry : source.entrySet()) {
            target.put(entry.getKey(), copyPortConfig(entry.getValue()));
        }
    }

    private void restorePortsConfig(NodeData node) {
        node.portConfig = new NodeData.PortsConfig();
        restorePortsConfigMap(mBackupPortConfigInputs, node.portConfig.inputs);
        restorePortsConfigMap(mBackupPortConfigExecInputs, node.portConfig.execInputs);
        restorePortsConfigMap(mBackupPortConfigExecOutputs, node.portConfig.execOutputs);
        restorePortsConfigMap(mBackupPortConfigOutputs, node.portConfig.outputs);
    }

    private void restorePortsConfigMap(Map<String, NodeData.PortConfig> source, Map<String, NodeData.PortConfig> target) {
        target.clear();
        for (Map.Entry<String, NodeData.PortConfig> entry : source.entrySet()) {
            target.put(entry.getKey(), copyPortConfig(entry.getValue()));
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
}
