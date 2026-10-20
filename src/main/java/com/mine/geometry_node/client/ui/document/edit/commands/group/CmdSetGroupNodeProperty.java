package com.mine.geometry_node.client.ui.document.edit.commands.group;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;

public class CmdSetGroupNodeProperty implements ICommand {
    private final GraphController mController;
    private final String mNodeId;

    private final String mNewName;

    private final Integer mNewColor;

    private final String mNewComment;

    public CmdSetGroupNodeProperty(GraphController controller, String nodeId, String newName, int newColor, String newComment) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mNewName = newName;
        this.mNewColor = newColor;
        this.mNewComment = newComment;
    }

    @Override
    public void execute() {
        mController.setGroupNodeProperty(mNodeId, mNewName, mNewColor, mNewComment);
    }

}
