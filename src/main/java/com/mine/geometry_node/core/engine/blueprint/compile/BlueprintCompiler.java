package com.mine.geometry_node.core.engine.blueprint.compile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles blueprint JSON into the immutable runtime index used by the VM.
 */
public final class BlueprintCompiler {
    private BlueprintCompiler() {
    }

    @SuppressWarnings("unchecked")
    public static RuntimeGraphIndex compile(Reader jsonReader) {
        JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
        JsonObject rootNodes = root.getAsJsonObject("nodes");

        GraphFlattener flattener = new GraphFlattener();
        flattener.flatten(rootNodes);

        validateNoDataCycles(flattener.inputLookup);

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

        Map<String, Integer> keyDict = new HashMap<>();
        List<String> dictReverse = new ArrayList<>();
        for (String key : flattener.allStaticKeys) {
            if (!keyDict.containsKey(key)) {
                keyDict.put(key, dictReverse.size());
                dictReverse.add(key);
            }
        }
        int maxPortId = dictReverse.size();

        JsonObject[] nodeDataArray = new JsonObject[size];
        String[] typeArray = new String[size];
        RuntimeGraphIndex.IntFlowTarget[][] flowOutputArray = new RuntimeGraphIndex.IntFlowTarget[size][maxPortId];
        RuntimeGraphIndex.IntConnectionSource[][] inputArray = new RuntimeGraphIndex.IntConnectionSource[size][maxPortId];
        Map<String, Object>[] propertyArray = new Map[size];
        Map<String, Object>[] staticInputArray = new Map[size];

        for (int i = 0; i < size; i++) {
            String strId = idToString[i];

            nodeDataArray[i] = flattener.nodeDataLookup.get(strId);
            JsonObject node = nodeDataArray[i];
            typeArray[i] = node != null && node.has("node_type") ? node.get("node_type").getAsString() : "unknown";

            Map<String, GraphFlattener.TargetConnection> oldFlow = flattener.flowOutputLookup.get(strId);
            if (oldFlow != null) {
                for (Map.Entry<String, GraphFlattener.TargetConnection> e : oldFlow.entrySet()) {
                    Integer targetInt = stringToId.get(e.getValue().targetNodeId());
                    Integer portId = keyDict.get(e.getKey());
                    if (targetInt != null && portId != null) {
                        flowOutputArray[i][portId] = new RuntimeGraphIndex.IntFlowTarget(targetInt, e.getValue().targetPortName());
                    }
                }
            }

            propertyArray[i] = flattener.propertyLookup.getOrDefault(strId, Collections.emptyMap());
            staticInputArray[i] = flattener.staticInputLookup.getOrDefault(strId, Collections.emptyMap());
        }

        for (Map.Entry<String, RuntimeGraphIndex.ConnectionSource> entry : flattener.inputLookup.entrySet()) {
            String[] parts = entry.getKey().split("#");
            String targetId = parts[0];
            String portName = parts[1];

            Integer targetInt = stringToId.get(targetId);
            Integer sourceInt = stringToId.get(entry.getValue().sourceNodeId());
            Integer portId = keyDict.get(portName);

            if (targetInt != null && sourceInt != null && portId != null) {
                inputArray[targetInt][portId] = new RuntimeGraphIndex.IntConnectionSource(sourceInt, entry.getValue().sourcePortName());
            }
        }

        Map<String, List<Integer>> typeToIntList = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : flattener.typeLookup.entrySet()) {
            List<Integer> intList = new ArrayList<>();
            for (String s : entry.getValue()) {
                Integer id = stringToId.get(s);
                if (id != null) {
                    intList.add(id);
                }
            }
            typeToIntList.put(entry.getKey(), List.copyOf(intList));
        }

        Map<String, List<Integer>> receiveLookup = new HashMap<>();
        List<Integer> receiveNodes = typeToIntList.getOrDefault("receive_blueprint", List.of());
        for (int nodeId : receiveNodes) {
            Object frequency = staticInputArray[nodeId].get("frequency");
            if (frequency == null) {
                continue;
            }

            String frequencyKey = String.valueOf(frequency);
            if (frequencyKey.isEmpty()) {
                continue;
            }

            receiveLookup.computeIfAbsent(frequencyKey, k -> new ArrayList<>()).add(nodeId);
        }
        Map<String, List<Integer>> receiveLookupImmutable = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : receiveLookup.entrySet()) {
            receiveLookupImmutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return RuntimeGraphIndex.createCompiled(
                idToString,
                stringToId,
                nodeDataArray,
                typeArray,
                flowOutputArray,
                inputArray,
                typeToIntList,
                Map.copyOf(receiveLookupImmutable),
                propertyArray,
                staticInputArray,
                keyDict,
                dictReverse
        );
    }

    static Map<String, Object> parseValueMap(JsonObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (String key : obj.keySet()) {
            JsonElement val = obj.get(key);
            Object unwrapped = unwrapJsonElement(val);
            if (unwrapped != null) {
                map.put(key, unwrapped);
            } else {
                System.err.println("[BlueprintCompiler] Warning: Ignored null/unsupported value for key: " + key);
            }
        }
        return Map.copyOf(map);
    }

    private static Object unwrapJsonElement(JsonElement element) {
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
                Object value = unwrapJsonElement(item);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }

        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            Map<String, Object> map = new HashMap<>();
            for (String key : jsonObject.keySet()) {
                Object value = unwrapJsonElement(jsonObject.get(key));
                if (value != null) {
                    map.put(key, value);
                }
            }
            return Map.copyOf(map);
        }
        return null;
    }

    private static void validateNoDataCycles(Map<String, RuntimeGraphIndex.ConnectionSource> inputLookup) {
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
                throw new IllegalStateException("[BlueprintCompiler] Data flow cycle detected! Graph compilation failed at node: " + nodeId);
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
}
