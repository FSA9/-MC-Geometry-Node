package com.mine.geometry_node.client.feature.graph.edit.commands.port;

import com.mine.geometry_node.client.feature.graph.edit.ICommand;
import com.mine.geometry_node.client.feature.graph.edit.GraphController;

public class CmdAddBranch implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPortId;
    private final int mNewCount;

    public CmdAddBranch(GraphController controller, String nodeId, String portId, int currentCount) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPortId = portId;
        this.mNewCount = currentCount + 1;
    }

    @Override
    public void execute() {
        mController.setNodeInputValue(mNodeId, mPortId, mNewCount);
    }

}
