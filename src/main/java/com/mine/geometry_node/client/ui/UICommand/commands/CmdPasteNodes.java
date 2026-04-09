package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.*;

public class CmdPasteNodes implements ICommand {
    private final GraphController mController;
    private final List<NodeData> mPastedNodes = new ArrayList<>();

    /**
     * @param controller 图控制器
     * @param json 剪贴板中复制的子图 JSON
     * @param offset 粘贴时为了避免完全重合，给的一个像素偏移量
     */
    public CmdPasteNodes(GraphController controller, String json, float offset) {
        this.mController = controller;

        // 1. 利用现有的 JSON 工具，把 JSON 当作一个临时的 Graph 读出来
        NodeGraph tempGraph = GraphJsonIO.fromJson(json);

        // 2. ID 重映射表：Old ID -> New ID
        Map<String, String> oldToNewIdMap = new HashMap<>();

        // 3. 生成新 ID，并处理位置偏移
        for (NodeData oldNode : tempGraph.nodes.values()) {
            String newId = UUID.randomUUID().toString(); // 生成全新ID
            oldToNewIdMap.put(oldNode.id, newId);
            oldNode.id = newId;

            // 稍微偏移一下坐标，防止粘贴的节点和原节点完全叠在一起看不见
            oldNode.uiPos[0] += offset;
            oldNode.uiPos[1] += offset;

            mPastedNodes.add(oldNode);
        }

        // 4. 重映射连线：把内部互相连接的旧 ID 替换成新 ID
        for (NodeData node : mPastedNodes) {
            // 修复数据连线
            Map<String, List<Connection>> newOutputs = new HashMap<>();
            for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                List<Connection> newLinks = new ArrayList<>();
                for (Connection oldLink : entry.getValue()) {
                    String targetId = oldLink.targetNodeId();
                    // 如果连接的目标也在本次复制的节点中，更新为它的新 ID
                    if (oldToNewIdMap.containsKey(targetId)) {
                        newLinks.add(new Connection(oldToNewIdMap.get(targetId), oldLink.targetPortName()));
                    } else {
                        // 如果连接的目标不在复制集合中，保留对原图外部节点的引用（或者你想断开也可以直接 drop 掉）
                        newLinks.add(oldLink);
                    }
                }
                newOutputs.put(entry.getKey(), newLinks);
            }
            node.outputs = newOutputs;

            // 修复执行流连线
            Map<String, String> newExec = new HashMap<>();
            for (Map.Entry<String, String> entry : node.execution.entrySet()) {
                String targetId = entry.getValue();
                newExec.put(entry.getKey(), oldToNewIdMap.getOrDefault(targetId, targetId));
            }
            node.execution = newExec;

            // 清空已连接输入状态，因为这是新节点，稍后由 Controller 重新计算
            node.connectedInputs.clear();
        }
    }

    @Override
    public void execute() {
        // 先把节点加进去
        for (NodeData node : mPastedNodes) {
            mController.addNode(node);
        }

        // 节点就绪后，再发送连线事件更新 UI
        for (NodeData node : mPastedNodes) {
            for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                for (Connection link : entry.getValue()) {
                    mController.addConnection(node.id, entry.getKey(), link.targetNodeId(), link.targetPortName());
                }
            }
            for (Map.Entry<String, String> entry : node.execution.entrySet()) {
                mController.addExecutionConnection(node.id, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void undo() {
        // 撤销粘贴很简单：直接把这些新生成的节点删掉即可
        for (NodeData node : mPastedNodes) {
            mController.removeNode(node.id);
        }
    }
}