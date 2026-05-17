package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.FrameData;

public class CmdAddFrame implements ICommand {
    private final GraphController mController;
    private final FrameData mFrameData;

    public CmdAddFrame(GraphController controller, FrameData frameData) {
        this.mController = controller;
        this.mFrameData = frameData;
    }

    @Override
    public void execute() {
        mController.addFrame(mFrameData);
    }

    @Override
    public void undo() {
        mController.removeFrame(mFrameData.id);
    }
}