package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeConnections;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;

import java.util.List;
import java.util.Map;

public class CmdConnect implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String outNodeId, outPortId;
    private final String inNodeId, inPortId;

    // 目标输入和执行流输出都可能各自替换一条已有连接。
    private String mDisplacedInboundNodeId;
    private String mDisplacedInboundPortId;
    private Connection mDisplacedExecutionOutput;
    private Connection mDisplacedBehaviorOutput;
    private BehaviorTreeConnections.ParentConnection mDisplacedBehaviorParent;

    public CmdConnect(GraphController controller, NodeGraph graph, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.mGraph = graph;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;

        // 在命令创建时，立即快照当前的旧连接状态
        snapshotDisplacedConnections();
    }

    @Override
    public boolean canExecute() {
        return mController != null
                && mController.canConnectPorts(outNodeId, outPortId, inNodeId, inPortId);
    }

    private boolean isFlowConnection() {
        if (isBehaviorConnection()) return false;
        if (outPortId.startsWith("flow_") || inPortId.startsWith("flow_")) return true;
        PortType outType = getPortType(outNodeId, outPortId, false);
        PortType inType = getPortType(inNodeId, inPortId, true);
        if ((outType != null && outType.isFlow()) || (inType != null && inType.isFlow())) return true;
        return (isBoundaryVirtualPort(outNodeId) && inType != null && inType.isFlow())
                || (isBoundaryVirtualPort(inNodeId) && outType != null && outType.isFlow());
    }

    private boolean isBehaviorConnection() {
        return mController != null && mController.isBehaviorStructureConnection(
                outNodeId, outPortId, inNodeId, inPortId);
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
        if (mGraph == null) return;
        if (isBehaviorConnection()) {
            mDisplacedBehaviorOutput = mController.getBehaviorConnection(outNodeId, outPortId);
            mDisplacedBehaviorParent = mController.getBehaviorParent(inNodeId);
            return;
        }
        boolean executionFlow = isFlowConnection();
        if (executionFlow) {
            NodeData outNode = mGraph.getNode(outNodeId);
            Connection existingOutput = outNode != null ? outNode.execOutputs.get(outPortId) : null;
            if (existingOutput != null
                    && (!existingOutput.targetNodeId().equals(inNodeId)
                    || !existingOutput.targetPortName().equals(inPortId))) {
                mDisplacedExecutionOutput = existingOutput;
            }
            if (isRerouteInput(inNodeId, inPortId)) return;
        }

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
        if (isBehaviorConnection()) {
            mController.addBehaviorConnection(outNodeId, outPortId, inNodeId, inPortId);
            return;
        }

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

    @Override
    public void undo() {
        if (isBehaviorConnection()) {
            mController.removeBehaviorConnection(outNodeId, outPortId, inNodeId, inPortId);
            if (mDisplacedBehaviorOutput != null) {
                mController.addBehaviorConnection(outNodeId, outPortId,
                        mDisplacedBehaviorOutput.targetNodeId(),
                        mDisplacedBehaviorOutput.targetPortName());
            }
            if (mDisplacedBehaviorParent != null
                    && (mDisplacedBehaviorOutput == null
                    || !mDisplacedBehaviorParent.parentId().equals(outNodeId)
                    || !mDisplacedBehaviorParent.portId().equals(outPortId))) {
                mController.addBehaviorConnection(mDisplacedBehaviorParent.parentId(),
                        mDisplacedBehaviorParent.portId(), inNodeId,
                        mDisplacedBehaviorParent.connection().targetPortName());
            }
            return;
        }
        // 1. 撤销新连线
        if (isFlowConnection()) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }

        // 2. 恢复该执行流输出原先指向的目标。
        if (mDisplacedExecutionOutput != null) {
            mController.addExecutionConnection(
                    outNodeId,
                    outPortId,
                    mDisplacedExecutionOutput.targetNodeId(),
                    mDisplacedExecutionOutput.targetPortName()
            );
        }

        // 3. 恢复目标输入原先的来源。
        if (mDisplacedInboundNodeId != null && mDisplacedInboundPortId != null) {
            if (isFlowConnection()) {
                mController.addExecutionConnection(
                        mDisplacedInboundNodeId, mDisplacedInboundPortId, inNodeId, inPortId);
            } else {
                mController.addConnection(
                        mDisplacedInboundNodeId, mDisplacedInboundPortId, inNodeId, inPortId);
            }
        }
    }
}
