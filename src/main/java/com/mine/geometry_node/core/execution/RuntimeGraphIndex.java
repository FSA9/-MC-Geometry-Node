package com.mine.geometry_node.core.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [运行时图索引 / 蓝图字节码载体] (Immutable Graph Index)
 */
public class RuntimeGraphIndex {

    // ====================================================
    // 1. 数据结构定义
    // ====================================================

    /** 编译期内部使用：表示数据流连接的源头信息 (基于 String) */
    public record ConnectionSource(String sourceNodeId, String sourcePortName) {}

    /** 运行时核心结构：表示数据流连接的源头信息 (基于 Int 寄存器/索引) */
    public record IntConnectionSource(int sourceNodeId, String sourcePortName) {}


    // ====================================================
    // 2. 核心索引结构
    // ====================================================

    // --- ID 双向映射表 ---
    private final String[] idToString;
    private final Map<String, Integer> stringToId;

    // --- 节点数据核心数组 (数组下标即为 int nodeId) ---
    private final JsonObject[] nodeDataArray;                             // 节点原始配置数据 (只读透传)
    private final String[] typeArray;                                     // 节点类型标识 (如 "math_add")
    private final Map<String, Integer>[] flowOutputArray;                 // 执行流拓扑：当前节点输出端口 -> 下一个节点 ID
    private final Map<String, IntConnectionSource>[] inputArray;          // 数据流拓扑：当前节点输入端口 -> 上游数据提供者
    private final Map<String, Object>[] propertyArray;                    // 节点静态属性 (Properties)
    private final Map<String, Object>[] staticInputArray;                 // 节点静态默认输入 (Static Inputs)

    // --- 分类与查询辅助 ---
    private final Map<String, List<Integer>> typeLookup;                  // 按节点类型归类 (常用于查找事件起始节点)

    // --- 全局并发字典 ---
    // 用于将运行时的动态 String (如局部变量名、事件参数名) 映射为固定的寄存器槽位(int)
    private final Map<String, Integer> keyDictionary = new ConcurrentHashMap<>();
    private final List<String> dictionaryReverse = new CopyOnWriteArrayList<>();


    // ====================================================
    // 3. 构造与工厂方法 (Constructors & Factory)
    // ====================================================

    private RuntimeGraphIndex(String[] idToString,
                              Map<String, Integer> stringToId,
                              JsonObject[] nodeDataArray,
                              String[] typeArray,
                              Map<String, Integer>[] flowOutputArray,
                              Map<String, IntConnectionSource>[] inputArray,
                              Map<String, List<Integer>> typeLookup,
                              Map<String, Object>[] propertyArray,
                              Map<String, Object>[] staticInputArray) {
        this.idToString = idToString;
        this.stringToId = stringToId;
        this.nodeDataArray = nodeDataArray;
        this.typeArray = typeArray;
        this.flowOutputArray = flowOutputArray;
        this.inputArray = inputArray;
        this.typeLookup = typeLookup;
        this.propertyArray = propertyArray;
        this.staticInputArray = staticInputArray;
    }

    /**
     * [编译器主入口] 将原始 JSON 蓝图文件编译为高性能的运行时索引。
     */
    public static RuntimeGraphIndex build(Reader jsonReader) {
        JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
        JsonObject rootNodes = root.getAsJsonObject("nodes");

        // 1: 展平图结构 (消除 NodeGroup 等嵌套逻辑)
        GraphFlattener flattener = new GraphFlattener();
        flattener.flatten(rootNodes);

        // 2: 编译期安全检查 (数据流成环/死锁检测)
        validateNoDataCycles(flattener.inputLookup);

        // 3: 生成 ID 映射表 (String -> Int)
        Set<String> allIds = flattener.nodeDataLookup.keySet();
        int size = allIds.size();

        String[] idToString = new String[size];
        Map<String, Integer> stringToId = new HashMap<>(size);

        int indexCounter = 0;
        for (String id : allIds) {
            idToString[indexCounter] = id;
            stringToId.put(id, indexCounter);
            indexCounter++;
        }

        // 4: 初始化连续内存数组
        JsonObject[] nodeDataArray = new JsonObject[size];
        String[] typeArray = new String[size];
        Map<String, Integer>[] flowOutputArray = new Map[size];
        Map<String, IntConnectionSource>[] inputArray = new Map[size];
        Map<String, Object>[] propertyArray = new Map[size];
        Map<String, Object>[] staticInputArray = new Map[size];

        for (int i = 0; i < size; i++) {
            inputArray[i] = new HashMap<>();
        }

        // 5: 填充数组 (降维打击)
        for (int i = 0; i < size; i++) {
            String strId = idToString[i];

            // 5.1 基础配置填充
            nodeDataArray[i] = flattener.nodeDataLookup.get(strId);
            JsonObject node = nodeDataArray[i];
            typeArray[i] = (node != null && node.has("node_type")) ? node.get("node_type").getAsString() : "unknown";

            // 5.2 执行流转换 (Target String ID -> Target Int ID)
            Map<String, String> oldFlow = flattener.flowOutputLookup.get(strId);
            if (oldFlow != null) {
                Map<String, Integer> newFlow = new HashMap<>();
                for (Map.Entry<String, String> e : oldFlow.entrySet()) {
                    Integer targetInt = stringToId.get(e.getValue());
                    if (targetInt != null) newFlow.put(e.getKey(), targetInt);
                }
                flowOutputArray[i] = newFlow;
            } else {
                flowOutputArray[i] = Collections.emptyMap();
            }

            // 5.3 静态数据提取
            propertyArray[i] = flattener.propertyLookup.getOrDefault(strId, Collections.emptyMap());
            staticInputArray[i] = flattener.staticInputLookup.getOrDefault(strId, Collections.emptyMap());
        }

        // 6: 数据流拓扑转换 (TargetID#Port -> SourceIntID)
        for (Map.Entry<String, ConnectionSource> entry : flattener.inputLookup.entrySet()) {
            String[] parts = entry.getKey().split("#");
            String targetId = parts[0];
            String portName = parts[1];

            Integer targetInt = stringToId.get(targetId);
            Integer sourceInt = stringToId.get(entry.getValue().sourceNodeId());

            if (targetInt != null && sourceInt != null) {
                inputArray[targetInt].put(portName, new IntConnectionSource(sourceInt, entry.getValue().sourcePortName()));
            }
        }

        // 7: 类型反向索引构建
        Map<String, List<Integer>> typeToIntList = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : flattener.typeLookup.entrySet()) {
            List<Integer> intList = new ArrayList<>();
            for (String s : entry.getValue()) {
                Integer id = stringToId.get(s);
                if (id != null) intList.add(id);
            }
            typeToIntList.put(entry.getKey(), List.copyOf(intList));
        }

