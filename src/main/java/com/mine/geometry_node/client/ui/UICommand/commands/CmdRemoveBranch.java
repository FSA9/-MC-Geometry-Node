package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdRemoveBranch implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String mNodeId;
    private final String mPropertyKey;
    private final int mOldCount;
    private final String mRefId;

    // --- 全量快照备份 ---
    // 移位删除会改变多个属性和输入值，所以直接深拷贝备份字典
    private final Map<String, Object> mBackupProperties = new HashMap<>();
    private final Map<String, Object> mBackupInputs = new HashMap<>();

    // 连线备份
    private final Map<String, List<Connection>> mBackupOutputs = new HashMap<>();
    private final Map<String, String> mBackupExecution = new HashMap<>();
    private final List<InboundConnectionBackup> mBackupInbounds = new ArrayList<>();

    private record InboundConnectionBackup(String sourceNodeId, String sourcePortId, String targetPortId) {}

    // 【修复点 1】：新增 refId 参数
    public CmdRemoveBranch(GraphController controller, NodeGraph graph, String nodeId, String propertyKey, int currentCount, String refId) {
        this.mController = controller;
        this.mGraph = graph;
        this.mNodeId = nodeId;
        this.mPropertyKey = propertyKey;
        this.mOldCount = currentCount;
        this.mRefId = refId;

        backupFullState();
    }

    private void backupFullState() {
        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        // 1. 备份所有属性和输入值
        mBackupProperties.putAll(targetNode.properties);
        mBackupInputs.putAll(targetNode.inputs);

        // 2. 备份本节点发出的所有数据连线
        for (Map.Entry<String, List<Connection>> entry : targetNode.outputs.entrySet()) {
            mBackupOutputs.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        // 3. 备份本节点发出的所有执行流连线
        mBackupExecution.putAll(targetNode.execution);

        // 4. 【关键修复】遍历全图，备份所有指向本节点（不管什么端口）的连线
        for (NodeData otherNode : mGraph.nodes.values()) {
            if (otherNode.id.equals(mNodeId)) continue;

            for (Map.Entry<String, List<Connection>> entry : otherNode.outputs.entrySet()) {
                String outPortId = entry.getKey();
                for (Connection link : entry.getValue()) {
                    if (link.targetNodeId().equals(mNodeId)) {
                        mBackupInbounds.add(new InboundConnectionBackup(otherNode.id, outPortId, link.targetPortName()));
                    }
                }
            }
        }
    }

    @Override
    public void execute() {
        // 【修复点 2】：不再是简单的 -1，而是调用我们在 GraphController 写好的移位删除逻辑
        mController.removeDynamicBranch(mNodeId, mRefId);
    }

    @Override
    public void undo() {
        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        // --- 撤销移位带来的破坏：先斩断现有关系，再用快照全量覆盖 ---

        // 1. 斩断当前全图中所有指向本节点的连线 (因为这些线现在的 targetPort 可能是移位后的错误端口)
        for (NodeData otherNode : mGraph.nodes.values()) {
            if (otherNode.id.equals(mNodeId)) continue;
            for (String outPort : new ArrayList<>(otherNode.outputs.keySet())) {
                for (Connection link : new ArrayList<>(otherNode.getConnections(outPort))) {
                    if (link.targetNodeId().equals(mNodeId)) {
                        mController.removeConnection(otherNode.id, outPort, mNodeId, link.targetPortName());
                    }
                }
            }
        }

        // 2. 清理本节点发出的所有残留连线
        for (String outPort : new ArrayList<>(targetNode.outputs.keySet())) {
            for (Connection link : new ArrayList<>(targetNode.getConnections(outPort))) {
                mController.removeConnection(mNodeId, outPort, link.targetNodeId(), link.targetPortName());
            }
        }
        for (String execPort : new ArrayList<>(targetNode.execution.keySet())) {
            mController.removeExecutionConnection(mNodeId, execPort);
        }

        // 3. 恢复节点的内部状态 (属性与输入值)
        targetNode.properties.clear();
        targetNode.properties.putAll(mBackupProperties);
        targetNode.inputs.clear();
        targetNode.inputs.putAll(mBackupInputs);

        // 4. 重建原本的所有连线
        for (Map.Entry<String, List<Connection>> entry : mBackupOutputs.entrySet()) {
            for (Connection link : entry.getValue()) {
                mController.addConnection(mNodeId, entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }
        for (Map.Entry<String, String> entry : mBackupExecution.entrySet()) {
            mController.addExecutionConnection(mNodeId, entry.getKey(), entry.getValue());
        }
        for (InboundConnectionBackup inbound : mBackupInbounds) {
            mController.addConnection(inbound.sourceNodeId, inbound.sourcePortId, mNodeId, inbound.targetPortId);
        }

        // 5. 强制触发 UI 刷新 (向总数属性写入旧值)
        mController.setNodeProperty(mNodeId, mPropertyKey, mOldCount);
    }
}