package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;

public class CmdAddBranch implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPortId;
    private final int mOldCount;
    private final int mNewCount;

    public CmdAddBranch(GraphController controller, String nodeId, String portId, int currentCount) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPortId = portId;
        this.mOldCount = currentCount;
        this.mNewCount = currentCount + 1;
    }

    @Override
    public void execute() {
        mController.setNodeInputValue(mNodeId, mPortId, mNewCount);
    }

    @Override
    public void undo() {
        mController.setNodeInputValue(mNodeId, mPortId, mOldCount);
    }
}