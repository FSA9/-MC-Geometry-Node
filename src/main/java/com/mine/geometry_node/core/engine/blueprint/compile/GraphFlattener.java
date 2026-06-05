package com.mine.geometry_node.core.engine.blueprint.compile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
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
    public record TargetConnection(String targetNodeId, String targetPortName) {}
    final Map<String, JsonObject> nodeDataLookup = new HashMap<>();
    final Map<String, Map<String, TargetConnection>> flowOutputLookup = new HashMap<>();
    final Map<String, RuntimeGraphIndex.ConnectionSource> inputLookup = new HashMap<>();
    final Map<String, List<String>> typeLookup = new HashMap<>();
    final Map<String, Map<String, Object>> propertyLookup = new HashMap<>();
    final Map<String, Map<String, Object>> staticInputLookup = new HashMap<>();

    private final Map<String, GroupBoundary> groupBoundaries = new HashMap<>();
    private final Map<String, String> internalToGroupMap = new HashMap<>();

    final Set<String> allStaticKeys = new HashSet<>();

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
            String globalId = prefix + localId;

            // 1. 基础信息提取
            nodeDataLookup.put(globalId, nodeObj);

            String type = "unknown";
            if (nodeObj.has("node_type")) {
                type = nodeObj.get("node_type").getAsString();
                typeLookup.computeIfAbsent(type, k -> new ArrayList<>()).add(globalId);

                if ("node_group".equals(type) && nodeObj.has("sub_nodes")) {
                    GroupBoundary boundary = parseGroupBoundary(globalId, nodeObj, prefix);
                    groupBoundaries.put(globalId, boundary);

                    if (boundary.groupInId != null) internalToGroupMap.put(boundary.groupInId, globalId);
                    if (boundary.groupOutId != null) internalToGroupMap.put(boundary.groupOutId, globalId);

                    flattenRecursive(globalId + "/", nodeObj.getAsJsonObject("sub_nodes"));
                    continue;
                }
            }

            // 2. 属性与静态输入提取
            if (nodeObj.has("properties")) {
                Map<String, Object> props = BlueprintCompiler.parseValueMap(nodeObj.getAsJsonObject("properties"));
                propertyLookup.put(globalId, props);
                // ✨ 收集属性里的字符串值 (这通常包含了填写的“变量名”、“事件参数名”等)
                for (Object val : props.values()) {
                    if (val instanceof String s) allStaticKeys.add(s);
                }
            }

            // --- 烘焙(Baking) 核心逻辑 ---
            Map<String, Object> bakedInputs = new HashMap<>();
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
            if (nodeObj.has("inputs")) {
                bakedInputs.putAll(BlueprintCompiler.parseValueMap(nodeObj.getAsJsonObject("inputs")));
            }
            staticInputLookup.put(globalId, bakedInputs);
            // ✨ 收集所有的输入端口名
            allStaticKeys.addAll(bakedInputs.keySet());

            // --- 执行流提取 ---
            String execKey = nodeObj.has("exec_outputs") ? "exec_outputs" : (nodeObj.has("execution") ? "execution" : null);
            if (execKey != null) {
                Map<String, TargetConnection> flowMap = new HashMap<>();
                JsonObject execObj = nodeObj.getAsJsonObject(execKey);

                for (String port : execObj.keySet()) {
                    // ✨ 收集执行输出端口名
                    allStaticKeys.add(port);

                    JsonElement el = execObj.get(port);
                    if (el.isJsonObject()) {
                        JsonObject tObj = el.getAsJsonObject();
                        String targetLocalId = tObj.get("target_node").getAsString();
                        String targetPort = tObj.get("target_port").getAsString();
                        flowMap.put(port, new TargetConnection(prefix + targetLocalId, targetPort));
                    } else if (el.isJsonPrimitive()) {
                        String targetLocalId = el.getAsString();
                        flowMap.put(port, new TargetConnection(prefix + targetLocalId, "flow_in"));
                    }
                }
                flowOutputLookup.put(globalId, flowMap);
            }

            // 数据流提取
            if (nodeObj.has("outputs")) {
                JsonObject outObj = nodeObj.getAsJsonObject("outputs");
                for (String sourcePort : outObj.keySet()) {
                    // ✨ 收集数据输出端口名
                    allStaticKeys.add(sourcePort);

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
        // --- 1. 数据流重定向 (Pull Model) ---
        Map<String, RuntimeGraphIndex.ConnectionSource> finalInputLookup = new HashMap<>();
        for (Map.Entry<String, RuntimeGraphIndex.ConnectionSource> entry : inputLookup.entrySet()) {
            RuntimeGraphIndex.ConnectionSource resolvedSource = resolveDataSource(entry.getValue());
            if (resolvedSource != null) {
                finalInputLookup.put(entry.getKey(), resolvedSource);
            }
        }
        inputLookup.clear();
        inputLookup.putAll(finalInputLookup);

        // --- 2. [重写] 执行流重定向 (Push Model) ---
        for (String sourceId : new HashSet<>(flowOutputLookup.keySet())) {
            Map<String, TargetConnection> outputs = flowOutputLookup.get(sourceId);
            if (outputs == null) continue;

            Map<String, TargetConnection> newOutputs = new HashMap<>();
            for (Map.Entry<String, TargetConnection> entry : outputs.entrySet()) {
                String outPortName = entry.getKey();
                TargetConnection initialTarget = entry.getValue();

                TargetConnection resolvedTarget = resolveExecutionTarget(initialTarget.targetNodeId(), initialTarget.targetPortName());
                if (resolvedTarget != null) {
                    newOutputs.put(outPortName, resolvedTarget);
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
    private TargetConnection resolveExecutionTarget(String targetId, String targetPort) {
        // 情况 A: 目标是一个 Group 节点 (外部 -> 进内部)
        if (groupBoundaries.containsKey(targetId)) {
            GroupBoundary boundary = groupBoundaries.get(targetId);
            if (boundary.groupInId == null) return null;

            Map<String, TargetConnection> internalFlows = flowOutputLookup.get(boundary.groupInId);
            if (internalFlows != null) {
                // 1. 外部连的是 Group 的 targetPort，对应 group_in 的输出端口也是 targetPort
                if (internalFlows.containsKey(targetPort)) {
                    TargetConnection nextHop = internalFlows.get(targetPort);
                    return resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName());
                }
                // 2. 兼容回落
                if (internalFlows.containsKey("flow_out")) {
                    TargetConnection nextHop = internalFlows.get("flow_out");
                    return resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName());
                }
            }
            return null; // 死胡同
        }

        // 情况 B: 目标是一个 GroupOut 节点 (内部 -> 出外部)
        if (internalToGroupMap.containsKey(targetId)) {
            String ownerGroupId = internalToGroupMap.get(targetId);
            GroupBoundary boundary = groupBoundaries.get(ownerGroupId);

            if (targetId.equals(boundary.groupOutId)) {
                Map<String, TargetConnection> externalFlows = flowOutputLookup.get(ownerGroupId);
                if (externalFlows != null) {
                    // 内部连的是 group_out 的 targetPort，对应 Group 的输出端口也是 targetPort
                    if (externalFlows.containsKey(targetPort)) {
                        TargetConnection nextHop = externalFlows.get(targetPort);
                        return resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName());
                    }
                    if (externalFlows.containsKey("flow_out")) {
                        TargetConnection nextHop = externalFlows.get("flow_out");
                        return resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName());
                    }
                }
                return null; // 流程在 Group 处终结
            }
        }

        // 情况 C: 普通节点，触底返回真实的节点与端口
        return new TargetConnection(targetId, targetPort);
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
