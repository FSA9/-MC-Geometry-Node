package com.mine.geometry_node.client.feature.graph.edit.commands.node;

import com.mine.geometry_node.client.feature.graph.edit.ICommand;
import com.mine.geometry_node.client.feature.graph.edit.GraphController;
import com.mine.geometry_node.core.node.document.NodeData;

public class CmdAddNode implements ICommand {
    private final GraphController mController;
    private final NodeData mNodeData;

    public CmdAddNode(GraphController controller, NodeData nodeData) {
        this.mController = controller;
        this.mNodeData = nodeData;
    }

    @Override
    public boolean canExecute() {
        return mController != null && mController.canAddNode(mNodeData);
    }

    @Override
    public void execute() {
        mController.addNode(mNodeData);
    }

}
