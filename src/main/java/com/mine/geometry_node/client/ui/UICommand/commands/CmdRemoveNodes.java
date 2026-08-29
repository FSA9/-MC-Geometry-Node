package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.ArrayList;
import java.util.List;

public class CmdRemoveNodes implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;

    // 保存被删除的节点数据
    private final List<NodeData> mRemovedNodes = new ArrayList<>();

    // 记录被斩断的连线快照 (现在不论数据流还是执行流，都可以完美存储 inPortId 了！)
    private record ConnectionSnapshot(String outNodeId, String outPortId, String inNodeId, String inPortId) {}

    // 别人指向被删节点的连线
    private final List<ConnectionSnapshot> mBrokenIncomingLinks = new ArrayList<>();
    private final List<ConnectionSnapshot> mBrokenIncomingExecs = new ArrayList<>();

    // 被删节点指向别人的连线 (修复 Bug 1 必须记录这些)
    private final List<ConnectionSnapshot> mBrokenOutgoingLinks = new ArrayList<>();
    private final List<ConnectionSnapshot> mBrokenOutgoingExecs = new ArrayList<>();

    public CmdRemoveNodes(GraphController controller, NodeGraph graph, List<String> nodeIdsToRemove) {
        this.mController = controller;
        this.mGraph = graph;

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
        mBrokenOutgoingLinks.clear();
        mBrokenOutgoingExecs.clear();

        List<String> targetIds = mRemovedNodes.stream().map(n -> n.id).toList();

        // 1. 扫描全图，找出所有【别人指向即将被删除节点】的连线，记录并断开
        for (NodeData otherNode : mGraph.nodes.values()) {
            if (targetIds.contains(otherNode.id)) continue;

            // 【核心修复2】使用 new ArrayList<>(keySet) 拷贝一份 Key，彻底杜绝 ConcurrentModificationException
            if (otherNode.outputs != null) {
                for (String outPort : new ArrayList<>(otherNode.outputs.keySet())) {
                    List<Connection> links = new ArrayList<>(otherNode.getConnections(outPort));
                    for (Connection link : links) {
                        if (targetIds.contains(link.targetNodeId())) {
                            mBrokenIncomingLinks.add(new ConnectionSnapshot(otherNode.id, outPort, link.targetNodeId(), link.targetPortName()));
                            mController.removeConnection(otherNode.id, outPort, link.targetNodeId(), link.targetPortName());
                        }
                    }
                }
            }

            if (otherNode.execOutputs != null) {
                for (String outPort : new ArrayList<>(otherNode.execOutputs.keySet())) {
                    Connection link = otherNode.execOutputs.get(outPort);
                    if (link != null && targetIds.contains(link.targetNodeId())) {
                        mBrokenIncomingExecs.add(new ConnectionSnapshot(otherNode.id, outPort, link.targetNodeId(), link.targetPortName()));
                        mController.removeExecutionConnection(otherNode.id, outPort);
                    }
                }
            }
        }

        // 2. 【核心修复1】正式移除节点前，必须显式斩断它们自己【向外发出】的连线
        // 这样目标节点 (如节点 B) 才会收到规范的断线事件，从而更新 UI 恢复交互框
        for (NodeData node : mRemovedNodes) {
            // 斩断发出的数据连线
            if (node.outputs != null) {
                for (String outPort : new ArrayList<>(node.outputs.keySet())) {
                    for (Connection link : new ArrayList<>(node.getConnections(outPort))) {
                        mBrokenOutgoingLinks.add(new ConnectionSnapshot(node.id, outPort, link.targetNodeId(), link.targetPortName()));
                        mController.removeConnection(node.id, outPort, link.targetNodeId(), link.targetPortName());
                    }
                }
            }
            // 斩断发出的执行流连线
            if (node.execOutputs != null) {
                for (String outPort : new ArrayList<>(node.execOutputs.keySet())) {
                    Connection link = node.execOutputs.get(outPort);
                    if (link != null) {
                        mBrokenOutgoingExecs.add(new ConnectionSnapshot(node.id, outPort, link.targetNodeId(), link.targetPortName()));
                        mController.removeExecutionConnection(node.id, outPort);
                    }
                }
            }
        }

        // 3. 一切连线关系都通过正式 API 解除了，安全移除节点
        for (NodeData node : mRemovedNodes) {
            mController.removeNode(node.id);
        }
    }

    @Override
    public void undo() {
        // 1. 把节点主体加回图中
        for (NodeData node : mRemovedNodes) {
            mController.addNode(node);
        }
        // 2. 依靠全量快照，恢复被删节点发出的连线
        for (ConnectionSnapshot snap : mBrokenOutgoingLinks) {
            mController.addConnection(snap.outNodeId, snap.outPortId, snap.inNodeId, snap.inPortId);
        }
        for (ConnectionSnapshot snap : mBrokenOutgoingExecs) {
            mController.addExecutionConnection(snap.outNodeId, snap.outPortId, snap.inNodeId, snap.inPortId);
        }

        // 3. 依靠全量快照，恢复外部指向它们的连线
        for (ConnectionSnapshot snap : mBrokenIncomingLinks) {
            mController.addConnection(snap.outNodeId, snap.outPortId, snap.inNodeId, snap.inPortId);
        }
        for (ConnectionSnapshot snap : mBrokenIncomingExecs) {
            mController.addExecutionConnection(snap.outNodeId, snap.outPortId, snap.inNodeId, snap.inPortId);
        }
    }
}
