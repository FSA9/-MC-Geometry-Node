package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.FrameData;
import com.mine.geometry_node.core.node.NodeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdRemoveFrames implements ICommand {
    private final GraphController mController;
    private final List<FrameData> mRemovedFrames = new ArrayList<>();

    // 记忆被浅删除前，子元素原先隶属于哪个被删的框，用于 Undo 还原
    private final Map<String, String> mNodeToOldParent = new HashMap<>();
    private final Map<String, String> mFrameToOldParent = new HashMap<>();

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
                mNodeToOldParent.put(node.id, node.parentFrame);
            }
        }
        for (FrameData frame : controller.getContext().getGraph().frames.values()) {
            if (frame.parentFrame != null && targetIds.contains(frame.parentFrame)) {
                mFrameToOldParent.put(frame.id, frame.parentFrame);
            }
        }
    }

    @Override
    public void execute() {
        // 1. 斩断羁绊：让原本属于这些框的子元素变成孤儿 (浅删除的核心)
        for (String childNodeId : mNodeToOldParent.keySet()) {
            mController.setElementParentFrame(childNodeId, true, null);
        }
        for (String childFrameId : mFrameToOldParent.keySet()) {
            mController.setElementParentFrame(childFrameId, false, null);
        }
        // 2. 移除图框
        for (FrameData frame : mRemovedFrames) {
            mController.removeFrame(frame.id);
        }
    }

    @Override
    public void undo() {
        for (FrameData frame : mRemovedFrames) {
            mController.addFrame(frame);
        }
        for (Map.Entry<String, String> entry : mNodeToOldParent.entrySet()) {
            mController.setElementParentFrame(entry.getKey(), true, entry.getValue());
        }
        for (Map.Entry<String, String> entry : mFrameToOldParent.entrySet()) {
            mController.setElementParentFrame(entry.getKey(), false, entry.getValue());
        }
    }
}