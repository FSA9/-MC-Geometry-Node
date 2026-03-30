package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;

public class CmdDisconnect implements ICommand {
    private final GraphController mController;
    private final String outNodeId, outPortId;
    private final String inNodeId, inPortId;

    public CmdDisconnect(GraphController controller, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.outNodeId = outNodeId; this.outPortId = outPortId;
        this.inNodeId = inNodeId; this.inPortId = inPortId;
    }

    private boolean isExecutionFlow() {
        return outPortId.startsWith("flow_") || inPortId.startsWith("flow_");
    }

    @Override
    public void execute() {
        if (isExecutionFlow()) {
            // 执行流
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            // 数据流
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    @Override
    public void undo() {
        if (isExecutionFlow()) {
            // 执行流
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId);
        } else {
            // 数据流
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }
}