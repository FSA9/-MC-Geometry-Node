package com.mine.geometry_node.client.ui.document.edit.commands.group;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;

public class CmdAddGroupVirtualPort implements ICommand {
    private final GraphController mController;
    private final String mBoundaryNodeId;

    public CmdAddGroupVirtualPort(GraphController controller, String boundaryNodeId) {
        this.mController = controller;
        this.mBoundaryNodeId = boundaryNodeId;
    }

    @Override
    public void execute() {
        mController.addGroupVirtualPort(mBoundaryNodeId);
    }
}
