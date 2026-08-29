package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.*;

public class CmdPasteElements implements ICommand {
    private final GraphController mController;
    private final List<NodeData> mPastedNodes = new ArrayList<>();
    private final List<FrameData> mPastedFrames = new ArrayList<>();
    private final Map<String, Map<String, List<Connection>>> mPastedDataConnections = new HashMap<>();
    private final Map<String, Map<String, Connection>> mPastedExecutionConnections = new HashMap<>();

    public CmdPasteElements(GraphController controller, String json, float targetUiX, float targetUiY) {
        this.mController = controller;
        NodeGraph tempGraph = GraphJsonIO.fromJson(json);
        Map<String, String> oldToNewIdMap = new HashMap<>();
        boolean pasteFrames = !controller.getContext().isInsideGroupScope();

        // 1. 寻找阵型左上角（遍历包含 Node 和 Frame）
        float minX = Float.MAX_VALUE; float minY = Float.MAX_VALUE;
        for (NodeData oldNode : tempGraph.nodes.values()) {
            if (oldNode.uiPos[0] < minX) minX = oldNode.uiPos[0];
            if (oldNode.uiPos[1] < minY) minY = oldNode.uiPos[1];
        }
        if (pasteFrames) {
            for (FrameData oldFrame : tempGraph.frames.values()) {
                if (oldFrame.uiPos[0] < minX) minX = oldFrame.uiPos[0];
                if (oldFrame.uiPos[1] < minY) minY = oldFrame.uiPos[1];
            }
        }
        if (minX == Float.MAX_VALUE) { minX = 0; minY = 0; }

        // 2. 生成新 ID 与相对坐标偏移
        if (pasteFrames) {
            for (FrameData oldFrame : tempGraph.frames.values()) {
                String newId = UUID.randomUUID().toString();
                oldToNewIdMap.put(oldFrame.id, newId);
                oldFrame.id = newId;
                oldFrame.uiPos[0] = targetUiX + (oldFrame.uiPos[0] - minX);
                oldFrame.uiPos[1] = targetUiY + (oldFrame.uiPos[1] - minY);
                mPastedFrames.add(oldFrame);
            }
        }
        for (NodeData oldNode : tempGraph.nodes.values()) {
            String newId = UUID.randomUUID().toString();
            oldToNewIdMap.put(oldNode.id, newId);
            oldNode.id = newId;
            oldNode.uiPos[0] = targetUiX + (oldNode.uiPos[0] - minX);
            oldNode.uiPos[1] = targetUiY + (oldNode.uiPos[1] - minY);
            mPastedNodes.add(oldNode);
        }

        // 3. 重塑大环境的层级关系与连线
        for (FrameData frame : mPastedFrames) {
            // 如果原本属于某个框，且那个框也被一起复制了，就更新 ID；如果那个框没被复制，则晋升为顶层孤儿
            if (frame.parentFrame != null) {
                frame.parentFrame = oldToNewIdMap.getOrDefault(frame.parentFrame, null);
            }
        }
        for (NodeData node : mPastedNodes) {
            if (pasteFrames && node.parentFrame != null) {
                node.parentFrame = oldToNewIdMap.getOrDefault(node.parentFrame, null);
            } else {
                node.parentFrame = null;
            }

            Map<String, List<Connection>> newOutputs = new HashMap<>();
            for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                List<Connection> newLinks = new ArrayList<>();
                for (Connection oldLink : entry.getValue()) {
                    if (oldToNewIdMap.containsKey(oldLink.targetNodeId())) {
                        newLinks.add(new Connection(oldToNewIdMap.get(oldLink.targetNodeId()), oldLink.targetPortName()));
                    }
                }
                if (!newLinks.isEmpty()) newOutputs.put(entry.getKey(), newLinks);
            }
            mPastedDataConnections.put(node.id, newOutputs);
            node.outputs = new HashMap<>();

            Map<String, Connection> newExecOutputs = new HashMap<>();
            if (node.execOutputs != null) {
                for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                    Connection oldLink = entry.getValue();
                    if (oldToNewIdMap.containsKey(oldLink.targetNodeId())) {
                        newExecOutputs.put(entry.getKey(), new Connection(oldToNewIdMap.get(oldLink.targetNodeId()), oldLink.targetPortName()));
                    }
                }
            }
            mPastedExecutionConnections.put(node.id, newExecOutputs);
            node.execOutputs = new HashMap<>();
            if (node.connectedInputs != null) node.connectedInputs.clear();
        }
    }

    @Override
    public void execute() {
        for (FrameData frame : mPastedFrames) mController.addFrame(frame);
        for (NodeData node : mPastedNodes) mController.addNode(node);

        for (NodeData node : mPastedNodes) {
            Map<String, List<Connection>> dataConnections = mPastedDataConnections.getOrDefault(node.id, Map.of());
            for (Map.Entry<String, List<Connection>> entry : dataConnections.entrySet()) {
                for (Connection link : entry.getValue()) {
                    mController.addConnection(node.id, entry.getKey(), link.targetNodeId(), link.targetPortName());
                }
            }
            Map<String, Connection> executionConnections = mPastedExecutionConnections.getOrDefault(node.id, Map.of());
            for (Map.Entry<String, Connection> entry : executionConnections.entrySet()) {
                Connection link = entry.getValue();
                mController.addExecutionConnection(node.id, entry.getKey(), link.targetNodeId(), link.targetPortName());
            }
        }
    }

    @Override
    public void undo() {
        for (NodeData node : mPastedNodes) mController.removeNode(node.id);
        for (FrameData frame : mPastedFrames) mController.removeFrame(frame.id);
    }

    public List<NodeData> getPastedNodes() {
        return Collections.unmodifiableList(mPastedNodes);
    }

    public List<FrameData> getPastedFrames() {
        return Collections.unmodifiableList(mPastedFrames);
    }
}
