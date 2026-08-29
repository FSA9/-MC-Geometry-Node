package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
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

    @Override
    public void undo() {
        mController.removeNode(mNodeData.id);
    }
}
