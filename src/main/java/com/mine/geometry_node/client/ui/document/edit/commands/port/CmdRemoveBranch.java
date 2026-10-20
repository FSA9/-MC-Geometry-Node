package com.mine.geometry_node.client.ui.document.edit.commands.port;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;

public class CmdRemoveBranch implements ICommand {
    private final GraphController mController;
    private final String mNodeId;
    private final String mPropertyKey; // 现在这个 Key 对应 inputs 里的键
    private final int mOldCount;

    private final int mRemoveIndex;

    public CmdRemoveBranch(GraphController controller, String nodeId, String propertyKey, int currentCount, int removeIndex) {
        this.mController = controller;
        this.mNodeId = nodeId;
        this.mPropertyKey = propertyKey;
        this.mOldCount = currentCount;
        this.mRemoveIndex = removeIndex;
    }

    @Override
    public void execute() {
        mController.removeDynamicBranch(mNodeId, mPropertyKey, mRemoveIndex, mOldCount);
    }

}
