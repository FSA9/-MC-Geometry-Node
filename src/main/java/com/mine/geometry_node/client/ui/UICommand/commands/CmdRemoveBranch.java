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
    private final int mNewCount;

    // 1. 本节点输出出去的数据连线备份
    private final Map<String, List<Connection>> mBackupOutputs = new HashMap<>();
    // 2. 本节点输出出去的执行流连线备份
    private final Map<String, String> mBackupExecution = new HashMap<>();
    // 3. 其他节点连接到本节点失效端口的连线备份 (谁 -> 连到了本节点的哪个端口)
    private final List<InboundConnectionBackup> mBackupInbounds = new ArrayList<>();

    private record InboundConnectionBackup(String sourceNodeId, String sourcePortId, String targetPortId) {}

    public CmdRemoveBranch(GraphController controller, NodeGraph graph, String nodeId, String propertyKey, int currentCount) {
        this.mController = controller;
        this.mGraph = graph;
        this.mNodeId = nodeId;
        this.mPropertyKey = propertyKey;
        this.mOldCount = currentCount;
        this.mNewCount = currentCount - 1;

        backupConnectionsBeforeRemoval();
    }

    private void backupConnectionsBeforeRemoval() {
        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        // 针对当前即将被删除的序号 (mOldCount)
        String deadInputPort = "case_" + mOldCount;
        String deadOutputPort = "flow_out_" + mOldCount;

        // 1. 备份本节点的输出数据连线 (目前 Switch 没有，但为了代码通用性写上)
        if (targetNode.outputs.containsKey(deadOutputPort)) {
            mBackupOutputs.put(deadOutputPort, new ArrayList<>(targetNode.outputs.get(deadOutputPort)));
        }

        // 2. 备份本节点的执行流连线
        if (targetNode.execution.containsKey(deadOutputPort)) {
            mBackupExecution.put(deadOutputPort, targetNode.execution.get(deadOutputPort));
        }

        // 3. 遍历全图，寻找连接到 deadInputPort 的线
        for (NodeData otherNode : mGraph.nodes.values()) {
            if (otherNode.id.equals(mNodeId)) continue;

            for (Map.Entry<String, List<Connection>> entry : otherNode.outputs.entrySet()) {
                String outPortId = entry.getKey();
                for (Connection link : entry.getValue()) {
                    if (link.targetNodeId().equals(mNodeId) && link.targetPortName().equals(deadInputPort)) {
                        mBackupInbounds.add(new InboundConnectionBackup(otherNode.id, outPortId, deadInputPort));
                    }
                }
            }
        }
    }

    @Override
    public void execute() {
        // 直接降维打击，Controller 会自动通过 setNodeProperty 斩断连线并刷新 UI
        mController.setNodeProperty(mNodeId, mPropertyKey, mNewCount);
    }

    @Override
    public void undo() {
        // 1. 恢复端口数量 (UI 会重新长出这个端口)
        mController.setNodeProperty(mNodeId, mPropertyKey, mOldCount);

        NodeData targetNode = mGraph.getNode(mNodeId);
        if (targetNode == null) return;

        // 2. 恢复输出数据连线
        for (Map.Entry<String, List<Connection>> entry : mBackupOutputs.entrySet()) {
            for (Connection link : entry.getValue()) {
                mController.addConnection(mNodeId, entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }

        // 3. 恢复执行流连线
        for (Map.Entry<String, String> entry : mBackupExecution.entrySet()) {
            mController.addExecutionConnection(mNodeId, entry.getKey(), entry.getValue());
        }

        // 4. 恢复外来连线
        for (InboundConnectionBackup inbound : mBackupInbounds) {
            mController.addConnection(inbound.sourceNodeId, inbound.sourcePortId, mNodeId, inbound.targetPortId);
        }
    }
}