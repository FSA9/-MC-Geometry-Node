package com.mine.geometry_node.client.ui.UICommand.commands.group;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;

public class CmdRemoveGroupVirtualPort implements ICommand {
    private final GraphController mController;
    private final String mBoundaryNodeId;
    private final String mPortId;

    public CmdRemoveGroupVirtualPort(GraphController controller, String boundaryNodeId, String portId) {
        this.mController = controller;
        this.mBoundaryNodeId = boundaryNodeId;
        this.mPortId = portId;
    }

    @Override
    public void execute() {
        mController.removeGroupVirtualPort(mBoundaryNodeId, mPortId);
    }

}
