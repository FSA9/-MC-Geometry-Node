package com.mine.geometry_node.client.feature.graph.edit.commands.layout;

import com.mine.geometry_node.client.feature.graph.edit.ICommand;
import com.mine.geometry_node.client.feature.graph.edit.GraphController;

import java.util.List;

public class CmdChangeParent implements ICommand {
    private final GraphController mController;
    private final List<String> mElementIds; // 支持节点 ID，也支持子图框 ID
    private final boolean mIsNode;
    private final String mNewParentFrameId;

    public CmdChangeParent(GraphController controller, List<String> elementIds, boolean isNode, String newParentFrameId) {
        this.mController = controller;
        this.mElementIds = List.copyOf(elementIds);
        this.mIsNode = isNode;
        this.mNewParentFrameId = newParentFrameId;
    }

    @Override
    public void execute() {
        for (String id : mElementIds) {
            mController.setElementParentFrame(id, mIsNode, mNewParentFrameId);
        }
    }

}
