package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.NodeData;

public class CmdSetGroupNodeProperty implements ICommand {
    private final GraphController mController;
    private final String mNodeId;

    private final String mOldName;
    private final String mNewName;

    private final Integer mOldColor;
    private final Integer mNewColor;

    private final String mOldComment;
    private final String mNewComment;

    public CmdSetGroupNodeProperty(GraphController controller, String nodeId, String newName, int newColor, String newComment) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mNewName = newName;
        this.mNewColor = newColor;
        this.mNewComment = newComment;

        NodeData node = controller.getCurrentGraph().getNode(nodeId);
        if (node != null) {
            this.mOldName = node.customName;
            this.mOldColor = node.customColor;
            this.mOldComment = node.comment;
        } else {
            this.mOldName = null;
            this.mOldColor = null;
            this.mOldComment = null;
        }
    }

    @Override
    public void execute() {
        mController.setGroupNodeProperty(mNodeId, mNewName, mNewColor, mNewComment);
    }

    @Override
    public void undo() {
        mController.setGroupNodeProperty(mNodeId, mOldName, mOldColor, mOldComment);
    }
}
