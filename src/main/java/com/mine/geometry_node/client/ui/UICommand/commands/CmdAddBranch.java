package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;

public class CmdAddBranch implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPropertyKey;
    private final int mOldCount;
    private final int mNewCount;

    public CmdAddBranch(GraphController controller, String nodeId, String propertyKey, int currentCount) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPropertyKey = propertyKey;
        this.mOldCount = currentCount;
        this.mNewCount = currentCount + 1;
    }

    @Override
    public void execute() {
        // 交给 Controller，它会自动修改属性 -> 重算 NodeDef -> 通知 UI 刷新排版
        mController.setNodeProperty(mNodeId, mPropertyKey, mNewCount);
    }

    @Override
    public void undo() {
        mController.setNodeProperty(mNodeId, mPropertyKey, mOldCount);
    }
}