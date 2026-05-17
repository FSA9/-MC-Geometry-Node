package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.FrameData;
import com.mine.geometry_node.core.node.NodeData;

import java.util.ArrayList;
import java.util.List;

public class CmdRemoveFrame implements ICommand {
    private final GraphController mController;
    private final FrameData mFrameData;

    // 记录删除图框前，里面到底有哪些子节点/子图框，方便撤销时重新挂回去
    private final List<String> mChildNodes = new ArrayList<>();
    private final List<String> mChildFrames = new ArrayList<>();

    public CmdRemoveFrame(GraphController controller, String frameId) {
        this.mController = controller;
        this.mFrameData = controller.getContext().getGraph().getFrame(frameId);

        if (mFrameData != null) {
            for (NodeData node : controller.getContext().getGraph().nodes.values()) {
                if (frameId.equals(node.parentFrame)) mChildNodes.add(node.id);
            }
            for (FrameData frame : controller.getContext().getGraph().frames.values()) {
                if (frameId.equals(frame.parentFrame)) mChildFrames.add(frame.id);
            }
        }
    }

    @Override
    public void execute() {
        if (mFrameData == null) return;

        // 1. 解除所有子节点的关联 (触发外层更新)
        for (String childId : mChildNodes) {
            mController.setElementParentFrame(childId, true, null);
        }
        for (String childId : mChildFrames) {
            mController.setElementParentFrame(childId, false, null);
        }

        // 2. 删掉图框
        mController.removeFrame(mFrameData.id);
    }

    @Override
    public void undo() {
        if (mFrameData == null) return;

        // 1. 恢复图框
        mController.addFrame(mFrameData);

        // 2. 把子节点重新挂回来
        for (String childId : mChildNodes) {
            mController.setElementParentFrame(childId, true, mFrameData.id);
        }
        for (String childId : mChildFrames) {
            mController.setElementParentFrame(childId, false, mFrameData.id);
        }
    }
}