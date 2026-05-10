package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdMoveNode implements ICommand {
    private final GraphController mController;
    private final List<String> mNodeIds;
    private final Map<String, float[]> mOldPositions = new HashMap<>();
    private final Map<String, float[]> mNewPositions = new HashMap<>();

    private final float mDx, mDy;

    public CmdMoveNode(GraphController controller, List<String> nodeIds, float dx, float dy) {
        this.mController = controller;
        this.mNodeIds = nodeIds;
        this.mDx = dx;
        this.mDy = dy;

        // 记录移动前的旧位置和期望的新位置
        for (String id : nodeIds) {
            float[] oldPos = controller.getNodePosition(id);
            if (oldPos != null) {
                mOldPositions.put(id, new float[]{oldPos[0], oldPos[1]});
                mNewPositions.put(id, new float[]{oldPos[0] + dx, oldPos[1] + dy});
            }
        }
    }

    @Override
    public void execute() {
        for (String id : mNodeIds) {
            float[] pos = mNewPositions.get(id);
            if (pos != null) mController.setNodePosition(id, pos[0], pos[1]);
        }
    }

    @Override
    public void undo() {
        for (String id : mNodeIds) {
            float[] pos = mOldPositions.get(id);
            if (pos != null) mController.setNodePosition(id, pos[0], pos[1]);
        }
    }
}