package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;

public class CmdDisconnect implements ICommand {
    private final GraphController mController;
    private final String outNodeId;
    private final String outPortId;
    private final String inNodeId;
    private final String inPortId;

    public CmdDisconnect(GraphController controller, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;
    }

    private boolean isExecutionFlow() {
        return outPortId.startsWith("flow_") || inPortId.startsWith("flow_");
    }

    @Override
    public void execute() {
        if (isExecutionFlow()) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    @Override
    public void undo() {
        // 撤销时：恢复连线
        if (isExecutionFlow()) {
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId, inPortId);
        } else {
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }
}