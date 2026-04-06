package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.Viewport.GraphController;

public class CmdConnect implements ICommand {
    private final GraphController mController;
    private final String outNodeId, outPortId;
    private final String inNodeId, inPortId;

    public CmdConnect(GraphController controller, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.outNodeId = outNodeId; this.outPortId = outPortId;
        this.inNodeId = inNodeId; this.inPortId = inPortId;
    }

    // 执行流判断器
    private boolean isExecutionFlow() {
        return outPortId.startsWith("flow_") || inPortId.startsWith("flow_");
    }

    @Override
    public void execute() {
        if (isExecutionFlow()) {
            // 执行流
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId);
        } else {
            // 数据流
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    @Override
    public void undo() {
        if (isExecutionFlow()) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }
}