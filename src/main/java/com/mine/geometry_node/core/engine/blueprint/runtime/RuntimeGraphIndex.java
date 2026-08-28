package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [运行时图索引 / 蓝图字节码载体] (Immutable Graph Index)
 */
public class RuntimeGraphIndex implements CompiledGraph, CompiledDataIndex {

    // ====================================================
    // 1. 数据结构定义
    // ====================================================

    public record IntFlowTarget(int targetNodeId, String targetPortName) {}

    /** 编译期内部使用：表示数据流连接的源头信息 (基于 String) */
    public record ConnectionSource(String sourceNodeId, String sourcePortName) {}

    /** 运行时核心结构：表示数据流连接的源头信息 (基于 Int 寄存器/索引) */
    public record IntConnectionSource(int sourceNodeId, String sourcePortName) {}

    private final Map<String, Integer> keyDictionary;
    private final List<String> dictionaryReverse;
    private final String graphTypeId;
    private final QuestDefinition questDefinition;
    private final QuestConditionOverview questConditionOverview;

    // ====================================================
    // 2. 核心索引结构
    // ====================================================

    // --- ID 双向映射表 ---
    private final String[] idToString;
    private final Map<String, Integer> stringToId;

    // --- 节点数据核心数组 (数组下标即为 int nodeId) ---
    private final JsonObject[] nodeDataArray;                               // 节点原始配置数据 (只读透传)
    private final String[] typeArray;                                       // 节点类型标识 (如 "math_add")
    private final IntFlowTarget[][] flowOutputArray;                        // [nodeId][portId] -> 目标节点
    private final IntConnectionSource[][] inputArray;
    private final Map<String, Object>[] propertyArray;                      // 节点静态属性 (Properties)
    private final Map<String, Object>[] staticInputArray;                   // 节点静态默认输入 (Static Inputs)

    // --- 分类与查询辅助 ---
    private final Map<String, List<Integer>> typeLookup;                  // 按节点类型归类 (常用于查找事件起始节点)
    private final Map<String, List<Integer>> receiveBlueprintLookup;
    private final Map<String, List<Integer>> multiblockStructureLookup;

    // ====================================================
    // 3. 构造与工厂方法 (Constructors & Factory)
    // ====================================================

    private RuntimeGraphIndex(String graphTypeId,
                              QuestDefinition questDefinition,
                              QuestConditionOverview questConditionOverview,
                              String[] idToString,
                              Map<String, Integer> stringToId,
                              JsonObject[] nodeDataArray,
                              String[] typeArray,
                              IntFlowTarget[][] flowOutputArray,
                              IntConnectionSource[][] inputArray,
                              Map<String, List<Integer>> typeLookup,
                              Map<String, List<Integer>> receiveBlueprintLookup,
                              Map<String, List<Integer>> multiblockStructureLookup,
                              Map<String, Object>[] propertyArray,
                              Map<String, Object>[] staticInputArray,
                              Map<String, Integer> keyDictionary,
                              List<String> dictionaryReverse) {
        this.graphTypeId = graphTypeId;
        this.questDefinition = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
        this.questConditionOverview = questConditionOverview != null
                ? questConditionOverview
                : QuestConditionOverview.EMPTY;
        this.idToString = idToString.clone();
        this.stringToId = Map.copyOf(stringToId);
        this.nodeDataArray = nodeDataArray.clone();
        this.typeArray = typeArray.clone();
        this.flowOutputArray = copyFlowOutputArray(flowOutputArray);
        this.inputArray = copyInputArray(inputArray);
        this.typeLookup = copyLookup(typeLookup);
        this.receiveBlueprintLookup = copyLookup(receiveBlueprintLookup);
        this.multiblockStructureLookup = copyLookup(multiblockStructureLookup);
        this.propertyArray = copyObjectMapArray(propertyArray);
        this.staticInputArray = copyObjectMapArray(staticInputArray);
        this.keyDictionary = Map.copyOf(keyDictionary);
        this.dictionaryReverse = List.copyOf(dictionaryReverse);
    }

