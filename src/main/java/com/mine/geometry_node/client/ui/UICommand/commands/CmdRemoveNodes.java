package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CmdRemoveNodes implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;

    // 保存被删除的节点数据，用于撤销
    private final List<NodeData> mRemovedNodes = new ArrayList<>();

    // 保存被斩断的【其他节点指向被删除节点】的连线，用于撤销
    private record ConnectionSnapshot(String outNodeId, String outPortId, String inNodeId, String inPortId) {}
    private final List<ConnectionSnapshot> mBrokenIncomingLinks = new ArrayList<>();
    private final List<ConnectionSnapshot> mBrokenIncomingExecs = new ArrayList<>();

    public CmdRemoveNodes(GraphController controller, NodeGraph graph, List<String> nodeIdsToRemove) {
        this.mController = controller;
        this.mGraph = graph;

        // 1. 收集将被删除的节点深拷贝或直接引用 (这里直接存引用，因为我们从图里移除了它)
        for (String id : nodeIdsToRemove) {
            NodeData node = graph.getNode(id);
            if (node != null) {
                mRemovedNodes.add(node);
            }
        }
    }

    @Override
    public void execute() {
        mBrokenIncomingLinks.clear();
        mBrokenIncomingExecs.clear();
        List<String> targetIds = mRemovedNodes.stream().map(n -> n.id).toList();

        // 1. 扫描全图，找出所有指向这些即将被删除节点的连线，记录并断开
        for (NodeData otherNode : mGraph.nodes.values()) {
            if (targetIds.contains(otherNode.id)) continue; // 跳过本身也要被删的节点

            // 检查数据连线
            for (Map.Entry<String, List<Connection>> entry : otherNode.outputs.entrySet()) {
                String outPort = entry.getKey();
                // 必须拷贝一份 List 防止并发修改异常
                List<Connection> links = new ArrayList<>(entry.getValue());
                for (Connection link : links) {
                    if (targetIds.contains(link.targetNodeId())) {
                        mBrokenIncomingLinks.add(new ConnectionSnapshot(otherNode.id, outPort, link.targetNodeId(), link.targetPortName()));
                        mController.removeConnection(otherNode.id, outPort, link.targetNodeId(), link.targetPortName());
                    }
                }
            }

            // 检查执行流连线
            for (Map.Entry<String, String> entry : otherNode.execution.entrySet()) {
                if (targetIds.contains(entry.getValue())) {
                    mBrokenIncomingExecs.add(new ConnectionSnapshot(otherNode.id, entry.getKey(), entry.getValue(), null));
                    mController.removeExecutionConnection(otherNode.id, entry.getKey());
                }
            }
        }

        // 2. 正式从图中移除这些节点
        for (NodeData node : mRemovedNodes) {
            mController.removeNode(node.id);
        }
    }

    @Override
    public void undo() {
        // 1. 把节点加回图中
        for (NodeData node : mRemovedNodes) {
            mController.addNode(node);
        }

        // 2. 恢复它们原本的内部发出连线 (因为 NodeData 里的 outputs 和 execution 没被清空，只要发事件告诉 UI 重连即可)
        for (NodeData node : mRemovedNodes) {
            for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                for (Connection link : entry.getValue()) {
                    // 通知 UI 添加连线，因为数据已经在 node 里了
                    mController.addConnection(node.id, entry.getKey(), link.targetNodeId(), link.targetPortName());
                }
            }
            for (Map.Entry<String, String> entry : node.execution.entrySet()) {
                mController.addExecutionConnection(node.id, entry.getKey(), entry.getValue());
            }
        }

        // 3. 恢复那些被斩断的外部节点指向它们的连线
        for (ConnectionSnapshot snap : mBrokenIncomingLinks) {
            mController.addConnection(snap.outNodeId, snap.outPortId, snap.inNodeId, snap.inPortId);
        }
        for (ConnectionSnapshot snap : mBrokenIncomingExecs) {
            mController.addExecutionConnection(snap.outNodeId, snap.outPortId, snap.inNodeId);
        }
    }
}