        return new RuntimeGraphIndex(idToString, stringToId, nodeDataArray, typeArray, flowOutputArray, inputArray, typeToIntList, propertyArray, staticInputArray);
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

    /** * [寄存器分配器] 将任意 String 键映射为固定的 Int 寄存器 ID。
     * 若该键首次出现，则自动扩容并分配一个全新的连续 ID。
     */
    public int getOrRegisterKey(String key) {
        return keyDictionary.computeIfAbsent(key, k -> {
            int id = dictionaryReverse.size();
            dictionaryReverse.add(k);
            return id;
        });
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

    @Nullable
    public Object getNodeProperty(int nodeId, String key) {
        if (nodeId < 0 || nodeId >= propertyArray.length) return null;
        return propertyArray[nodeId].get(key);
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
    public int findFlowTarget(int currentNodeId, String outputPortName) {
        if (currentNodeId < 0 || currentNodeId >= flowOutputArray.length) return -1;
        return flowOutputArray[currentNodeId].getOrDefault(outputPortName, -1);
    }

    /**
     * 向上游索要数据流的源头
     * @return 包装了源节点 ID 与端口名的记录类，若未连接则返回 null
     */
    @Nullable
    public IntConnectionSource findInputSource(int targetNodeId, String inputPortName) {
        if (targetNodeId < 0 || targetNodeId >= inputArray.length) return null;
        return inputArray[targetNodeId].get(inputPortName);
    }

    /**
     * 获取指定类型的所有节点 (常用于查找引擎分发事件的入口节点)
     */
    public List<Integer> findNodesByType(String nodeType) {
        return typeLookup.getOrDefault(nodeType, List.of());
    }


    // ====================================================
    // 6. 编译器安全防线 (Compiler Validators)
    // ====================================================

    /**
     * [死锁检测] 遍历验证整张图的数据流依赖是否存在环形结构。
     * 若检测到成环（例如 A 的运算需要 B，B 的运算又需要 A），将直接抛出异常中断编译。
     */
    private static void validateNoDataCycles(Map<String, ConnectionSource> inputLookup) {
        Map<String, Set<String>> dependencyGraph = new HashMap<>();

        // 构建有向依赖图 (Target -> Source)
        for (String key : inputLookup.keySet()) {
            String targetNodeId = key.split("#")[0];
            String sourceNodeId = inputLookup.get(key).sourceNodeId();
            dependencyGraph.computeIfAbsent(targetNodeId, k -> new HashSet<>()).add(sourceNodeId);
        }

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        // 对每个节点发起 DFS
        for (String nodeId : dependencyGraph.keySet()) {
            if (checkCycleDFS(nodeId, dependencyGraph, visited, recStack)) {
                throw new IllegalStateException("[RuntimeGraphIndex] Data flow cycle detected! Graph compilation failed at node: " + nodeId);
            }
        }
    }

    /**
     * 经典的深度优先搜索 (DFS) 探路算法寻找环
     */
    private static boolean checkCycleDFS(String current, Map<String, Set<String>> adj,
                                         Set<String> visited, Set<String> recStack) {
        if (recStack.contains(current)) return true;
        if (visited.contains(current)) return false;

        recStack.add(current);
        visited.add(current);

        Set<String> dependencies = adj.get(current);
        if (dependencies != null) {
            for (String dep : dependencies) {
                if (checkCycleDFS(dep, adj, visited, recStack)) return true;
            }
        }

        recStack.remove(current);
        return false;
    }


    // ====================================================
    // 7. 内部序列化工具 (JSON Helpers)
    // ====================================================

    /** 批量解析 JSON 中的属性字典 */
    static Map<String, Object> parseValueMap(JsonObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (String key : obj.keySet()) {
            JsonElement val = obj.get(key);
            Object unwrapped = unwrapJsonElement(val);
            if (unwrapped != null) {
                map.put(key, unwrapped);
            } else {
                System.err.println("[RuntimeGraphIndex] Warning: Ignored null/unsupported value for key: " + key);
            }
        }
        return Map.copyOf(map); // 返回不可变 Map 保证运行时安全
    }

    /** 递归拆解 GSON 的基础包装类，映射为 Java 基础类型 */
    static Object unwrapJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean();
            if (prim.isNumber()) return prim.getAsNumber();
            if (prim.isString()) return prim.getAsString();
        }

        if (element.isJsonArray()) {
            JsonArray jsonArray = element.getAsJsonArray();
            List<Object> list = new ArrayList<>();
            for (JsonElement item : jsonArray) {
                list.add(unwrapJsonElement(item));
            }
            return list;
        }
        return null;
    }
}