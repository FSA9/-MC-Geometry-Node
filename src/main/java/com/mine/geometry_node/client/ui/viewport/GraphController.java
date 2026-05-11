package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphController {
    private final EditorContext mContext;

    public GraphController(EditorContext context) {
        this.mContext = context;
    }

    public void addNode(NodeData node) {
        mContext.getGraph().addNode(node);
        mContext.notifyNodeAdded(node);
    }

    public void removeNode(String nodeId) {
        mContext.getGraph().removeNode(nodeId);
        mContext.notifyNodeRemoved(nodeId);
    }

    public float[] getNodePosition(String nodeId) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        return node != null ? node.uiPos : null;
    }

    public void setNodePosition(String nodeId, float x, float y) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node != null) {
            node.setPosition(x, y);
            mContext.notifyNodeMoved(nodeId, x, y);
        }
    }

    public void addConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.inputs.remove(inPortId);
                inNode.setInputConnected(inPortId, true);
            }

            mContext.notifyConnectionAdded(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void removeConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.removeDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.setInputConnected(inPortId, false);
            }

            mContext.notifyConnectionRemoved(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void addExecutionConnection(String outNodeId, String outPortId, String inNodeId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addExecutionConnection(outPortId, inNodeId);
            mContext.notifyExecutionConnectionAdded(outNodeId, outPortId, inNodeId);
        }
    }

    public void removeExecutionConnection(String outNodeId, String outPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            String inNodeId = outNode.execution.get(outPortId);
            if (inNodeId != null) {
                outNode.removeExecutionConnection(outPortId);
                mContext.notifyExecutionConnectionRemoved(outNodeId, outPortId, inNodeId);
            }
        }
    }

    // 【核心重构】吸收了原 setNodeProperty 的全部清理逻辑
    public void setNodeInputValue(String nodeId, String portId, Object value) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        // 1. 更新节点输入值 (不论是物理连线端口还是纯属性端口，统统存这里)
        if (value == null) {
            node.inputs.remove(portId);
        } else {
            node.inputs.put(portId, value);
        }

        // 2. 获取更新属性后的新 NodeDef (防止这个输入值是动态分支数量控制参数)
        NodeDef newDef = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (newDef == null) return;

        // 3. 提取新定义中所有【仍然合法】的端口 ID
        Set<String> validInputs = new HashSet<>();
        Set<String> validOutputs = new HashSet<>();
        for (PortRow row : newDef.rows()) {
            if (row.leftPort() != null) validInputs.add(row.leftPort().id());
            if (row.rightPort() != null) validOutputs.add(row.rightPort().id());
        }

        // 4. 清理当前节点失效的【输出】连线
        List<String> invalidOutPorts = new ArrayList<>();
        for (String outPort : node.outputs.keySet()) {
            if (!validOutputs.contains(outPort)) {
                invalidOutPorts.add(outPort);
            }
        }
        for (String invalidOut : invalidOutPorts) {
            List<Connection> links = new ArrayList<>(node.getConnections(invalidOut));
            for (Connection link : links) {
                removeConnection(nodeId, invalidOut, link.targetNodeId(), link.targetPortName());
            }
        }

        // 5. 清理当前节点失效的【执行流】连线
        List<String> invalidExecPorts = new ArrayList<>();
        for (String execPort : node.execution.keySet()) {
            if (!validOutputs.contains(execPort)) {
                invalidExecPorts.add(execPort);
            }
        }
        for (String invalidExec : invalidExecPorts) {
            removeExecutionConnection(nodeId, invalidExec);
        }

        // 6. 清理其他节点连接到当前节点失效【输入】端口的连线
        for (NodeData otherNode : mContext.getGraph().nodes.values()) {
            for (String otherOutPort : new ArrayList<>(otherNode.outputs.keySet())) {
                List<Connection> links = new ArrayList<>(otherNode.getConnections(otherOutPort));
                for (Connection link : links) {
                    if (link.targetNodeId().equals(nodeId) && !validInputs.contains(link.targetPortName())) {
                        removeConnection(otherNode.id, otherOutPort, nodeId, link.targetPortName());
                    }
                }
            }
        }

        // 7. 通知 viewport 重新构建该节点的 UI
        mContext.notifyNodeStructureChanged(node);
    }

    public boolean isInputPortConnected(String targetNodeId, String targetPortId) {
        NodeData node = mContext.getGraph().getNode(targetNodeId);
        if (node == null) return false;
        return node.connectedInputs.contains(targetPortId);
    }

    // 【核心清理】移除所有对 properties 的操作
    public void removeDynamicBranch(String nodeId, String propertyKey, int removeIndex, int totalCount) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        if (removeIndex < 1 || removeIndex > totalCount) return;

        for (int i = removeIndex; i < totalCount; i++) {
            String oldSuffix = "_" + (i + 1);
            String newSuffix = "_" + i;
            shiftMapData(node.inputs, oldSuffix, newSuffix);
            shiftMapData(node.outputs, oldSuffix, newSuffix);
            shiftMapData(node.execution, oldSuffix, newSuffix);
            shiftConnections(nodeId, oldSuffix, newSuffix);
        }

        String lastSuffix = "_" + totalCount;
        node.inputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.outputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.execution.keySet().removeIf(k -> k.endsWith(lastSuffix));
        shiftConnections(nodeId, lastSuffix, null);

        // 使用统一的 setNodeInputValue，它会自动触发 UI 刷新和清理
        setNodeInputValue(nodeId, propertyKey, totalCount - 1);
    }

    private <V> void shiftMapData(java.util.Map<String, V> map, String oldSuffix, String newSuffix) {
        map.keySet().removeIf(k -> k.endsWith(newSuffix));

        java.util.Map<String, V> toMove = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, V> entry : map.entrySet()) {
            if (entry.getKey().endsWith(oldSuffix)) {
                String newKey = entry.getKey().substring(0, entry.getKey().length() - oldSuffix.length()) + newSuffix;
                toMove.put(newKey, entry.getValue());
            }
        }

        map.keySet().removeIf(k -> k.endsWith(oldSuffix));
        map.putAll(toMove);
    }

    private void shiftConnections(String targetNodeId, String oldSuffix, String newSuffix) {
        List<Runnable> connectionUpdates = new ArrayList<>();

        for (NodeData otherNode : mContext.getGraph().nodes.values()) {
            for (String outPort : otherNode.outputs.keySet()) {
                for (Connection link : otherNode.getConnections(outPort)) {
                    if (link.targetNodeId().equals(targetNodeId) && link.targetPortName().endsWith(oldSuffix)) {
                        connectionUpdates.add(() -> {
                            removeConnection(otherNode.id, outPort, targetNodeId, link.targetPortName());
                            if (newSuffix != null) {
                                String newPortName = link.targetPortName().substring(0, link.targetPortName().length() - oldSuffix.length()) + newSuffix;
                                addConnection(otherNode.id, outPort, targetNodeId, newPortName);
                            }
                        });
                    }
                }
            }
        }

        for (Runnable r : connectionUpdates) {
            r.run();
        }
    }
}