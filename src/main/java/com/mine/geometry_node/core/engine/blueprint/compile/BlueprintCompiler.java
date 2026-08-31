package com.mine.geometry_node.core.engine.blueprint.compile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompileContext;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphFlattener;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.engine.graph.compile.CompiledNodeTable;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDocumentValidator;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.events.block.OnMultiblockBuilt;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles blueprint JSON into the immutable runtime index used by the VM.
 */
public final class BlueprintCompiler implements GraphCompiler<BlueprintPlan> {
    private static final Gson GSON = new Gson();
    public static final BlueprintCompiler INSTANCE = new BlueprintCompiler();

    private BlueprintCompiler() {
    }

    @SuppressWarnings("unchecked")
    public static BlueprintPlan compile(Reader jsonReader) {
        JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
        return compileDocument(GraphCompileContext.ANONYMOUS, root);
    }

    @Override
    public GraphKind runtimeKind() {
        return GraphKind.BLUEPRINT;
    }

    @Override
    public BlueprintPlan compile(JsonObject document) {
        return compileDocument(GraphCompileContext.ANONYMOUS, document);
    }

    @Override
    public BlueprintPlan compile(GraphCompileContext context, JsonObject document) {
        return compileDocument(context, document);
    }

    @SuppressWarnings("unchecked")
    private static BlueprintPlan compileDocument(GraphCompileContext context, JsonObject root) {
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

        FlattenedGraph flattened = GraphFlattener.flatten(rootNodes);
        GraphDocumentValidator.requireValid(GraphDocumentValidator.input(
                context != null ? context.diagnosticAssetId() : "<anonymous>",
                graphTypeId, flattened));
        CompiledNodeTable nodeTable = CompiledNodeTable.build(flattened);
        CompiledNodeIndex nodes = nodeTable.index();

        List<String> allIds = nodeTable.nodeIds();
        int size = allIds.size();
        Map<String, Integer> stringToId = new HashMap<>(size);
        for (int index = 0; index < size; index++) stringToId.put(allIds.get(index), index);
        Map<Integer, BlueprintPlan.IntFlowTarget>[] flowOutputArray = new Map[size];

        for (int i = 0; i < size; i++) {
            String strId = allIds.get(i);
            flowOutputArray[i] = new HashMap<>();

            Map<String, FlattenedGraph.TargetConnection> oldFlow = flattened.executionOutputs().get(strId);
            if (oldFlow != null) {
                for (Map.Entry<String, FlattenedGraph.TargetConnection> e : oldFlow.entrySet()) {
                    Integer targetInt = stringToId.get(e.getValue().targetNodeId());
                    int portId = nodes.getPortKey(e.getKey());
                    if (targetInt != null && portId >= 0) {
                        flowOutputArray[i].put(portId,
                                new BlueprintPlan.IntFlowTarget(targetInt, e.getValue().targetPortName()));
                    }
                }
            }
        }

        Map<String, List<Integer>> typeToIntList = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : flattened.nodesByType().entrySet()) {
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
            Object frequency = nodes.getStaticInput(nodeId, "frequency");
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
            Object configuredId = nodes.getStaticInput(nodeId, StandardPorts.TYPE.getId());
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

        return BlueprintPlan.createCompiled(
                graphTypeId,
                questDefinition,
                questConditionOverview,
                nodes,
                flowOutputArray,
                typeToIntList,
                Map.copyOf(receiveLookupImmutable),
                Map.copyOf(multiblockLookupImmutable)
        );
    }
}
