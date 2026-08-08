package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.NodeData;

public class CmdAddGroupVirtualPort implements ICommand {
    private final GraphController mController;
    private final String mBoundaryNodeId;

    private String mPortId;
    private String mCategory;
    private NodeData.PortConfig mPortConfig;

    public CmdAddGroupVirtualPort(GraphController controller, String boundaryNodeId) {
        this.mController = controller;
        this.mBoundaryNodeId = boundaryNodeId;
    }

    @Override
    public void execute() {
        if (mPortId == null) {
            mPortId = mController.addGroupVirtualPort(mBoundaryNodeId);
            mCategory = mController.getGroupVirtualPortCategory(mBoundaryNodeId, mPortId);
            mPortConfig = mController.getGroupVirtualPortConfig(mBoundaryNodeId, mPortId);
        } else {
            mController.restoreGroupVirtualPort(mBoundaryNodeId, mCategory, mPortId, mPortConfig);
        }
    }

    @Override
    public void undo() {
        if (mPortId != null) {
            mController.removeGroupVirtualPort(mBoundaryNodeId, mPortId);
        }
    }
}
