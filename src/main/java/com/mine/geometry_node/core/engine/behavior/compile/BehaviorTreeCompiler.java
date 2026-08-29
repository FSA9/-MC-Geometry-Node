package com.mine.geometry_node.core.engine.behavior.compile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompileContext;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.engine.graph.compile.GraphFlattener;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDiagnostic;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDocumentValidator;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphValidationException;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphValidationResult;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorRootNode;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.TypeConverter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles editable behavior documents into immutable runtime plans. */
public final class BehaviorTreeCompiler implements GraphCompiler<BehaviorTreePlan> {
    public static final BehaviorTreeCompiler INSTANCE = new BehaviorTreeCompiler();
    private static final Gson GSON = new Gson();

    private BehaviorTreeCompiler() {
    }

    @Override
    public GraphKind runtimeKind() {
        return GraphKind.BEHAVIOR_TREE;
    }

    @Override
    public BehaviorTreePlan compile(JsonObject document) {
        return compile(GraphCompileContext.ANONYMOUS, document);
    }

    @Override
    public BehaviorTreePlan compile(GraphCompileContext context, JsonObject document) {
        Compilation compilation = inspectDocument(context, document);
        if (!compilation.diagnostics.isEmpty()) {
            throw new GraphValidationException(compilation.diagnostics);
        }
        return buildPlan(context, compilation);
    }

