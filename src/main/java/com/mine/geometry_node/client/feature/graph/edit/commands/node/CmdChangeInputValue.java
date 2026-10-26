package com.mine.geometry_node.client.feature.graph.edit.commands.node;

import com.mine.geometry_node.client.feature.graph.edit.ICommand;
import com.mine.geometry_node.client.feature.graph.edit.GraphController;

public class CmdChangeInputValue implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPortId;
    private final Object mNewValue;

    public CmdChangeInputValue(GraphController controller, String nodeId, String portId, Object newValue) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPortId = portId;
        this.mNewValue = newValue;
    }

    @Override
    public void execute() {
        mController.setNodeInputValue(mNodeId, mPortId, mNewValue);
    }

}