    private static Map<String, List<Integer>> copyLookup(Map<String, List<Integer>> lookup) {
        Map<String, List<Integer>> copy = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : lookup.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static IntFlowTarget[][] copyFlowOutputArray(IntFlowTarget[][] source) {
        IntFlowTarget[][] copy = new IntFlowTarget[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : new IntFlowTarget[0];
        }
        return copy;
    }

    private static IntConnectionSource[][] copyInputArray(IntConnectionSource[][] source) {
        IntConnectionSource[][] copy = new IntConnectionSource[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : new IntConnectionSource[0];
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object>[] copyObjectMapArray(Map<String, Object>[] source) {
        Map<String, Object>[] copy = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? Map.copyOf(source[i]) : Map.of();
        }
        return copy;
    }

    /**
     * Compiler-facing factory. Runtime code should use the query API on the
     * returned immutable index rather than constructing indexes directly.
     */
    public static RuntimeGraphIndex createCompiled(String graphTypeId,
                                                   QuestDefinition questDefinition,
                                                   QuestConditionOverview questConditionOverview,
                                                   String[] idToString,
                                                   Map<String, Integer> stringToId,
                                                   JsonObject[] nodeDataArray,
                                                   String[] typeArray,
                                                   IntFlowTarget[][] flowOutputArray,
                                                   IntConnectionSource[][] inputArray,
                                                   Map<String, List<Integer>> typeLookup,
                                                   Map<String, List<Integer>> receiveBlueprintLookup,
                                                   Map<String, List<Integer>> multiblockStructureLookup,
                                                   Map<String, Object>[] propertyArray,
                                                   Map<String, Object>[] staticInputArray,
                                                   Map<String, Integer> keyDictionary,
                                                   List<String> dictionaryReverse) {
        return new RuntimeGraphIndex(
                graphTypeId,
                questDefinition,
                questConditionOverview,
                idToString,
                stringToId,
                nodeDataArray,
                typeArray,
                flowOutputArray,
                inputArray,
                typeLookup,
                receiveBlueprintLookup,
                multiblockStructureLookup,
                propertyArray,
                staticInputArray,
                keyDictionary,
                dictionaryReverse
        );
    }

    /**
     * @deprecated Use {@link BlueprintCompiler#compile(Reader)}.
     */
    @Deprecated
    public static RuntimeGraphIndex build(Reader jsonReader) {
        return BlueprintCompiler.compile(jsonReader);
    }

    // ====================================================
    // 4. 字典与映射 API (Dictionaries & Mappings)
    // ====================================================

    /** 通过 String ID 获取运行时的 Int 索引 (常用于读档恢复) */
    public int getStringToId(String strId) {
        return stringToId.getOrDefault(strId, -1);
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
        return (id >= 0 && id < idToString.length) ? idToString[id] : null;
    }

    @Override
    @Nullable
    public String getNodeId(int nodeId) {
        return getIdToString(nodeId);
    }

    public int getKeyId(String key) {
        return keyDictionary.getOrDefault(key, -1);
    }

    @Override
    public int getPortKey(String portName) {
        return getKeyId(portName);
    }

    /** 将 Int 寄存器 ID 翻译回原始的 String (用于序列化保存) */
    @Nullable
    public String getKeyFromId(int id) {
        if (id >= 0 && id < dictionaryReverse.size()) {
            return dictionaryReverse.get(id);
        }
        return null;
    }


    // ====================================================
    // 5. 图查询 API - O(1) (Graph Query Operations)
    // ====================================================

    public String getNodeType(int nodeId) {
        if (nodeId < 0 || nodeId >= typeArray.length) return "unknown";
        return typeArray[nodeId];
    }

    public boolean hasPort(int nodeId, String portName) {
        if (nodeId < 0 || nodeId >= nodeDataArray.length) return false;
        JsonObject node = nodeDataArray[nodeId];
        if (node == null) return false;

        if (node.has("inputs") && node.getAsJsonObject("inputs").has(portName)) return true;
        if (node.has("outputs") && node.getAsJsonObject("outputs").has(portName)) return true;
        if (node.has("exec_outputs") && node.getAsJsonObject("exec_outputs").has(portName)) return true;
        if (node.has("execution") && node.getAsJsonObject("execution").has(portName)) return true;

        if (node.has("port_config") && node.get("port_config").isJsonObject()) {
            JsonObject portConfig = node.getAsJsonObject("port_config");
            if (hasPortConfigEntry(portConfig, "inputs", portName)) return true;
            if (hasPortConfigEntry(portConfig, "exec_inputs", portName)) return true;
            if (hasPortConfigEntry(portConfig, "outputs", portName)) return true;
            if (hasPortConfigEntry(portConfig, "exec_outputs", portName)) return true;
        }

        return false;
    }

    private static boolean hasPortConfigEntry(JsonObject portConfig, String category, String portName) {
        return portConfig.has(category)
                && portConfig.get(category).isJsonObject()
                && portConfig.getAsJsonObject(category).has(portName);
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
        if (nodeId < 0 || nodeId >= staticInputArray.length) return null;
        return staticInputArray[nodeId].get(portName);
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
        if (portId < 0 || portId >= flowOutputArray[currentNodeId].length) return null;
        return flowOutputArray[currentNodeId][portId];
    }

    /**
     * 向上游索要数据流的源头
     * @return 包装了源节点 ID 与端口名的记录类，若未连接则返回 null
     */
    @Nullable
    public IntConnectionSource findInputSource(int targetNodeId, String inputPortName) {
        if (targetNodeId < 0 || targetNodeId >= inputArray.length) return null;
        int portId = getKeyId(inputPortName);
        if (portId < 0 || portId >= inputArray[targetNodeId].length) return null;
        return inputArray[targetNodeId][portId];
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        IntConnectionSource source = findInputSource(targetNodeId, inputPortName);
        return source != null
                ? new DataConnectionSource(source.sourceNodeId(), source.sourcePortName())
                : null;
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
        return dictionaryReverse.size();
    }

    public int getNodeCount() {
        return nodeDataArray.length;
    }
}
