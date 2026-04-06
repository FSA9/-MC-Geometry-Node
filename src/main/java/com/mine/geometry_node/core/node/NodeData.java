// --- START OF FILE NodeData.java (Updated) ---
package com.mine.geometry_node.core.node;

import com.google.gson.annotations.SerializedName;

import java.util.*;

/**
 * [存储层] 节点实例纯状态容器
 */
public class NodeData {
    // 索引标识符
    public transient String id;

    @SerializedName("node_type")
    public String type;

    @SerializedName("UI_pos")
    public float[] uiPos = new float[2];

    @SerializedName("properties")
    public Map<String, Object> properties = new HashMap<>();

    @SerializedName("inputs")
    public Map<String, Object> inputs = new HashMap<>();

    @SerializedName("execution")
    public Map<String, String> execution = new HashMap<>();

    @SerializedName("outputs")
    public Map<String, List<Connection>> outputs = new HashMap<>();

    public transient Set<String> connectedInputs = new HashSet<>();

    // 支持节点组递归
    @SerializedName("sub_nodes")
    public Map<String, NodeData> subNodes;

    public NodeData() {}

    public NodeData(String id, String type, float x, float y) {
        this.id = id;
        this.type = type;
        this.uiPos[0] = x;
        this.uiPos[1] = y;
    }

    public float getX() { return uiPos[0]; }
    public float getY() { return uiPos[1]; }
    public void setPosition(float x, float y) {
        this.uiPos[0] = x;
        this.uiPos[1] = y;
    }

    public void setFlow(String port, String targetNodeId) {
        this.execution.put(port, targetNodeId);
    }

    // --- 辅助方法 ---

    public void addExecutionConnection(String outPort, String targetId) {
        this.execution.put(outPort, targetId);
    }

    public void removeExecutionConnection(String outPort) {
        this.execution.remove(outPort);
    }

    public void addDataConnection(String outPort, String targetId, String targetInPort) {
        Connection newLink = new Connection(targetId, targetInPort);
        this.outputs.computeIfAbsent(outPort, k -> new ArrayList<>()).add(newLink);
    }

    public void removeDataConnection(String outPort, String targetId, String targetInPort) {
        List<Connection> list = this.outputs.get(outPort);
        if (list != null) {
            // 对象字段匹配
            list.removeIf(link ->
                    link.targetNodeId().equals(targetId) &&
                            link.targetPortName().equals(targetInPort)
            );

            if (list.isEmpty()) {
                this.outputs.remove(outPort);
            }
        }
    }

    /**
     * 判断指定的输入端口是否已被连线
     */
    public boolean isInputConnected(String portId) {
        if (connectedInputs == null) {
            connectedInputs = new HashSet<>();
        }
        return connectedInputs.contains(portId);
    }

    /**
     * 更新输入端口的连线状态 (由 GraphController 在连线/断开时调用)
     */
    public void setInputConnected(String portId, boolean connected) {
        if (connectedInputs == null) {
            connectedInputs = new HashSet<>();
        }
        if (connected) {
            connectedInputs.add(portId);
        } else {
            connectedInputs.remove(portId);
        }
    }

    // 获取目标端口所有连线
    public List<Connection> getConnections(String outPort) {
        return this.outputs.getOrDefault(outPort, List.of());
    }
}