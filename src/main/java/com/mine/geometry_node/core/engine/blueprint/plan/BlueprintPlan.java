package com.mine.geometry_node.core.engine.blueprint.plan;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [蓝图编译计划 / 蓝图字节码载体] (Immutable Blueprint Plan)
 */
public class BlueprintPlan implements CompiledGraph, CompiledDataIndex {

    // ====================================================
    // 1. 数据结构定义
    // ====================================================

    public record IntFlowTarget(int targetNodeId, String targetPortName) {}

    private final CompiledNodeIndex nodes;
    private final String graphTypeId;
    private final QuestDefinition questDefinition;
    private final QuestConditionOverview questConditionOverview;

    // ====================================================
    // 2. 核心索引结构
    // ====================================================

    // --- 节点数据核心数组 (数组下标即为 int nodeId) ---
    private final Map<Integer, IntFlowTarget>[] flowOutputArray;

    // --- 分类与查询辅助 ---
    private final Map<String, List<Integer>> typeLookup;                  // 按节点类型归类 (常用于查找事件起始节点)
    private final Map<String, List<Integer>> receiveBlueprintLookup;
    private final Map<String, List<Integer>> multiblockStructureLookup;

    // ====================================================
    // 3. 构造与工厂方法 (Constructors & Factory)
    // ====================================================

    private BlueprintPlan(String graphTypeId,
                              QuestDefinition questDefinition,
                              QuestConditionOverview questConditionOverview,
                              String[] idToString,
                              String[] typeArray,
                              Set<String>[] portArray,
                              Map<Integer, IntFlowTarget>[] flowOutputArray,
                              Map<Integer, DataConnectionSource>[] inputArray,
                              Map<String, List<Integer>> typeLookup,
                              Map<String, List<Integer>> receiveBlueprintLookup,
                              Map<String, List<Integer>> multiblockStructureLookup,
                              Map<String, Object>[] staticInputArray,
                              Map<String, Integer> keyDictionary) {
        this.graphTypeId = graphTypeId;
        this.questDefinition = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
        this.questConditionOverview = questConditionOverview != null
                ? questConditionOverview
                : QuestConditionOverview.EMPTY;
        this.nodes = new CompiledNodeIndex(idToString, typeArray, staticInputArray,
                inputArray, portArray, keyDictionary);
        this.flowOutputArray = copyMapArray(flowOutputArray);
        this.typeLookup = copyLookup(typeLookup);
        this.receiveBlueprintLookup = copyLookup(receiveBlueprintLookup);
        this.multiblockStructureLookup = copyLookup(multiblockStructureLookup);
    }

    private static Map<String, List<Integer>> copyLookup(Map<String, List<Integer>> lookup) {
        Map<String, List<Integer>> copy = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : lookup.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<Integer, T>[] copyMapArray(Map<Integer, T>[] source) {
        Map<Integer, T>[] copy = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? Map.copyOf(source[i]) : Map.of();
        }
        return copy;
    }

    /**
     * Compiler-facing factory. Runtime code should use the query API on the
     * returned immutable plan rather than constructing plans directly.
     */
    public static BlueprintPlan createCompiled(String graphTypeId,
                                                   QuestDefinition questDefinition,
                                                   QuestConditionOverview questConditionOverview,
                                                   String[] idToString,
                                                   String[] typeArray,
                                                   Set<String>[] portArray,
                                                   Map<Integer, IntFlowTarget>[] flowOutputArray,
                                                   Map<Integer, DataConnectionSource>[] inputArray,
                                                   Map<String, List<Integer>> typeLookup,
                                                   Map<String, List<Integer>> receiveBlueprintLookup,
                                                   Map<String, List<Integer>> multiblockStructureLookup,
                                                   Map<String, Object>[] staticInputArray,
                                                   Map<String, Integer> keyDictionary) {
        return new BlueprintPlan(
                graphTypeId,
                questDefinition,
                questConditionOverview,
                idToString,
                typeArray,
                portArray,
                flowOutputArray,
                inputArray,
                typeLookup,
                receiveBlueprintLookup,
                multiblockStructureLookup,
                staticInputArray,
                keyDictionary
        );
    }

    // ====================================================
    // 4. 字典与映射 API (Dictionaries & Mappings)
    // ====================================================

    /** 通过 String ID 获取运行时的 Int 索引 (常用于读档恢复) */
    public int getStringToId(String strId) {
        return nodes.getNodeKey(strId);
    }

    public String getGraphTypeId() {
        return graphTypeId;
    }

    @Override
    public String graphTypeId() {
        return graphTypeId;
    }

    @Override
    public GraphKind runtimeKind() {
        return GraphTypeRegistry.INSTANCE.require(graphTypeId).runtimeKind();
    }

    public QuestDefinition getQuestDefinition() {
        return questDefinition;
    }

