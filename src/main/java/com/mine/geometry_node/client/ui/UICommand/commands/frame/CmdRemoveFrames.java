package com.mine.geometry_node.client.ui.UICommand.commands.frame;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CmdRemoveFrames implements ICommand {
    private final GraphController mController;
    private final List<FrameData> mRemovedFrames = new ArrayList<>();

    // Children detached by shallow deletion.
    private final Set<String> mChildNodeIds = new HashSet<>();
    private final Set<String> mChildFrameIds = new HashSet<>();

    public CmdRemoveFrames(GraphController controller, List<String> frameIds) {
        this.mController = controller;

        for (String id : frameIds) {
            FrameData fd = controller.getContext().getGraph().getFrame(id);
            if (fd != null) mRemovedFrames.add(fd);
        }

        List<String> targetIds = new ArrayList<>();
        for (FrameData fd : mRemovedFrames) targetIds.add(fd.id);

        for (NodeData node : controller.getContext().getGraph().nodes.values()) {
            if (node.parentFrame != null && targetIds.contains(node.parentFrame)) {
                mChildNodeIds.add(node.id);
            }
        }
        for (FrameData frame : controller.getContext().getGraph().frames.values()) {
            if (frame.parentFrame != null && targetIds.contains(frame.parentFrame)) {
                mChildFrameIds.add(frame.id);
            }
        }
    }

    @Override
    public void execute() {
        // 1. 斩断羁绊：让原本属于这些框的子元素变成孤儿 (浅删除的核心)
        for (String childNodeId : mChildNodeIds) {
            mController.setElementParentFrame(childNodeId, true, null);
        }
        for (String childFrameId : mChildFrameIds) {
            mController.setElementParentFrame(childFrameId, false, null);
        }
        // 2. 移除图框
        for (FrameData frame : mRemovedFrames) {
            mController.removeFrame(frame.id);
        }
    }

}
