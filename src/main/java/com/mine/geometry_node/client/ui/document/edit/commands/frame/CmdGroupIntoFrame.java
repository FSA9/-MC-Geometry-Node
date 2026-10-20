package com.mine.geometry_node.client.ui.document.edit.commands.frame;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;

import java.util.List;
import java.util.UUID;

public class CmdGroupIntoFrame implements ICommand {
    private final GraphController mController;
    private final FrameData mNewFrame;
    private final List<String> mNodeIds;

    public CmdGroupIntoFrame(GraphController controller, List<String> selectedNodeIds) {
        this.mController = controller;
        this.mNodeIds = List.copyOf(selectedNodeIds);

        // 生成一个新的图框 (位置无所谓，随后会被自动重算覆盖)
        this.mNewFrame = new FrameData(UUID.randomUUID().toString(), 0, 0);
        this.mNewFrame.title = "New Frame";
    }

    @Override
    public void execute() {
        // 1. 先把空框加进去
        mController.addFrame(mNewFrame);

        // 2. 将选中的节点全部并入新框
        for (String id : mNodeIds) {
            mController.setElementParentFrame(id, true, mNewFrame.id);
        }

        // (注：setElementParentFrame 内部会自动调用 updateFrameBounds 撑大这个新框)
    }

}
