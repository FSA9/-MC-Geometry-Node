package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.List;
import java.util.Map;

public class CmdConnect implements ICommand {
    private final GraphController mController;
    private final NodeGraph mGraph;
    private final String outNodeId, outPortId;
    private final String inNodeId, inPortId;

    // 状态记录：记录被顶替掉的旧连线，用于撤销恢复
    private String oldOutNodeId = null;
    private String oldOutPortId = null;

    public CmdConnect(GraphController controller, NodeGraph graph, String outNodeId, String outPortId, String inNodeId, String inPortId) {
        this.mController = controller;
        this.mGraph = graph;
        this.outNodeId = outNodeId;
        this.outPortId = outPortId;
        this.inNodeId = inNodeId;
        this.inPortId = inPortId;

        // 在命令创建时，立即快照当前的旧连接状态
        findAndRecordOldConnection();
    }

    private boolean isExecutionFlow() {
        // [此处保持你的硬编码逻辑即可，或者通过 PortType 动态判断更佳，但目前用着没问题]
        return outPortId.startsWith("flow_") || inPortId.startsWith("flow_");
    }

    /**
     * 遍历图，寻找是否已有其它输出端口指向了我们的目标输入端口
     */
    private void findAndRecordOldConnection() {
        if (mGraph == null) return;

        for (NodeData node : mGraph.nodes.values()) {
            if (isExecutionFlow()) {
                // [核心修改] 遍历 execOutputs，现在它是 Map<String, Connection>
                if (node.execOutputs != null) {
                    for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                        Connection link = entry.getValue();
                        if (link.targetNodeId().equals(inNodeId) && link.targetPortName().equals(inPortId)) {
                            oldOutNodeId = node.id;
                            oldOutPortId = entry.getKey();
                            return;
                        }
                    }
                }
            } else {
                if (node.outputs != null) {
                    for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                        for (Connection link : entry.getValue()) {
                            if (link.targetNodeId().equals(inNodeId) && link.targetPortName().equals(inPortId)) {
                                oldOutNodeId = node.id;
                                oldOutPortId = entry.getKey();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void execute() {
        // 1. 如果有旧连线，先断开它 (打断旧关系)
        if (oldOutNodeId != null && oldOutPortId != null) {
            if (isExecutionFlow()) {
                mController.removeExecutionConnection(oldOutNodeId, oldOutPortId);
            } else {
                mController.removeConnection(oldOutNodeId, oldOutPortId, inNodeId, inPortId);
            }
        }

        // 2. 连接新连线 [核心修改] 增加 inPortId 参数
        if (isExecutionFlow()) {
            mController.addExecutionConnection(outNodeId, outPortId, inNodeId, inPortId);
        } else {
            mController.addConnection(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    @Override
    public void undo() {
        // 1. 撤销新连线
        if (isExecutionFlow()) {
            mController.removeExecutionConnection(outNodeId, outPortId);
        } else {
            mController.removeConnection(outNodeId, outPortId, inNodeId, inPortId);
        }

        // 2. 恢复旧连线 [核心修改] 增加 inPortId 参数
        if (oldOutNodeId != null && oldOutPortId != null) {
            if (isExecutionFlow()) {
                mController.addExecutionConnection(oldOutNodeId, oldOutPortId, inNodeId, inPortId);
            } else {
                mController.addConnection(oldOutNodeId, oldOutPortId, inNodeId, inPortId);
            }
        }
    }
}