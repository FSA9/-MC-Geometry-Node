package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.FrameData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdChangeParent implements ICommand {
    private final GraphController mController;
    private final List<String> mElementIds; // 支持节点 ID，也支持子图框 ID
    private final boolean mIsNode;
    private final String mNewParentFrameId;

    private final Map<String, String> mOldParents = new HashMap<>();

    public CmdChangeParent(GraphController controller, List<String> elementIds, boolean isNode, String newParentFrameId) {
        this.mController = controller;
        this.mElementIds = elementIds;
        this.mIsNode = isNode;
        this.mNewParentFrameId = newParentFrameId;

        // 记录修改前的 parentFrame
        for (String id : elementIds) {
            if (isNode) {
                NodeData node = controller.getContext().getCurrentGraph().getNode(id);
                if (node != null) mOldParents.put(id, node.parentFrame);
            } else {
                FrameData frame = controller.getContext().getGraph().getFrame(id);
                if (frame != null) mOldParents.put(id, frame.parentFrame);
            }
        }
    }

    @Override
    public void execute() {
        for (String id : mElementIds) {
            mController.setElementParentFrame(id, mIsNode, mNewParentFrameId);
        }
    }

    @Override
    public void undo() {
        for (String id : mElementIds) {
            String oldParent = mOldParents.get(id);
            mController.setElementParentFrame(id, mIsNode, oldParent);
        }
    }
}
