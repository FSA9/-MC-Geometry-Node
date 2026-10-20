package com.mine.geometry_node.client.ui.document.edit.commands.port;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;

public class CmdRenamePort implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mCategory;
    private final String mPortId;
    private final String mNewName;

    public CmdRenamePort(GraphController controller, String nodeId, String category, String portId, String newName) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mCategory = category;
        this.mPortId = portId;
        this.mNewName = newName;
    }

    @Override
    public void execute() {
        mController.setPortCustomName(mNodeId, mCategory, mPortId, mNewName);
    }

}
