package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;

public class CmdChangeProperty implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPropKey;
    private final Object mOldValue;
    private final Object mNewValue;

    public CmdChangeProperty(GraphController controller, String nodeId, String propKey, Object oldValue, Object newValue) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPropKey = propKey;
        this.mOldValue = oldValue;
        this.mNewValue = newValue;
    }

    @Override
    public void execute() {
        mController.setNodeProperty(mNodeId, mPropKey, mNewValue);
    }

    @Override
    public void undo() {
        mController.setNodeProperty(mNodeId, mPropKey, mOldValue);
    }
}