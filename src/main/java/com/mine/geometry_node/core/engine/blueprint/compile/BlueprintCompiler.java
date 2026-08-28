package com.mine.geometry_node.core.engine.blueprint.compile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompileContext;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDocumentValidator;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.events.block.OnMultiblockBuilt;
import com.mine.geometry_node.core.node.port.StandardPorts;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles blueprint JSON into the immutable runtime index used by the VM.
 */
public final class BlueprintCompiler implements GraphCompiler<RuntimeGraphIndex> {
    private static final Gson GSON = new Gson();
    public static final BlueprintCompiler INSTANCE = new BlueprintCompiler();

    private BlueprintCompiler() {
    }

    @SuppressWarnings("unchecked")
    public static RuntimeGraphIndex compile(Reader jsonReader) {
        JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
        return compileDocument(GraphCompileContext.ANONYMOUS, root);
    }

    @Override
    public GraphKind runtimeKind() {
        return GraphKind.BLUEPRINT;
    }

    @Override
    public RuntimeGraphIndex compile(JsonObject document) {
        return compileDocument(GraphCompileContext.ANONYMOUS, document);
    }

    @Override
    public RuntimeGraphIndex compile(GraphCompileContext context, JsonObject document) {
        return compileDocument(context, document);
    }

    @SuppressWarnings("unchecked")
    private static RuntimeGraphIndex compileDocument(GraphCompileContext context, JsonObject root) {
        String graphTypeId = root.has("graph_kind")
                ? GraphType.normalizeId(root.get("graph_kind").getAsString())
                : GraphTypeRegistry.BLUEPRINT.id();
        if (graphTypeId.isEmpty()) {
            graphTypeId = GraphTypeRegistry.BLUEPRINT.id();
        }
        GraphType graphType = GraphTypeRegistry.INSTANCE.require(graphTypeId);
        if (graphType.runtimeKind() != GraphKind.BLUEPRINT) {
            throw new IllegalArgumentException(
                    "Blueprint compiler cannot compile graph type: " + graphType.id());
        }
        QuestDefinition questDefinition = GraphTypeRegistry.QUEST.id().equals(graphTypeId)
                ? QuestDefinition.fromJson(root.get("quest"))
                : QuestDefinition.EMPTY;
        QuestConditionOverview questConditionOverview = GraphTypeRegistry.QUEST.id().equals(graphTypeId)
                ? QuestConditionOverview.fromGraph(GSON.fromJson(root, NodeGraph.class))
                : QuestConditionOverview.EMPTY;
        JsonObject rootNodes = root.getAsJsonObject("nodes");

        GraphFlattener flattener = new GraphFlattener();
        flattener.flatten(rootNodes);
        GraphDocumentValidator.requireValid(validationInput(context, graphTypeId, flattener));

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

        Map<String, List<Integer>> multiblockLookup = new HashMap<>();
        List<Integer> multiblockNodes = typeToIntList.getOrDefault(OnMultiblockBuilt.TYPE_ID, List.of());
        for (int nodeId : multiblockNodes) {
            Object configuredId = staticInputArray[nodeId].get(StandardPorts.TYPE.getId());
            String structureId = configuredId != null ? String.valueOf(configuredId).trim() : "";
            if (structureId.isEmpty()) {
                structureId = MultiblockStructureManager.ANY_STRUCTURE_ID;
            }

            multiblockLookup.computeIfAbsent(structureId, k -> new ArrayList<>()).add(nodeId);
        }
        Map<String, List<Integer>> multiblockLookupImmutable = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : multiblockLookup.entrySet()) {
            multiblockLookupImmutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return RuntimeGraphIndex.createCompiled(
                graphTypeId,
                questDefinition,
                questConditionOverview,
                idToString,
                stringToId,
                nodeDataArray,
                typeArray,
                flowOutputArray,
                inputArray,
                typeToIntList,
                Map.copyOf(receiveLookupImmutable),
                Map.copyOf(multiblockLookupImmutable),
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
                System.err.println("[BlueprintCompiler] Warning: Ignored null/unsupported value for input: " + key);
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

    private static GraphDocumentValidator.Input validationInput(
            GraphCompileContext context, String graphTypeId, GraphFlattener flattener) {
        List<GraphDocumentValidator.Node> nodes = flattener.nodeDataLookup.entrySet().stream()
                .map(entry -> new GraphDocumentValidator.Node(entry.getKey(),
                        entry.getValue() != null && entry.getValue().has("node_type")
                                ? entry.getValue().get("node_type").getAsString() : null))
                .toList();
        List<GraphDocumentValidator.DataEdge> edges = flattener.inputLookup.entrySet().stream()
                .map(entry -> new GraphDocumentValidator.DataEdge(
                        entry.getValue().sourceNodeId(), dataTargetNode(entry.getKey())))
                .toList();
        int flowConnections = flattener.flowOutputLookup.values().stream()
                .mapToInt(Map::size).sum();
        return new GraphDocumentValidator.Input(
                context != null ? context.diagnosticAssetId() : "<anonymous>",
                graphTypeId, nodes, edges, flowConnections + flattener.inputLookup.size());
    }

    private static String dataTargetNode(String inputKey) {
        int separator = inputKey != null ? inputKey.indexOf('#') : -1;
        return separator >= 0 ? inputKey.substring(0, separator) : inputKey;
    }
}
