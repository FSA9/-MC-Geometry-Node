package com.mine.geometry_node.core.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

import java.util.*;

/**
 * [图展平器]
 * 负责将嵌套的节点组 (Node Group) 递归展开为扁平的一维图结构，
 * 并处理执行流与数据流的边界桥接。
 */
class GraphFlattener {

    // 扁平化后的最终数据容器
    final Map<String, JsonObject> nodeDataLookup = new HashMap<>();
    final Map<String, Map<String, String>> flowOutputLookup = new HashMap<>();
    final Map<String, RuntimeGraphIndex.ConnectionSource> inputLookup = new HashMap<>();
    final Map<String, List<String>> typeLookup = new HashMap<>();
    final Map<String, Map<String, Object>> propertyLookup = new HashMap<>();
    final Map<String, Map<String, Object>> staticInputLookup = new HashMap<>();

    // 暂存：节点组的边界映射 (用于最后一步的桥接)
    // Key: GroupNodeID (全局ID) -> Info
    private final Map<String, GroupBoundary> groupBoundaries = new HashMap<>();

    // 辅助索引：快速查找某个 group_in/group_out 属于哪个 Group
    // Key: GroupIn/Out_NodeID (全局ID) -> Value: Group_NodeID (全局ID)
    private final Map<String, String> internalToGroupMap = new HashMap<>();

    /**
     * 执行展平逻辑
     * @param rootNodes 根节点的 JSON 对象
     */
    void flatten(JsonObject rootNodes) {
        // 1. 递归展开所有节点
        flattenRecursive("", rootNodes);

        // 2. 桥接节点组边界 (Bridging)
        bridgeGroups();
    }

    private void flattenRecursive(String prefix, JsonObject nodesMap) {
        for (String localId : nodesMap.keySet()) {
            JsonObject nodeObj = nodesMap.getAsJsonObject(localId);
            String globalId = prefix + localId; // 全局唯一 ID

            // 1. 基础信息提取
            nodeDataLookup.put(globalId, nodeObj);

            String type = "unknown";
            if (nodeObj.has("node_type")) {
                type = nodeObj.get("node_type").getAsString();
                typeLookup.computeIfAbsent(type, k -> new ArrayList<>()).add(globalId);

                // 节点组递归
                if ("node_group".equals(type) && nodeObj.has("sub_nodes")) {
                    GroupBoundary boundary = parseGroupBoundary(globalId, nodeObj, prefix);
                    groupBoundaries.put(globalId, boundary);

                    if (boundary.groupInId != null) internalToGroupMap.put(boundary.groupInId, globalId);
                    if (boundary.groupOutId != null) internalToGroupMap.put(boundary.groupOutId, globalId);

                    flattenRecursive(globalId + "/", nodeObj.getAsJsonObject("sub_nodes"));

                    // Group 节点本身只是容器，不参与运算，但保留它在 DataLookup 中供调试
                    continue;
                }
            }

            // 2. 属性与静态输入提取 (含默认值烘焙优化)
            if (nodeObj.has("properties")) {
                propertyLookup.put(globalId, RuntimeGraphIndex.parseValueMap(nodeObj.getAsJsonObject("properties")));
            }

            // --- 烘焙(Baking) 核心逻辑 ---
            Map<String, Object> bakedInputs = new HashMap<>();

            // 引擎注册表获取该节点默认值
            BaseNode logic = NodeRegistry.INSTANCE.get(type);
            if (logic != null) {
                NodeDef def = logic.getDefaultDefinition();
                if (def != null) {
                    for (PortRow row : def.rows()) {
                        if (row.leftPort() != null && row.leftPort().defaultValue() != null) {
                            bakedInputs.put(row.leftPort().id(), row.leftPort().defaultValue());
                        }
                    }
                }
            }
            // JSON 静态值覆盖默认值
            if (nodeObj.has("inputs")) {
                bakedInputs.putAll(RuntimeGraphIndex.parseValueMap(nodeObj.getAsJsonObject("inputs")));
            }
            // 保存数据字典
            staticInputLookup.put(globalId, bakedInputs);

            // 执行流提取
            if (nodeObj.has("execution")) {
                Map<String, String> flowMap = new HashMap<>();
                JsonObject execObj = nodeObj.getAsJsonObject("execution");
                for (String port : execObj.keySet()) {
                    JsonElement targetId = execObj.get(port);
                    if (!targetId.isJsonNull()) {
                        flowMap.put(port, prefix + targetId.getAsString());
                    }
                }
                flowOutputLookup.put(globalId, flowMap);
            }

            // 数据流提取
            if (nodeObj.has("outputs")) {
                JsonObject outObj = nodeObj.getAsJsonObject("outputs");
                for (String sourcePort : outObj.keySet()) {
                    JsonArray targets = outObj.getAsJsonArray(sourcePort);
                    for (JsonElement t : targets) {
                        JsonObject tObj = t.getAsJsonObject();
                        String targetLocalId = tObj.get("target_node").getAsString();
                        String targetPort = tObj.get("target_port").getAsString();

                        String targetGlobalId = prefix + targetLocalId;
                        String key = makeKey(targetGlobalId, targetPort);

                        inputLookup.put(key, new RuntimeGraphIndex.ConnectionSource(globalId, sourcePort));
                    }
                }
            }
        }
    }

