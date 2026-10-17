package com.mine.geometry_node.client.ui.UICommand.commands.group;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;

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
