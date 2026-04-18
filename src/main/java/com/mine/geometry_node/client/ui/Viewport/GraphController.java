package com.mine.geometry_node.client.ui.Viewport;

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

    // 设置节点位置并通知 UI
    public void setNodePosition(String nodeId, float x, float y) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node != null) {
            node.setPosition(x, y);
            mContext.notifyNodeMoved(nodeId, x, y); // 需要在 EditorContext 补充此事件
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

            // 3. 数据全部就绪，最后通知 UI 刷新
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

    public void setNodeProperty(String nodeId, String key, Object value) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        // 1. 更新节点属性
        if (value == null) {
            node.properties.remove(key);
        } else {
            node.properties.put(key, value);
        }

        // 2. 获取更新属性后的新 NodeDef
        NodeDef newDef = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (newDef == null) return;

        // 3. 提取新定义中所有【仍然合法】的端口 ID
        Set<String> validInputs = new HashSet<>();
        Set<String> validOutputs = new HashSet<>();
        for (PortRow row : newDef.rows()) {
            if (row.leftPort() != null) validInputs.add(row.leftPort().id());
            if (row.rightPort() != null) validOutputs.add(row.rightPort().id());
        }

        // 4. 清理当前节点失效的【输出】连线 (右侧端口减少了)
        List<String> invalidOutPorts = new ArrayList<>();
        for (String outPort : node.outputs.keySet()) {
            if (!validOutputs.contains(outPort)) {
                invalidOutPorts.add(outPort);
            }
        }
        for (String invalidOut : invalidOutPorts) {
            // 防并发修改，复制一份 List
            List<Connection> links = new ArrayList<>(node.getConnections(invalidOut));
            for (Connection link : links) {
                removeConnection(nodeId, invalidOut, link.targetNodeId(), link.targetPortName());
            }
        }

        // --- 新增：4.5 清理当前节点失效的【执行流】连线 ---
        List<String> invalidExecPorts = new ArrayList<>();
        for (String execPort : node.execution.keySet()) {
            if (!validOutputs.contains(execPort)) {
                invalidExecPorts.add(execPort);
            }
        }
        for (String invalidExec : invalidExecPorts) {
            String targetNodeId = node.execution.remove(invalidExec);
            removeExecutionConnection(nodeId, invalidExec);
        }

        // 5. 清理其他节点连接到当前节点失效【输入】端口的连线 (左侧端口减少了)
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

        // 6. 无论有没有断线，只要属性变了都通知 Viewport 重新构建该节点的 UI
        mContext.notifyNodeStructureChanged(node);
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

    public void setNodeInputValue(String nodeId, String portId, Object value) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        // 注意：这里需要确保你的 NodeData 中有一个用于存储输入值的集合，比如 Map<String, Object> inputs
        if (value == null) {
            node.inputs.remove(portId);
        } else {
            node.inputs.put(portId, value);
        }
    }

    public boolean isInputPortConnected(String targetNodeId, String targetPortId) {
        NodeData node = mContext.getGraph().getNode(targetNodeId);
        if (node == null) return false;

        return node.connectedInputs.contains(targetPortId);
    }

    /**
     * 移除指定的动态分支，并将其后的所有分支数据向前位移
     */
    public void removeDynamicBranch(String nodeId, String refPortId) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null || refPortId == null || refPortId.isEmpty()) return;

        // 1. 解析要删除的索引 (例如从 "filter_type_2" 或 "center_2" 中提取 2)
        int lastUnderscore = refPortId.lastIndexOf('_');
        if (lastUnderscore == -1) return;
        int removeIndex;
        try {
            removeIndex = Integer.parseInt(refPortId.substring(lastUnderscore + 1));
        } catch (NumberFormatException e) {
            return; // 提取索引失败，退出
        }

        // 2. 获取当前总行数
        int totalCount = 1;
        Object countObj = node.properties.get("dynamic_branch_input_count"); // 替换为你的 PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()
        if (countObj instanceof Number n) totalCount = n.intValue();

        if (removeIndex < 1 || removeIndex > totalCount) return;

        // 3. 执行位移 (Shift)
        // 从 removeIndex 开始，把后面的数据依次往前挪一位
        for (int i = removeIndex; i < totalCount; i++) {
            String oldSuffix = "_" + (i + 1);
            String newSuffix = "_" + i;

            shiftMapData(node.properties, oldSuffix, newSuffix);
            shiftMapData(node.inputs, oldSuffix, newSuffix);
            shiftConnections(nodeId, oldSuffix, newSuffix);
        }

        // 4. 彻底清理最后一行的残留数据
        String lastSuffix = "_" + totalCount;
        node.properties.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.inputs.keySet().removeIf(k -> k.endsWith(lastSuffix));

        // 斩断指向最后一行的所有连线
        shiftConnections(nodeId, lastSuffix, null);

        // 5. 更新总行数并触发 UI 重构
        node.properties.put("dynamic_branch_input_count", totalCount - 1);

        // 由于结构发生了改变，强制进行 NodeDef 刷新逻辑
        setNodeProperty(nodeId, "dynamic_branch_input_count", totalCount - 1);
    }

    // --- 内部辅助方法：处理字典数据的位移 ---
    private void shiftMapData(java.util.Map<String, Object> map, String oldSuffix, String newSuffix) {
        // 先清理目标槽位，防止旧类型残留
        map.keySet().removeIf(k -> k.endsWith(newSuffix));

        java.util.Map<String, Object> toMove = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().endsWith(oldSuffix)) {
                String newKey = entry.getKey().substring(0, entry.getKey().length() - oldSuffix.length()) + newSuffix;
                toMove.put(newKey, entry.getValue());
            }
        }

        map.keySet().removeIf(k -> k.endsWith(oldSuffix));
        map.putAll(toMove);
    }

    // --- 内部辅助方法：处理外部指向该节点连线的位移 ---
    private void shiftConnections(String targetNodeId, String oldSuffix, String newSuffix) {
        List<Runnable> connectionUpdates = new ArrayList<>();

        for (NodeData otherNode : mContext.getGraph().nodes.values()) {
            for (String outPort : otherNode.outputs.keySet()) {
                for (Connection link : otherNode.getConnections(outPort)) {
                    if (link.targetNodeId().equals(targetNodeId) && link.targetPortName().endsWith(oldSuffix)) {
                        connectionUpdates.add(() -> {
                            // 先移除旧连线
                            removeConnection(otherNode.id, outPort, targetNodeId, link.targetPortName());

                            // 如果 newSuffix 不为空，建立新连线
                            if (newSuffix != null) {
                                String newPortName = link.targetPortName().substring(0, link.targetPortName().length() - oldSuffix.length()) + newSuffix;
                                addConnection(otherNode.id, outPort, targetNodeId, newPortName);
                            }
                        });
                    }
                }
            }
        }

        // 延迟执行以避免 ConcurrentModificationException
        for (Runnable r : connectionUpdates) {
            r.run();
        }
    }
}