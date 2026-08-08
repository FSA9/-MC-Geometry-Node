package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdMoveElements implements ICommand {
    private final GraphController mController;
    private final float mDx, mDy;

    // 统筹记录所有发生移动的节点和图框
    private final Map<String, float[]> mOldNodePos = new HashMap<>();
    private final Map<String, float[]> mNewNodePos = new HashMap<>();

    // 如果你希望图框在空载时也能移动，需要记录纯图框的位置
    private final Map<String, float[]> mOldFramePos = new HashMap<>();
    private final Map<String, float[]> mNewFramePos = new HashMap<>();

    public CmdMoveElements(GraphController controller, List<String> nodeIds, List<String> frameIds, float dx, float dy) {
        this.mController = controller;
        this.mDx = dx;
        this.mDy = dy;

        List<String> allAffectedNodes = new ArrayList<>(nodeIds);
        List<String> allAffectedFrames = new ArrayList<>(frameIds);

        // 1. 递归找出所有被选中的 Frame 下属的子节点和子图框
        for (String frameId : frameIds) {
            collectChildren(controller, frameId, allAffectedNodes, allAffectedFrames);
        }

        // 2. 记录 Node 坐标
        for (String id : allAffectedNodes) {
            float[] oldPos = controller.getNodePosition(id);
            if (oldPos != null && !mOldNodePos.containsKey(id)) {
                mOldNodePos.put(id, new float[]{oldPos[0], oldPos[1]});
                mNewNodePos.put(id, new float[]{oldPos[0] + dx, oldPos[1] + dy});
            }
        }

        // 3. 记录空图框坐标 (有子节点的图框会自动通过 Controller 重新算 bounds，无需在此移动)
        for (String id : allAffectedFrames) {
            FrameData frame = controller.getContext().getGraph().getFrame(id); // 需要在 Controller 提供获取方法
            if (frame != null && !mOldFramePos.containsKey(id)) {
                mOldFramePos.put(id, new float[]{frame.uiPos[0], frame.uiPos[1]});
                mNewFramePos.put(id, new float[]{frame.uiPos[0] + dx, frame.uiPos[1] + dy});
            }
        }
    }

    private void collectChildren(GraphController controller, String parentFrameId, List<String> outNodes, List<String> outFrames) {
        // 找节点
        for (NodeData node : controller.getContext().getCurrentGraph().nodes.values()) {
            if (parentFrameId.equals(node.parentFrame) && !outNodes.contains(node.id)) {
                outNodes.add(node.id);
            }
        }
        // 找子图框
        for (FrameData frame : controller.getContext().getGraph().frames.values()) {
            if (parentFrameId.equals(frame.parentFrame) && !outFrames.contains(frame.id)) {
                outFrames.add(frame.id);
                // 递归继续找孙子
                collectChildren(controller, frame.id, outNodes, outFrames);
            }
        }
    }

    @Override
    public void execute() {
        // 注意：Controller 里的 setNodePosition 会自动触发图框重新计算 Bounds
        for (Map.Entry<String, float[]> entry : mNewNodePos.entrySet()) {
            mController.setNodePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
        // 如果图框是空的，需要手动挪一下
        for (Map.Entry<String, float[]> entry : mNewFramePos.entrySet()) {
            // 需要你在 controller 补一个 setFramePosition(id, x, y)
            mController.setFramePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<String, float[]> entry : mOldNodePos.entrySet()) {
            mController.setNodePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
        for (Map.Entry<String, float[]> entry : mOldFramePos.entrySet()) {
            mController.setFramePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }
}
