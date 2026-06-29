package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.nodes.special.RerouteNode;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;

public class CmdInsertRerouteOnConnection implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String mOutNodeId;
    private final String mOutPortId;
    private final String mInNodeId;
    private final String mInPortId;
    private final boolean mExecution;
    private final NodeData mRerouteNode;

    public CmdInsertRerouteOnConnection(GraphController controller,
                                        NodeGraph graph,
                                        String outNodeId,
                                        String outPortId,
                                        String inNodeId,
                                        String inPortId,
                                        boolean execution,
                                        String rerouteNodeId,
                                        float uiX,
                                        float uiY) {
        this.mController = controller;
        this.mGraph = graph;
        this.mOutNodeId = outNodeId;
        this.mOutPortId = outPortId;
        this.mInNodeId = inNodeId;
        this.mInPortId = inPortId;
        this.mExecution = execution;
        this.mRerouteNode = new NodeData(rerouteNodeId, RerouteNode.TYPE_ID, uiX, uiY);
    }

    public boolean canExecute() {
        return mController != null
                && mGraph != null
                && mGraph.getNode(mOutNodeId) != null
                && mGraph.getNode(mInNodeId) != null
                && mGraph.getNode(mRerouteNode.id) == null
                && hasOriginalConnection();
    }

    @Override
    public void execute() {
        if (!canExecute()) return;

        disconnectOriginal();
        mController.addNode(mRerouteNode);
        connectThroughReroute();
    }

    @Override
    public void undo() {
        disconnectThroughReroute();
        mController.removeNode(mRerouteNode.id);
        reconnectOriginal();
    }

    private void disconnectOriginal() {
        if (mExecution) {
            mController.removeExecutionConnection(mOutNodeId, mOutPortId);
        } else {
            mController.removeConnection(mOutNodeId, mOutPortId, mInNodeId, mInPortId);
        }
    }

    private boolean hasOriginalConnection() {
        NodeData outNode = mGraph.getNode(mOutNodeId);
        if (outNode == null) return false;

        if (mExecution) {
            Connection connection = outNode.execOutputs != null ? outNode.execOutputs.get(mOutPortId) : null;
            return connection != null
                    && mInNodeId.equals(connection.targetNodeId())
                    && mInPortId.equals(connection.targetPortName());
        }

        if (outNode.outputs == null || outNode.outputs.get(mOutPortId) == null) return false;
        for (Connection connection : outNode.outputs.get(mOutPortId)) {
            if (connection != null
                    && mInNodeId.equals(connection.targetNodeId())
                    && mInPortId.equals(connection.targetPortName())) {
                return true;
            }
        }
        return false;
    }

    private void reconnectOriginal() {
        if (mExecution) {
            mController.addExecutionConnection(mOutNodeId, mOutPortId, mInNodeId, mInPortId);
        } else {
            mController.addConnection(mOutNodeId, mOutPortId, mInNodeId, mInPortId);
        }
    }

    private void connectThroughReroute() {
        if (mExecution) {
            mController.addExecutionConnection(mOutNodeId, mOutPortId, mRerouteNode.id, RerouteNodeSupport.INPUT_PORT);
            mController.addExecutionConnection(mRerouteNode.id, RerouteNodeSupport.OUTPUT_PORT, mInNodeId, mInPortId);
        } else {
            mController.addConnection(mOutNodeId, mOutPortId, mRerouteNode.id, RerouteNodeSupport.INPUT_PORT);
            mController.addConnection(mRerouteNode.id, RerouteNodeSupport.OUTPUT_PORT, mInNodeId, mInPortId);
        }
    }

    private void disconnectThroughReroute() {
        if (mExecution) {
            mController.removeExecutionConnection(mRerouteNode.id, RerouteNodeSupport.OUTPUT_PORT);
            mController.removeExecutionConnection(mOutNodeId, mOutPortId);
        } else {
            mController.removeConnection(mRerouteNode.id, RerouteNodeSupport.OUTPUT_PORT, mInNodeId, mInPortId);
            mController.removeConnection(mOutNodeId, mOutPortId, mRerouteNode.id, RerouteNodeSupport.INPUT_PORT);
        }
    }
}
