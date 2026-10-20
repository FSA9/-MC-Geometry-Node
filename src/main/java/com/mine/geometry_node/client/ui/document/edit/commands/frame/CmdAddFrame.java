package com.mine.geometry_node.client.ui.document.edit.commands.frame;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;

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

}
