package com.mine.geometry_node.core.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.*;

/**
 * [运行时图索引] (不可变 / 只读)
 */
public class RuntimeGraphIndex {

    // ===========================================
    // 1. 核心索引字段 (Fields)
    // ===========================================

    // [新增] ID 双向映射
    private final String[] idToString;
    private final Map<String, Integer> stringToId;

    // [优化] 核心数据结构全部变为数组 (下标即为 int nodeId)
    private final JsonObject[] nodeDataArray;
    private final String[] typeArray;
    private final Map<String, Integer>[] flowOutputArray;
    private final Map<String, IntConnectionSource>[] inputArray;
    private final Map<String, Object>[] propertyArray;
    private final Map<String, Object>[] staticInputArray;

    // 类型分类索引保持不变，但值变成了 int
    private final Map<String, List<Integer>> typeLookup;


    // ===========================================
    // 构造器与工厂方法
    // ===========================================

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
     * [核心构建] 从 Reader (JSON) 直接构建索引
     */
    public static RuntimeGraphIndex build(Reader jsonReader) {
        JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
        JsonObject rootNodes = root.getAsJsonObject("nodes");

        GraphFlattener flattener = new GraphFlattener();
        flattener.flatten(rootNodes);

        // 依然使用 String 校验死锁，安全可靠
        validateNoDataCycles(flattener.inputLookup);

        // --- 开始第一阶段优化：将 String 映射为 Int ---
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

        // 初始化数组
        JsonObject[] nodeDataArray = new JsonObject[size];
        String[] typeArray = new String[size];
        Map<String, Integer>[] flowOutputArray = new Map[size];
        Map<String, IntConnectionSource>[] inputArray = new Map[size];
        Map<String, Object>[] propertyArray = new Map[size];
        Map<String, Object>[] staticInputArray = new Map[size];

        for (int i = 0; i < size; i++) {
            inputArray[i] = new HashMap<>(); // 预初始化
        }

        // 填充数组
        for (int i = 0; i < size; i++) {
            String strId = idToString[i];

            nodeDataArray[i] = flattener.nodeDataLookup.get(strId);
            JsonObject node = nodeDataArray[i];
            typeArray[i] = (node != null && node.has("node_type")) ? node.get("node_type").getAsString() : "unknown";

            // 执行流转换 (String -> int)
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

            propertyArray[i] = flattener.propertyLookup.getOrDefault(strId, Collections.emptyMap());
            staticInputArray[i] = flattener.staticInputLookup.getOrDefault(strId, Collections.emptyMap());
        }

        // 数据流转换 (TargetID#Port -> SourceIntID)
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

        // 类型分类转换
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


    // ===========================================
    // 映射工具 API
    // ===========================================
    public int getStringToId(String strId) {
        return stringToId.getOrDefault(strId, -1);
    }

    public String getIdToString(int id) {
        return (id >= 0 && id < idToString.length) ? idToString[id] : null;
    }

    // ===========================================
    // 公开查询 API - O(1)
    // ===========================================
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

    @Nullable
    public Object getNodeStaticInput(int nodeId, String portName) {
        if (nodeId < 0 || nodeId >= staticInputArray.length) return null;
        return staticInputArray[nodeId].get(portName);
    }

    // 注意这里返回值变成了 int，找不到返回 -1
    public int findFlowTarget(int currentNodeId, String outputPortName) {
        if (currentNodeId < 0 || currentNodeId >= flowOutputArray.length) return -1;
        return flowOutputArray[currentNodeId].getOrDefault(outputPortName, -1);
    }

    @Nullable
    public IntConnectionSource findInputSource(int targetNodeId, String inputPortName) {
        if (targetNodeId < 0 || targetNodeId >= inputArray.length) return null;
        return inputArray[targetNodeId].get(inputPortName);
    }

    public List<Integer> findNodesByType(String nodeType) {
        return typeLookup.getOrDefault(nodeType, List.of());
    }

    // 新增内部类：使用 int 代替 string
    public record IntConnectionSource(int sourceNodeId, String sourcePortName) {}


    // ===========================================
    // 核心验证逻辑
    // ===========================================

    private static void validateNoDataCycles(Map<String, ConnectionSource> inputLookup) {
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        for (String key : inputLookup.keySet()) {
            String targetNodeId = key.split("#")[0];
            String sourceNodeId = inputLookup.get(key).sourceNodeId();
            dependencyGraph.computeIfAbsent(targetNodeId, k -> new HashSet<>()).add(sourceNodeId);
        }

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String nodeId : dependencyGraph.keySet()) {
            if (checkCycleDFS(nodeId, dependencyGraph, visited, recStack)) {
                throw new IllegalStateException("Data flow cycle detected! Graph compilation failed at node: " + nodeId);
            }
        }
    }

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


    // ===========================================
    // 5. 内部辅助方法
    // ===========================================

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
        return Map.copyOf(map);
    }

    static Object unwrapJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean();
            if (prim.isNumber()) return prim.getAsNumber();
            if (prim.isString()) return prim.getAsString();
        }
        // JSON解析
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

    private static String makeKey(String nodeId, String portName) {
        return nodeId + "#" + portName;
    }


    // ===========================================
    // 6. 内部数据结构 (Nested Types)
    // ===========================================

    /**
     * 表示数据流连接的源头信息
     */
    public record ConnectionSource(String sourceNodeId, String sourcePortName) {}
}