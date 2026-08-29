package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.NodeData;

import java.util.ArrayList;
import java.util.List;

public class CmdRemoveGroupVirtualPort implements ICommand {
    private final GraphController mController;
    private final String mBoundaryNodeId;
    private final String mPortId;

    private final String mCategory;
    private final NodeData.PortConfig mPortConfig;
    private final List<GraphController.ScopedConnectionSnapshot> mConnections = new ArrayList<>();

    public CmdRemoveGroupVirtualPort(GraphController controller, String boundaryNodeId, String portId) {
        this.mController = controller;
        this.mBoundaryNodeId = boundaryNodeId;
        this.mPortId = portId;
        this.mCategory = controller.getGroupVirtualPortCategory(boundaryNodeId, portId);
        this.mPortConfig = controller.getGroupVirtualPortConfig(boundaryNodeId, portId);
        backupConnections();
    }

    private void backupConnections() {
        mConnections.addAll(mController.getGroupVirtualPortConnectionSnapshots(mBoundaryNodeId, mPortId));
    }

    @Override
    public void execute() {
        mController.removeGroupVirtualPort(mBoundaryNodeId, mPortId);
    }

    @Override
    public void undo() {
        mController.restoreGroupVirtualPort(mBoundaryNodeId, mCategory, mPortId, mPortConfig);
        for (GraphController.ScopedConnectionSnapshot snapshot : mConnections) {
            mController.restoreGroupVirtualPortConnection(mBoundaryNodeId, snapshot);
        }
    }
}
