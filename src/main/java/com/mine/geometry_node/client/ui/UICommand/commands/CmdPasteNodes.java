package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.*;

public class CmdPasteNodes implements ICommand {
    private final GraphController mController;
    private final List<NodeData> mPastedNodes = new ArrayList<>();

    /**
     * @param targetUiX 鼠标所在的 UI 逻辑坐标 X
     * @param targetUiY 鼠标所在的 UI 逻辑坐标 Y
     */
    public CmdPasteNodes(GraphController controller, String json, float targetUiX, float targetUiY) {
        this.mController = controller;
        NodeGraph tempGraph = GraphJsonIO.fromJson(json);

        Map<String, String> oldToNewIdMap = new HashMap<>();

        // 1. 计算被复制节点群的整体包围盒左上角（用于计算相对偏移排列）
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (NodeData oldNode : tempGraph.nodes.values()) {
            if (oldNode.uiPos[0] < minX) minX = oldNode.uiPos[0];
            if (oldNode.uiPos[1] < minY) minY = oldNode.uiPos[1];
        }
        if (minX == Float.MAX_VALUE) { minX = 0; minY = 0; }

        // 2. 生成新 ID，并根据鼠标位置计算绝对偏移
        for (NodeData oldNode : tempGraph.nodes.values()) {
            String newId = UUID.randomUUID().toString();
            oldToNewIdMap.put(oldNode.id, newId);
            oldNode.id = newId;

            // 保持相对阵型，整体移动到鼠标位置
            float relativeX = oldNode.uiPos[0] - minX;
            float relativeY = oldNode.uiPos[1] - minY;
            oldNode.uiPos[0] = targetUiX + relativeX;
            oldNode.uiPos[1] = targetUiY + relativeY;

            mPastedNodes.add(oldNode);
        }

        // 3. 重映射连线并彻底清除外部残留连线
        for (NodeData node : mPastedNodes) {

            // 修复数据连线
            Map<String, List<Connection>> newOutputs = new HashMap<>();
            for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                List<Connection> newLinks = new ArrayList<>();
                for (Connection oldLink : entry.getValue()) {
                    String targetId = oldLink.targetNodeId();
                    // 【核心修改】：只保留复制群体内部的连线，丢弃对外部原图节点的连线
                    if (oldToNewIdMap.containsKey(targetId)) {
                        newLinks.add(new Connection(oldToNewIdMap.get(targetId), oldLink.targetPortName()));
                    }
                }
                // 只有非空时才保存，确保干净
                if (!newLinks.isEmpty()) {
                    newOutputs.put(entry.getKey(), newLinks);
                }
            }
            node.outputs = newOutputs;

            // 修复执行流连线
            Map<String, String> newExec = new HashMap<>();
            for (Map.Entry<String, String> entry : node.execution.entrySet()) {
                String targetId = entry.getValue();
                // 同理，只保留内部互连
                if (oldToNewIdMap.containsKey(targetId)) {
                    newExec.put(entry.getKey(), oldToNewIdMap.get(targetId));
                }
            }
            node.execution = newExec;

            // 【核心修改】：彻底清空残留的被动输入连接状态
            if (node.connectedInputs != null) {
                node.connectedInputs.clear();
            } else {
                node.connectedInputs = new HashSet<>();
            }
        }
    }

    @Override
    public void execute() {
        for (NodeData node : mPastedNodes) {
            mController.addNode(node);
        }

        // 恢复内部互连
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
        for (NodeData node : mPastedNodes) {
            mController.removeNode(node.id);
        }
    }
}