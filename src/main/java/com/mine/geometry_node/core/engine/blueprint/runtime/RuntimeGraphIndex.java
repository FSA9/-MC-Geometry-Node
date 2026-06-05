package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [运行时图索引 / 蓝图字节码载体] (Immutable Graph Index)
 */
public class RuntimeGraphIndex {

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

    // ====================================================
    // 3. 构造与工厂方法 (Constructors & Factory)
    // ====================================================

    private RuntimeGraphIndex(String[] idToString,
                              Map<String, Integer> stringToId,
                              JsonObject[] nodeDataArray,
                              String[] typeArray,
                              IntFlowTarget[][] flowOutputArray,
                              IntConnectionSource[][] inputArray,
                              Map<String, List<Integer>> typeLookup,
                              Map<String, List<Integer>> receiveBlueprintLookup,
                              Map<String, Object>[] propertyArray,
                              Map<String, Object>[] staticInputArray,
                              Map<String, Integer> keyDictionary,
                              List<String> dictionaryReverse) {
        this.idToString = idToString;
        this.stringToId = stringToId;
        this.nodeDataArray = nodeDataArray;
        this.typeArray = typeArray;
        this.flowOutputArray = flowOutputArray;
        this.inputArray = inputArray;
        this.typeLookup = typeLookup;
        this.receiveBlueprintLookup = receiveBlueprintLookup;
        this.propertyArray = propertyArray;
        this.staticInputArray = staticInputArray;
        this.keyDictionary = keyDictionary;
        this.dictionaryReverse = dictionaryReverse;
    }

    /**
     * Compiler-facing factory. Runtime code should use the query API on the
     * returned immutable index rather than constructing indexes directly.
     */
    public static RuntimeGraphIndex createCompiled(String[] idToString,
                                                   Map<String, Integer> stringToId,
                                                   JsonObject[] nodeDataArray,
                                                   String[] typeArray,
                                                   IntFlowTarget[][] flowOutputArray,
                                                   IntConnectionSource[][] inputArray,
                                                   Map<String, List<Integer>> typeLookup,
                                                   Map<String, List<Integer>> receiveBlueprintLookup,
                                                   Map<String, Object>[] propertyArray,
                                                   Map<String, Object>[] staticInputArray,
                                                   Map<String, Integer> keyDictionary,
                                                   List<String> dictionaryReverse) {
        return new RuntimeGraphIndex(
                idToString,
                stringToId,
                nodeDataArray,
                typeArray,
                flowOutputArray,
                inputArray,
                typeLookup,
                receiveBlueprintLookup,
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

    /** 通过 Int 索引还原原始的 String ID (常用于报错日志与存档持久化) */
    public String getIdToString(int id) {
        return (id >= 0 && id < idToString.length) ? idToString[id] : null;
    }

    public int registerKey(String key) {
        return keyDictionary.computeIfAbsent(key, k -> {
            int id = dictionaryReverse.size();
            dictionaryReverse.add(k);
            return id;
        });
    }

    public int getKeyId(String key) {
        return keyDictionary.getOrDefault(key, -1);
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
        if (node.has("execution") && node.getAsJsonObject("execution").has(portName)) return true;

        return false;
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

    /**
     * 获取指定类型的所有节点 (常用于查找引擎分发事件的入口节点)
     */
    public List<Integer> findNodesByType(String nodeType) {
        return typeLookup.getOrDefault(nodeType, List.of());
    }

    public List<Integer> findReceiveBlueprintNodes(String frequency) {
        return receiveBlueprintLookup.getOrDefault(frequency, List.of());
    }

    public Set<String> getReceiveBlueprintFrequencies() {
        return receiveBlueprintLookup.keySet();
    }


    public int getRegisterCount() {
        return dictionaryReverse.size();
    }

    public int getNodeCount() {
        return nodeDataArray.length;
    }
}
