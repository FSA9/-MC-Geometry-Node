package com.mine.geometry_node.client.feature.graph.edit.commands.connection;

import com.mine.geometry_node.client.feature.graph.edit.ICommand;
import com.mine.geometry_node.client.feature.graph.edit.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;

import java.util.List;
import java.util.Map;

public class CmdConnect implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String outNodeId, outPortId;
    private final String inNodeId, inPortId;

    // The destination input may displace an existing source.
    private String mDisplacedInboundNodeId;
    private String mDisplacedInboundPortId;

    public CmdConnect(GraphController controller, NodeGraph graph, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.mGraph = graph;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;
    }

    @Override
    public boolean canExecute() {
        return mController != null
                && mController.canConnectPorts(outNodeId, outPortId, inNodeId, inPortId);
    }

    private boolean isFlowConnection() {
        if (outPortId.startsWith("flow_") || inPortId.startsWith("flow_")) return true;
        PortType outType = getPortType(outNodeId, outPortId, false);
        PortType inType = getPortType(inNodeId, inPortId, true);
        if ((outType != null && outType.isFlow()) || (inType != null && inType.isFlow())) return true;
        return (isBoundaryVirtualPort(outNodeId) && inType != null && inType.isFlow())
                || (isBoundaryVirtualPort(inNodeId) && outType != null && outType.isFlow());
    }

    private PortType getPortType(String nodeId, String portId, boolean inputSide) {
        return mController.getResolvedPortType(nodeId, portId, inputSide);
    }

    private boolean isBoundaryVirtualPort(String nodeId) {
        if (mGraph == null || nodeId == null) return false;
        NodeData node = mGraph.getNode(nodeId);
        return node != null && (node.isGroupInputNode() || node.isGroupOutputNode());
    }

    /**
     * 遍历图，寻找是否已有其它输出端口指向了我们的目标输入端口
     */
    private void snapshotDisplacedConnections() {
        mDisplacedInboundNodeId = null;
        mDisplacedInboundPortId = null;
        if (mGraph == null) return;
        boolean executionFlow = isFlowConnection();
        if (executionFlow && isRerouteInput(inNodeId, inPortId)) return;

        for (NodeData node : mGraph.nodes.values()) {
            if (executionFlow) {
                if (node.execOutputs != null) {
                    for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                        Connection link = entry.getValue();
                        if (link.targetNodeId().equals(inNodeId) && link.targetPortName().equals(inPortId)) {
                            mDisplacedInboundNodeId = node.id;
                            mDisplacedInboundPortId = entry.getKey();
                            return;
                        }
                    }
                }
            } else {
                if (node.outputs != null) {
                    for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                        for (Connection link : entry.getValue()) {
                            if (link.targetNodeId().equals(inNodeId) && link.targetPortName().equals(inPortId)) {
                                mDisplacedInboundNodeId = node.id;
                                mDisplacedInboundPortId = entry.getKey();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRerouteInput(String nodeId, String portId) {
        if (mGraph == null || nodeId == null || portId == null) return false;
        NodeData node = mGraph.getNode(nodeId);
        return RerouteNodeSupport.isReroute(node) && RerouteNodeSupport.INPUT_PORT.equals(portId);
    }

    @Override
    public void execute() {
        if (!canExecute()) {
            return;
        }
        snapshotDisplacedConnections();
        // 1. 如果有旧连线，先断开它 (打断旧关系)
        if (mDisplacedInboundNodeId != null && mDisplacedInboundPortId != null) {
            if (isFlowConnection()) {
                mController.removeExecutionConnection(mDisplacedInboundNodeId, mDisplacedInboundPortId);
            } else {
                mController.removeConnection(
                        mDisplacedInboundNodeId, mDisplacedInboundPortId, inNodeId, inPortId);
            }
        }

        // 2. 连接新连线 [核心修改] 增加 inPortId 参数
        if (isFlowConnection()) {
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId, inPortId);
        } else {
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

}
