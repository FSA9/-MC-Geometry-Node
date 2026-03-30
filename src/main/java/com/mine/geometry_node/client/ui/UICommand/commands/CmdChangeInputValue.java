package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;

public class CmdChangeInputValue implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPortId;
    private final Object mOldValue;
    private final Object mNewValue;

    public CmdChangeInputValue(GraphController controller, String nodeId, String portId, Object oldValue, Object newValue) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPortId = portId;
        this.mOldValue = oldValue;
        this.mNewValue = newValue;
    }

    @Override
    public void execute() {
        mController.setNodeInputValue(mNodeId, mPortId, mNewValue);
    }

    @Override
    public void undo() {
        mController.setNodeInputValue(mNodeId, mPortId, mOldValue);
    }
}