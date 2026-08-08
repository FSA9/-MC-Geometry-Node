package com.mine.geometry_node.core.node;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import com.mine.geometry_node.core.node.port.PortType;

import java.util.*;

/**
 * [存储层] 节点实例纯状态容器
 */
public class NodeData {
    public static final int DEFAULT_GROUP_COLOR = FrameData.DEFAULT_COLOR;

    // 索引标识符
    public transient String id;

    @SerializedName("node_type")
    public String type;

    @SerializedName("UI_pos")
    public float[] uiPos = new float[2];

    @SerializedName("custom_name")
    public String customName;

    @SerializedName("custom_color")
    public Integer customColor;

    @SerializedName("comment")
    public String comment;

    @SerializedName("inputs")
    public Map<String, Object> inputs = new HashMap<>();

    @SerializedName("exec_outputs")
    public Map<String, Connection> execOutputs = new HashMap<>();

    @SerializedName("outputs")
    public Map<String, List<Connection>> outputs = new HashMap<>();

    @SerializedName("port_config")
    public PortsConfig portConfig = new PortsConfig();

    @SerializedName("parent_frame")
    public String parentFrame;

    public static class PortsConfig {
        public Map<String, PortConfig> inputs = new LinkedHashMap<>();

        @SerializedName("exec_inputs")
        public Map<String, PortConfig> execInputs = new LinkedHashMap<>();

        @SerializedName("exec_outputs")
        public Map<String, PortConfig> execOutputs = new LinkedHashMap<>();

        public Map<String, PortConfig> outputs = new LinkedHashMap<>();
    }

    public static class PortConfig {
        @SerializedName("custom_name")
        public String customName;
        public Boolean hidden;
        public PortType type;
        public Integer order;
    }

    public String getEffectivePortName(String category, String portId, String fallback) {
        if (portConfig == null) return fallback;

        Map<String, PortConfig> targetMap = getPortConfigMap(category);

        if (targetMap != null && targetMap.containsKey(portId)) {
            String custom = targetMap.get(portId).customName;
            if (custom != null && !custom.trim().isEmpty()) {
                return custom;
            }
        }
        return fallback;
    }

    public transient Set<String> connectedInputs = new HashSet<>();

    public transient NodeData parentGroupNode;

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

    public boolean isGroupNode() {
        return GroupNodeTypes.NODE_GROUP.equals(type);
    }

    public int getHeaderColor(int fallbackColor) {
        if (customColor != null) {
            return customColor | 0xFF000000;
        }
        return isGroupNode() ? DEFAULT_GROUP_COLOR : fallbackColor;
    }

    public boolean isGroupInputNode() {
        return GroupNodeTypes.GROUP_IN.equals(type);
    }

    public boolean isGroupOutputNode() {
        return GroupNodeTypes.GROUP_OUT.equals(type);
    }

    public boolean isRerouteNode() {
        return RerouteNodeSupport.isReroute(this);
    }

    public PortsConfig ensurePortConfig() {
        if (portConfig == null) {
            portConfig = new PortsConfig();
        }
        if (portConfig.inputs == null) portConfig.inputs = new LinkedHashMap<>();
        if (portConfig.execInputs == null) portConfig.execInputs = new LinkedHashMap<>();
        if (portConfig.execOutputs == null) portConfig.execOutputs = new LinkedHashMap<>();
        if (portConfig.outputs == null) portConfig.outputs = new LinkedHashMap<>();
        return portConfig;
    }

    public Map<String, PortConfig> getPortConfigMap(String category) {
        PortsConfig config = ensurePortConfig();
        return switch (category) {
            case GroupNodeTypes.CATEGORY_INPUTS -> config.inputs;
            case GroupNodeTypes.CATEGORY_EXEC_INPUTS -> config.execInputs;
            case GroupNodeTypes.CATEGORY_EXEC_OUTPUTS -> config.execOutputs;
            case GroupNodeTypes.CATEGORY_OUTPUTS -> config.outputs;
            default -> null;
        };
    }

    public Map<String, NodeData> ensureSubNodes() {
        if (subNodes == null) {
            subNodes = new LinkedHashMap<>();
        }
        return subNodes;
    }

    public void attachSubNode(String id, NodeData node) {
        if (id == null || node == null) return;
        node.id = id;
        node.parentGroupNode = this;
        ensureSubNodes().put(id, node);
    }

    public void setFlow(String port, String targetNodeId, String targetPortName) {
        this.execOutputs.put(port, new Connection(targetNodeId, targetPortName));
    }

    // --- 辅助方法 ---

    public void addExecutionConnection(String outPort, String targetId, String targetPortName) {
        this.execOutputs.put(outPort, new Connection(targetId, targetPortName));
    }

    public void removeExecutionConnection(String outPort) {
        this.execOutputs.remove(outPort);
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
