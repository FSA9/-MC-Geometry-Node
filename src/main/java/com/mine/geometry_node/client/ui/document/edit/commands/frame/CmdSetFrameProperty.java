package com.mine.geometry_node.client.ui.document.edit.commands.frame;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;

public class CmdSetFrameProperty implements ICommand {
    private final GraphController mController;
    private final String mFrameId;

    private final String mNewTitle;

    private final int mNewColor;

    public CmdSetFrameProperty(GraphController controller, String frameId, String newTitle, int newColor) {
        this.mController = controller;
        this.mFrameId = frameId;
        this.mNewTitle = newTitle;
        this.mNewColor = newColor;
    }

    @Override
    public void execute() {
        mController.setFrameProperty(mFrameId, mNewTitle, mNewColor);
    }

}