    public QuestConditionOverview getQuestConditionOverview() {
        return questConditionOverview;
    }

    /** 通过 Int 索引还原原始的 String ID (常用于报错日志与存档持久化) */
    public String getIdToString(int id) {
        return nodes.getNodeId(id);
    }

    @Override
    @Nullable
    public String getNodeId(int nodeId) {
        return getIdToString(nodeId);
    }

    public int getKeyId(String key) {
        return nodes.getPortKey(key);
    }

    @Override
    public int getPortKey(String portName) {
        return getKeyId(portName);
    }

    /** 将 Int 寄存器 ID 翻译回原始的 String (用于序列化保存) */
    @Nullable
    public String getKeyFromId(int id) {
        return nodes.getPortName(id);
    }


    // ====================================================
    // 5. 图查询 API - O(1) (Graph Query Operations)
    // ====================================================

    public String getNodeType(int nodeId) {
        String type = nodes.getNodeType(nodeId);
        return type.isEmpty() ? "unknown" : type;
    }

    public boolean hasPort(int nodeId, String portName) {
        return nodes.hasPort(nodeId, portName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getNodeStaticInput(int nodeId, String portId, Class<T> type, T defaultValue) {
        Object raw = getNodeStaticInput(nodeId, portId);
        if (raw == null) return defaultValue;

        // 如果类型直接匹配，直接强转
        if (type.isInstance(raw)) {
            return (T) raw;
        }

        // 针对 Number 的通用处理 (解决 Integer 和 Double 在 JSON 里的互转问题)
        if (raw instanceof Number n) {
            if (type == Integer.class) return (T) Integer.valueOf(n.intValue());
            if (type == Float.class) return (T) Float.valueOf(n.floatValue());
            if (type == Double.class) return (T) Double.valueOf(n.doubleValue());
            if (type == Long.class) return (T) Long.valueOf(n.longValue());
        }

        // 针对 String 强转数字的容错处理
        if (raw instanceof String s) {
            try {
                if (type == Integer.class) return (T) Integer.valueOf(s);
                if (type == Double.class) return (T) Double.valueOf(s);
            } catch (NumberFormatException ignored) {}
        }

        return defaultValue;
    }

    @Nullable
    public Object getNodeStaticInput(int nodeId, String portName) {
        return nodes.getStaticInput(nodeId, portName);
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, String portName) {
        return getNodeStaticInput(nodeId, portName);
    }

    /**
     * 查找控制流的下一个目标节点
     * @return 目标节点的 int ID，若分支尽头无连接则返回 -1
     */
    @Nullable
    public IntFlowTarget findFlowTarget(int currentNodeId, String outputPortName) {
        if (currentNodeId < 0 || currentNodeId >= flowOutputArray.length) return null;
        int portId = getKeyId(outputPortName);
        return portId >= 0 ? flowOutputArray[currentNodeId].get(portId) : null;
    }

    /**
     * 向上游索要数据流的源头
     * @return 包装了源节点 ID 与端口名的记录类，若未连接则返回 null
     */
    @Nullable
    public DataConnectionSource findInputSource(int targetNodeId, String inputPortName) {
        return nodes.findDataInput(targetNodeId, inputPortName);
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        return nodes.findDataInput(targetNodeId, inputPortName);
    }

    /**
     * 获取指定类型的所有节点 (常用于查找引擎分发事件的入口节点)
     */
    public List<Integer> findNodesByType(String nodeType) {
        return typeLookup.getOrDefault(nodeType, List.of());
    }

    public Set<String> getNodeTypes() {
        return typeLookup.keySet();
    }

    public List<Integer> findReceiveBlueprintNodes(String frequency) {
        return receiveBlueprintLookup.getOrDefault(frequency, List.of());
    }

    public Set<String> getReceiveBlueprintFrequencies() {
        return receiveBlueprintLookup.keySet();
    }

    public List<Integer> findMultiblockBuiltNodes(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return multiblockStructureLookup.getOrDefault("*", List.of());
        }

        List<Integer> exact = multiblockStructureLookup.getOrDefault(structureId, List.of());
        List<Integer> wildcard = multiblockStructureLookup.getOrDefault("*", List.of());
        if (wildcard.isEmpty()) {
            return exact;
        }
        if (exact.isEmpty()) {
            return wildcard;
        }

        List<Integer> combined = new java.util.ArrayList<>(wildcard.size() + exact.size());
        combined.addAll(exact);
        combined.addAll(wildcard);
        return List.copyOf(combined);
    }

    public Set<String> getMultiblockStructureIds() {
        return multiblockStructureLookup.keySet();
    }


    public int getRegisterCount() {
        return nodes.getPortCount();
    }

    public int getNodeCount() {
        return nodes.getNodeCount();
    }
}
