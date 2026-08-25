package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CmdGroupIntoFrame implements ICommand {
    private final GraphController mController;
    private final FrameData mNewFrame;
    private final List<String> mNodeIds;
    private final Map<String, String> mOldParents = new HashMap<>();

    public CmdGroupIntoFrame(GraphController controller, List<String> selectedNodeIds) {
        this.mController = controller;
        this.mNodeIds = List.copyOf(selectedNodeIds);

        // 生成一个新的图框 (位置无所谓，随后会被自动重算覆盖)
        this.mNewFrame = new FrameData(UUID.randomUUID().toString(), 0, 0);
        this.mNewFrame.title = "New Frame";

        // 记录这些节点原来的 parentFrame，方便撤销
        for (String id : mNodeIds) {
            NodeData node = controller.getContext().getCurrentGraph().getNode(id);
            if (node != null) {
                mOldParents.put(id, node.parentFrame);
            }
        }
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

    @Override
    public void undo() {
        // 1. 先把节点退回给原来的框
        for (String id : mNodeIds) {
            mController.setElementParentFrame(id, true, mOldParents.get(id));
        }

        // 2. 删除这个新建的框
        mController.removeFrame(mNewFrame.id);
    }
}