    private Compilation inspectDocument(GraphCompileContext context, JsonObject document) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        if (document == null) {
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MALFORMED",
                    "Behavior tree document is missing", "", "", ""));
        }
        try {
            FlattenedGraph flattened = GraphFlattener.flatten(document.getAsJsonObject("nodes"));
            return inspect(context, document, flattened);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() != null ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MALFORMED",
                    "Behavior tree document cannot be decoded: " + reason, "", "", ""));
        }
    }

    private Compilation inspect(GraphCompileContext context, JsonObject document,
                                FlattenedGraph flattened) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        List<GraphDiagnostic> diagnostics = new ArrayList<>();
        List<String> nodeIds = flattened.nodes().keySet().stream().sorted().toList();

        Map<String, NodeInfo> info = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            NodeData node = decodeNode(nodeId, flattened);
            if (node == null || node.type == null || !NodeRegistry.INSTANCE.has(node.type)) {
                continue;
            }
            NodeCapabilities capabilities = NodeRegistry.INSTANCE.getCapabilities(node.type);
            if (!capabilities.supports(GraphTypeRegistry.BEHAVIOR_TREE.id())) {
                continue;
            }
            NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
            if (definition == null) continue;
            PortCatalog ports = PortCatalog.from(definition);
            info.put(nodeId, new NodeInfo(node, capabilities, ports));
        }

        Map<String, List<String>> structure = compileStructure(nodeIds, flattened, info);
        Map<InputKey, DataLink> inbound = compileDataConnections(flattened, info);
        GraphValidationResult common = GraphDocumentValidator.validate(validationInput(
                assetId, document, flattened, nodeIds));
        diagnostics.addAll(common.diagnostics());
        BehaviorTreePlan.RootSchedule rootSchedule = compileRootSchedule(info);
        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId)
                .thenComparing(GraphDiagnostic::portId)
                .thenComparing(GraphDiagnostic::relatedNodeId)
                .thenComparing(GraphDiagnostic::message));
        return new Compilation(nodeIds, info, structure, inbound, rootSchedule,
                List.copyOf(diagnostics));
    }

    private static @Nullable NodeData decodeNode(String nodeId, FlattenedGraph flattened) {
        NodeData node = GSON.fromJson(flattened.nodes().get(nodeId), NodeData.class);
        if (node == null) return null;
        node.id = nodeId;
        node.restoreDocumentDefaults();
        node.inputs = new LinkedHashMap<>(flattened.staticInputs().getOrDefault(nodeId, Map.of()));
        return node;
    }

    private static Map<String, List<String>> compileStructure(
            List<String> nodeIds, FlattenedGraph flattened, Map<String, NodeInfo> info) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (String sourceId : nodeIds) {
            NodeInfo sourceInfo = info.get(sourceId);
            Map<String, FlattenedGraph.TargetConnection> outputs =
                    flattened.executionOutputs().get(sourceId);
            if (sourceInfo == null || outputs == null) continue;
            List<String> accepted = new ArrayList<>();
            for (String sourcePortId : sourceInfo.ports.outputs.keySet()) {
                FlattenedGraph.TargetConnection link = outputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.ports.outputs.get(sourcePortId);
                if (sourcePort == null || sourcePort.type() != PortType.BEHAVIOR_STRUCTURE) {
                    continue;
                }
                if (!isValid(link)) continue;
                NodeInfo targetInfo = info.get(link.targetNodeId());
                PortDef targetPort = targetInfo != null
                        ? targetInfo.ports.inputs.get(link.targetPortName()) : null;
                if (targetPort == null || targetPort.type() != PortType.BEHAVIOR_STRUCTURE) continue;
                accepted.add(link.targetNodeId());
            }
            if (!accepted.isEmpty()) children.put(sourceId, List.copyOf(accepted));
        }
        return Map.copyOf(children);
    }

    private static boolean isValid(@Nullable FlattenedGraph.TargetConnection link) {
        return link != null && link.targetNodeId() != null && !link.targetNodeId().isBlank()
                && link.targetPortName() != null && !link.targetPortName().isBlank();
    }

    private BehaviorTreePlan buildPlan(GraphCompileContext context, Compilation compilation) {
        int size = compilation.nodeIds.size();
        String[] ids = compilation.nodeIds.toArray(String[]::new);
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) indexes.put(ids[i], i);

        String[] types = new String[size];
        NodeCapabilities[] capabilities = new NodeCapabilities[size];
        int[] parents = new int[size];
        java.util.Arrays.fill(parents, -1);
        int[][] children = new int[size][];
        @SuppressWarnings("unchecked") Map<String, Object>[] staticInputs = new Map[size];
        @SuppressWarnings("unchecked") Map<String, CompiledDataIndex.DataConnectionSource>[] dataInputsByName = new Map[size];
        @SuppressWarnings("unchecked") Set<String>[] ports = new Set[size];
        Set<String> allPortNames = new java.util.TreeSet<>();
        int root = -1;

        Map<String, Map<String, DataLink>> inboundByNode = new LinkedHashMap<>();
        for (Map.Entry<InputKey, DataLink> entry : compilation.inbound.entrySet()) {
            inboundByNode.computeIfAbsent(entry.getKey().nodeId, ignored -> new LinkedHashMap<>())
                    .put(entry.getKey().portId, entry.getValue());
        }

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            String nodeId = ids[nodeIndex];
            NodeInfo nodeInfo = compilation.info.get(nodeId);
            types[nodeIndex] = nodeInfo.node.type;
            capabilities[nodeIndex] = nodeInfo.capabilities;
            if (root < 0 && BehaviorRootNode.TYPE_ID.equals(nodeInfo.node.type)) root = nodeIndex;

            List<String> childIds = compilation.structure.getOrDefault(nodeId, List.of());
            children[nodeIndex] = childIds.stream().mapToInt(indexes::get).toArray();
            for (int child : children[nodeIndex]) parents[child] = nodeIndex;

            Map<String, Object> effectiveInputs = new LinkedHashMap<>();
            for (PortDef input : nodeInfo.ports.inputs.values()) {
                Object value = nodeInfo.node.inputs.containsKey(input.id())
                        ? nodeInfo.node.inputs.get(input.id()) : input.defaultValue();
                if (value != null) {
                    Object converted = TypeConverter.convertForPort(value, input.type());
                    if (converted != null) {
                        effectiveInputs.put(input.id(), converted);
                    } else if (PortType.isCompatible(PortType.getTypeOf(value), input.type())) {
                        // Resolution that requires a world (for example UUID -> entity) is deferred.
                        effectiveInputs.put(input.id(), value);
                    }
                }
            }
            staticInputs[nodeIndex] = Map.copyOf(effectiveInputs);

            Map<String, CompiledDataIndex.DataConnectionSource> inputIndex = new LinkedHashMap<>();
            for (Map.Entry<String, DataLink> entry
                    : inboundByNode.getOrDefault(nodeId, Map.of()).entrySet()) {
                DataLink link = entry.getValue();
                inputIndex.put(entry.getKey(),
                        new CompiledDataIndex.DataConnectionSource(indexes.get(link.sourceNodeId), link.sourcePortId));
            }
            dataInputsByName[nodeIndex] = Map.copyOf(inputIndex);
            Set<String> nodePorts = new LinkedHashSet<>();
            nodePorts.addAll(nodeInfo.ports.inputs.keySet());
            nodePorts.addAll(nodeInfo.ports.outputs.keySet());
            ports[nodeIndex] = Set.copyOf(nodePorts);
            allPortNames.addAll(nodePorts);
        }

        List<String> portNames = List.copyOf(allPortNames);
        Map<String, Integer> portKeys = new LinkedHashMap<>();
        for (int i = 0; i < portNames.size(); i++) portKeys.put(portNames.get(i), i);
        @SuppressWarnings("unchecked")
        Map<Integer, CompiledDataIndex.DataConnectionSource>[] dataInputs = new Map[size];
        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            Map<Integer, CompiledDataIndex.DataConnectionSource> indexedInputs = new LinkedHashMap<>();
            dataInputsByName[nodeIndex].forEach((portName, source) ->
                    indexedInputs.put(portKeys.get(portName), source));
            dataInputs[nodeIndex] = Map.copyOf(indexedInputs);
        }
        return BehaviorTreePlan.createCompiled(
                context != null ? context.assetId() : "", ids, types, capabilities,
                root, parents, children, staticInputs, dataInputs, ports, portKeys,
                compilation.rootSchedule);
    }

    private static BehaviorTreePlan.RootSchedule compileRootSchedule(Map<String, NodeInfo> info) {
        Map.Entry<String, NodeInfo> rootEntry = null;
        for (Map.Entry<String, NodeInfo> entry : info.entrySet()) {
            if (!BehaviorRootNode.TYPE_ID.equals(entry.getValue().node.type)) continue;
            rootEntry = entry;
            break;
        }
        if (rootEntry == null) return BehaviorTreePlan.RootSchedule.DEFAULT;

        NodeInfo root = rootEntry.getValue();
        int interval = Math.max(1, staticInteger(root, BehaviorRootNode.RECHECK_TICK_PORT, 1));
        int offset = staticInteger(root, BehaviorRootNode.SCHEDULE_TICK_PORT,
                BehaviorTreePlan.RootSchedule.AUTO_OFFSET);
        offset = Math.max(BehaviorTreePlan.RootSchedule.AUTO_OFFSET, offset);
        return new BehaviorTreePlan.RootSchedule(interval, offset);
    }

    private static int staticInteger(NodeInfo node, String portId, int fallback) {
        Object value = node.node.inputs.get(portId);
        if (value == null) {
            PortDef port = node.ports.inputs.get(portId);
            value = port != null ? port.defaultValue() : null;
        }
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Map<InputKey, DataLink> compileDataConnections(
            FlattenedGraph flattened, Map<String, NodeInfo> info) {
        Map<InputKey, DataLink> inbound = new LinkedHashMap<>();
        List<Map.Entry<FlattenedGraph.InputKey, FlattenedGraph.DataConnectionSource>> links =
                flattened.dataInputs().entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<FlattenedGraph.InputKey,
                                FlattenedGraph.DataConnectionSource> entry) -> entry.getKey().nodeId())
                        .thenComparing(entry -> entry.getKey().portName()))
                .toList();
        for (Map.Entry<FlattenedGraph.InputKey, FlattenedGraph.DataConnectionSource> entry : links) {
            FlattenedGraph.InputKey target = entry.getKey();
            FlattenedGraph.DataConnectionSource source = entry.getValue();
            String sourceId = source.sourceNodeId();
            NodeInfo sourceInfo = info.get(sourceId);
            NodeInfo targetInfo = info.get(target.nodeId());
            if (sourceInfo == null || targetInfo == null) continue;
            PortDef sourcePort = sourceInfo.ports.outputs.get(source.sourcePortName());
            PortDef targetPort = targetInfo.ports.inputs.get(target.portName());
            if (sourcePort == null || targetPort == null || sourcePort.type().isFlow()
                    || targetPort.type().isFlow()
                    || !PortType.isCompatible(sourcePort.type(), targetPort.type())) continue;
            inbound.putIfAbsent(new InputKey(target.nodeId(), target.portName()),
                    new DataLink(sourceId, source.sourcePortName()));
        }
        return inbound;
    }

    private static GraphDocumentValidator.Input validationInput(
            String assetId, JsonObject document, FlattenedGraph flattened, List<String> nodeIds) {
        List<GraphDocumentValidator.Node> nodes = nodeIds.stream()
                .map(nodeId -> new GraphDocumentValidator.Node(nodeId,
                        readNodeType(flattened.nodes().get(nodeId))))
                .toList();
        return new GraphDocumentValidator.Input(assetId, readGraphType(document), nodes,
                flattenedConnectionCount(flattened));
    }

    private static @Nullable String readNodeType(@Nullable JsonObject node) {
        if (node == null || !node.has("node_type") || !node.get("node_type").isJsonPrimitive()) {
            return null;
        }
        return node.get("node_type").getAsString();
    }

    private static String readGraphType(JsonObject document) {
        if (document.has("graph_kind") && document.get("graph_kind").isJsonPrimitive()) {
            return document.get("graph_kind").getAsString();
        }
        return GraphTypeRegistry.BEHAVIOR_TREE.id();
    }

    private static int flattenedConnectionCount(FlattenedGraph flattened) {
        long count = flattened.dataInputs().size();
        count += flattened.executionOutputs().values().stream().mapToLong(Map::size).sum();
        return count > GraphDocumentValidator.MAX_CONNECTIONS
                ? GraphDocumentValidator.MAX_CONNECTIONS + 1 : (int) count;
    }

    private static Compilation failedCompilation(GraphDiagnostic diagnostic) {
        return new Compilation(List.of(), Map.of(), Map.of(), Map.of(),
                BehaviorTreePlan.RootSchedule.DEFAULT, List.of(diagnostic));
    }

    private static GraphDiagnostic diagnostic(String assetId, String code, String message,
                                              String nodeId, String portId,
                                              String relatedNodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, portId, relatedNodeId);
    }

    private record InputKey(String nodeId, String portId) {
    }

    private record DataLink(String sourceNodeId, String sourcePortId) {
    }

    private record NodeInfo(NodeData node, NodeCapabilities capabilities, PortCatalog ports) {
    }

    private record Compilation(List<String> nodeIds,
                               Map<String, NodeInfo> info, Map<String, List<String>> structure,
                               Map<InputKey, DataLink> inbound,
                               BehaviorTreePlan.RootSchedule rootSchedule,
                               List<GraphDiagnostic> diagnostics) {
    }

    private record PortCatalog(Map<String, PortDef> inputs, Map<String, PortDef> outputs) {
        private static PortCatalog from(NodeDef definition) {
            Map<String, PortDef> inputs = new LinkedHashMap<>();
            Map<String, PortDef> outputs = new LinkedHashMap<>();
            for (PortRow row : definition.rows()) {
                addPort(row.leftPort(), inputs);
                addPort(row.rightPort(), outputs);
            }
            return new PortCatalog(
                    Collections.unmodifiableMap(new LinkedHashMap<>(inputs)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(outputs)));
        }

        private static void addPort(@Nullable PortDef port, Map<String, PortDef> target) {
            if (port == null) return;
            if (port.id() == null || port.id().isBlank() || port.type() == null) return;
            target.putIfAbsent(port.id(), port);
        }
    }
}
