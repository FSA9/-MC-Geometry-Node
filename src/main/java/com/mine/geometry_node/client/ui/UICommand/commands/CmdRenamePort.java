package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;

public class CmdRenamePort implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mCategory;
    private final String mPortId;
    private final String mOldName;
    private final String mNewName;

    public CmdRenamePort(GraphController controller, String nodeId, String category, String portId, String oldName, String newName) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mCategory = category;
        this.mPortId = portId;
        this.mOldName = oldName;
        this.mNewName = newName;
    }

    @Override
    public void execute() {
        mController.setPortCustomName(mNodeId, mCategory, mPortId, mNewName);
    }

    @Override
    public void undo() {
        mController.setPortCustomName(mNodeId, mCategory, mPortId, mOldName);
    }
}