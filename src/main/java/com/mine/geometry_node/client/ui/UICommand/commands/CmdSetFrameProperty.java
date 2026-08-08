package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;

public class CmdSetFrameProperty implements ICommand {
    private final GraphController mController;
    private final String mFrameId;

    private final String mOldTitle;
    private final String mNewTitle;

    private final int mOldColor;
    private final int mNewColor;

    public CmdSetFrameProperty(GraphController controller, String frameId, String newTitle, int newColor) {
        this.mController = controller;
        this.mFrameId = frameId;
        this.mNewTitle = newTitle;
        this.mNewColor = newColor;

        FrameData frame = controller.getContext().getGraph().getFrame(frameId);
        if (frame != null) {
            this.mOldTitle = frame.title;
            this.mOldColor = frame.color;
        } else {
            this.mOldTitle = "New Frame";
            this.mOldColor = 0xFF556677;
        }
    }

    @Override
    public void execute() {
        mController.setFrameProperty(mFrameId, mNewTitle, mNewColor);
    }

    @Override
    public void undo() {
        mController.setFrameProperty(mFrameId, mOldTitle, mOldColor);
    }
}