    /**
     * 核心桥接逻辑：消除所有 NodeGroup、GroupIn、GroupOut 的中间商
     */
    private void bridgeGroups() {
        // --- 1. 数据流重定向 (Data Flow - Pull Model) ---
        // 目标：当节点请求数据时，必须追踪到真正的生产者，跳过 GroupOut(内部->外部) 和 GroupIn(外部->内部)

        Map<String, RuntimeGraphIndex.ConnectionSource> finalInputLookup = new HashMap<>();

        for (Map.Entry<String, RuntimeGraphIndex.ConnectionSource> entry : inputLookup.entrySet()) {
            String targetKey = entry.getKey(); // Who needs data?
            RuntimeGraphIndex.ConnectionSource source = entry.getValue(); // Who provides it?

            // 尝试解析源头的真实身份
            RuntimeGraphIndex.ConnectionSource resolvedSource = resolveDataSource(source);

            // 只有当源头有效（非空）且不是死胡同的时候才保留
            if (resolvedSource != null) {
                finalInputLookup.put(targetKey, resolvedSource);
            }
        }
        inputLookup.clear();
        inputLookup.putAll(finalInputLookup);


        // --- 2. 执行流重定向 (Execution Flow - Push Model) ---
        // 目标：当节点执行完毕跳转时，必须追踪到真正的下一个执行者，跳过 Group(外部->内部) 和 GroupOut(内部->外部)

        for (String sourceId : new HashSet<>(flowOutputLookup.keySet())) {
            Map<String, String> outputs = flowOutputLookup.get(sourceId);
            if (outputs == null) continue;

            Map<String, String> newOutputs = new HashMap<>();
            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                String portName = entry.getKey();
                String targetId = entry.getValue();

                String resolvedTarget = resolveExecutionTarget(targetId, portName);
                if (resolvedTarget != null) {
                    newOutputs.put(portName, resolvedTarget);
                }
            }
            flowOutputLookup.put(sourceId, newOutputs);
        }
    }

    // --- 递归解析逻辑 ---

    /**
     * [数据流解析] 给定一个数据源，如果是虚拟节点(Group/GroupIn)，则寻找其背后的真实数据源
     */
    private RuntimeGraphIndex.ConnectionSource resolveDataSource(RuntimeGraphIndex.ConnectionSource currentSource) {
        String nodeId = currentSource.sourceNodeId();
        String port = currentSource.sourcePortName();

        // 情况 A: 源头是一个 Group 节点 (说明我们在 Group 外部，连接了 Group 的输出)
        // 动作：钻入内部，寻找是谁连接了 `group_out` 的对应端口
        if (groupBoundaries.containsKey(nodeId)) {
            GroupBoundary boundary = groupBoundaries.get(nodeId);
            if (boundary.groupOutId == null) return null; // 该 Group 没有输出出口

            // 在 inputLookup 中查找：谁连到了 group_out 节点的 port 端口？
            String internalKey = makeKey(boundary.groupOutId, port);
            RuntimeGraphIndex.ConnectionSource internalProvider = inputLookup.get(internalKey);

            if (internalProvider != null) {
                return resolveDataSource(internalProvider); // 递归：内部提供者可能还是一个 Group
            }
            return null; // 内部 group_out 悬空，无数据
        }

        // 情况 B: 源头是一个 GroupIn 节点 (说明我们在 Group 内部，连接了 group_in 的输出)
        // 动作：钻出外部，寻找是谁连接了 `Group节点` 的对应端口
        if (internalToGroupMap.containsKey(nodeId)) {
            // 检查这是否是一个 GroupIn 节点 (通过边界信息反查，或者通过 typeLookup 查，这里用 map 简化判断)
            String ownerGroupId = internalToGroupMap.get(nodeId);
            GroupBoundary boundary = groupBoundaries.get(ownerGroupId);

            // 确认一下当前的 nodeId 确实是该组的 groupInId
            if (nodeId.equals(boundary.groupInId)) {
                // 在 inputLookup 中查找：谁连到了 Group 节点的 port 端口？
                String externalKey = makeKey(ownerGroupId, port);
                RuntimeGraphIndex.ConnectionSource externalProvider = inputLookup.get(externalKey);

                if (externalProvider != null) {
                    return resolveDataSource(externalProvider); // 递归：外部提供者可能还是一个 GroupIn
                }
                return null; // 外部 Group 悬空，无数据
            }
        }

        // 情况 C: 普通节点，直接返回
        return currentSource;
    }

    /**
     * [执行流解析] 给定一个跳转目标，如果是虚拟节点，则寻找其背后的真实目标
     */
    private String resolveExecutionTarget(String targetId, String portName) {
        // 情况 A: 目标是一个 Group 节点 (外部 -> 进内部)
        if (groupBoundaries.containsKey(targetId)) {
            GroupBoundary boundary = groupBoundaries.get(targetId);
            if (boundary.groupInId == null) return null; // Group 无入口

            // 关键修正：不能返回 groupInId，而要返回 groupInId 指向的下一个节点！
            // 假设 Group 的 "flow_in" 对应内部 group_in 的 "flow_out" (通常只有一个执行流)
            // 这里我们假设端口名映射规则：外部 portName (e.g. "flow") -> 内部 "flow"
            // 或者更简单的：group_in 通常只有一个名为 "flow_out" 或 similar 的输出。
            // 按照规范，我们尝试查找 targetId=groupInId, port=portName 的输出

            // 为了稳健，我们假设 group_in 直接透传端口名，或者如果没找到，尝试默认的 "flow_out"
            Map<String, String> internalFlows = flowOutputLookup.get(boundary.groupInId);
            if (internalFlows != null) {
                // 1. 尝试同名端口穿透
                if (internalFlows.containsKey(portName)) {
                    return resolveExecutionTarget(internalFlows.get(portName), portName);
                }
                // 2. 尝试默认端口 "flow_out" (针对标准 flow_in -> flow_out)
                if (internalFlows.containsKey("flow_out")) {
                    return resolveExecutionTarget(internalFlows.get("flow_out"), "flow_out");
                }
            }
            return null; // 死胡同
        }

        // 情况 B: 目标是一个 GroupOut 节点 (内部 -> 出外部)
        if (internalToGroupMap.containsKey(targetId)) {
            String ownerGroupId = internalToGroupMap.get(targetId);
            GroupBoundary boundary = groupBoundaries.get(ownerGroupId);

            if (targetId.equals(boundary.groupOutId)) {
                // 查找 Group 节点本身的输出
                Map<String, String> externalFlows = flowOutputLookup.get(ownerGroupId);
                if (externalFlows != null) {
                    // 同样，端口名穿透。内部 group_out 的输入端口名 = 外部 Group 的输出端口名
                    if (externalFlows.containsKey(portName)) {
                        return resolveExecutionTarget(externalFlows.get(portName), portName);
                    }
                    // 兼容默认 flow
                    if (externalFlows.containsKey("flow_out")) {
                        return resolveExecutionTarget(externalFlows.get("flow_out"), "flow_out");
                    }
                }
                return null; // 流程在 Group 处终结
            }
        }

        // 情况 C: 普通节点
        return targetId;
    }

    private GroupBoundary parseGroupBoundary(String groupId, JsonObject groupObj, String prefix) {
        JsonObject subNodes = groupObj.getAsJsonObject("sub_nodes");
        String inId = null;
        String outId = null;

        for (String key : subNodes.keySet()) {
            JsonObject sub = subNodes.getAsJsonObject(key);
            if (!sub.has("node_type")) continue;

            String type = sub.get("node_type").getAsString();
            String globalKey = prefix + groupId + "/" + key;

            if ("group_in".equals(type)) inId = globalKey;
            if ("group_out".equals(type)) outId = globalKey;
        }
        return new GroupBoundary(groupId, inId, outId);
    }

    private static String makeKey(String nodeId, String portName) {
        return nodeId + "#" + portName;
    }

    private record GroupBoundary(String groupId, String groupInId, String groupOutId) {}
}