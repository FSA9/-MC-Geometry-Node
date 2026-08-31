package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.ArrayList;
import java.util.List;

public class CmdDisconnect implements ICommand {
    private final GraphController mController;
    private final String outNodeId;
    private final String outPortId;
    private final String inNodeId;
    private final String inPortId;
    private final List<ExternalConnectionBackup> mExternalConnections = new ArrayList<>();

    private record ExternalConnectionBackup(String boundaryNodeId, GraphController.ScopedConnectionSnapshot snapshot) {}

    public CmdDisconnect(GraphController controller, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;
        backupExternalConnections();
    }

    private boolean isFlowConnection() {
        if (outPortId.startsWith("flow_") || inPortId.startsWith("flow_")) return true;
        PortType outType = mController.getResolvedPortType(outNodeId, outPortId, false);
        PortType inType = mController.getResolvedPortType(inNodeId, inPortId, true);
        return (outType != null && outType.isFlow()) || (inType != null && inType.isFlow());
    }

    private void backupExternalConnections() {
        backupExternalConnections(outNodeId, outPortId);
        backupExternalConnections(inNodeId, inPortId);
    }

    private void backupExternalConnections(String boundaryNodeId, String portId) {
        for (GraphController.ScopedConnectionSnapshot snapshot : mController.getBoundaryPortExternalConnectionSnapshots(boundaryNodeId, portId)) {
            mExternalConnections.add(new ExternalConnectionBackup(boundaryNodeId, snapshot));
        }
    }

    @Override
    public void execute() {
        if (isFlowConnection()) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    @Override
    public void undo() {
        // 撤销时：恢复连线
        if (isFlowConnection()) {
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId, inPortId);
        } else {
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
        for (ExternalConnectionBackup backup : mExternalConnections) {
            mController.restoreGroupVirtualPortConnection(backup.boundaryNodeId(), backup.snapshot());
        }
    }
}
