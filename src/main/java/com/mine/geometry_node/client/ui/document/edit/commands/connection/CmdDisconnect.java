package com.mine.geometry_node.client.ui.document.edit.commands.connection;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;

public class CmdDisconnect implements ICommand {
    private final GraphController mController;
    private final String outNodeId;
    private final String outPortId;
    private final String inNodeId;
    private final String inPortId;
    private final boolean mFlowConnection;

    public CmdDisconnect(GraphController controller, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;
        this.mFlowConnection = detectFlowConnection();
    }

    private boolean detectFlowConnection() {
        NodeData outNode = mController.getCurrentGraph().getNode(outNodeId);
        Connection storedExecution = outNode != null ? outNode.execOutputs.get(outPortId) : null;
        if (storedExecution != null
                && inNodeId.equals(storedExecution.targetNodeId())
                && inPortId.equals(storedExecution.targetPortName())) {
            return true;
        }
        if (outPortId.startsWith("flow_") || inPortId.startsWith("flow_")) return true;
        PortType outType = mController.getResolvedPortType(outNodeId, outPortId, false);
        PortType inType = mController.getResolvedPortType(inNodeId, inPortId, true);
        return (outType != null && outType.isFlow()) || (inType != null && inType.isFlow());
    }

    @Override
    public void execute() {
        if (mFlowConnection) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

}
