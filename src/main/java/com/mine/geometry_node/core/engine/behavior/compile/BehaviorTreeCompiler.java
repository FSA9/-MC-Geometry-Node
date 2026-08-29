package com.mine.geometry_node.core.engine.behavior.compile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.behavior.structure.BehaviorTreeConnections;
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
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.TypeConverter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles editable behavior documents into immutable runtime plans. */
public final class BehaviorTreeCompiler implements GraphCompiler<BehaviorTreePlan> {
    public static final BehaviorTreeCompiler INSTANCE = new BehaviorTreeCompiler(NodeCatalog.REGISTRY);
    private static final Gson GSON = new Gson();

    private final NodeCatalog nodes;

    private BehaviorTreeCompiler(NodeCatalog nodes) {
        this.nodes = nodes;
    }

    static BehaviorTreeCompiler forTesting(NodeCatalog nodes) {
        return new BehaviorTreeCompiler(nodes);
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

    public GraphValidationResult validate(GraphCompileContext context, JsonObject document) {
        return new GraphValidationResult(inspectDocument(context, document).diagnostics);
    }

    private Compilation inspectDocument(GraphCompileContext context, JsonObject document) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        if (document == null) {
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MALFORMED",
                    "Behavior tree document is missing", "", "", ""));
        }
        try {
            FlattenedGraph flattened = GraphFlattener.flatten(document.getAsJsonObject("nodes"));
            return inspect(context, readDocument(document, flattened));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() != null ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MALFORMED",
                    "Behavior tree document cannot be decoded: " + reason, "", "", ""));
        }
    }

    private Compilation inspect(GraphCompileContext context, NodeGraph graph) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        List<GraphDiagnostic> diagnostics = new ArrayList<>();
        List<String> nodeIds = graph.nodes.keySet().stream().sorted().toList();

        Map<String, NodeInfo> info = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            NodeData node = graph.nodes.get(nodeId);
            if (node == null || node.type == null || !nodes.has(node.type)) {
                continue;
            }
            NodeCapabilities capabilities = nodes.capabilities(node.type);
            if (!capabilities.supports(GraphTypeRegistry.BEHAVIOR_TREE.id())) {
                continue;
            }
            NodeDef definition = nodes.definition(node);
            if (definition == null) continue;
            PortCatalog ports = PortCatalog.from(definition);
            info.put(nodeId, new NodeInfo(node, capabilities, ports));
        }

        Map<String, List<String>> structure = compileStructure(nodeIds, graph, info);
        Map<InputKey, DataLink> inbound = compileDataConnections(nodeIds, graph, info);
        GraphValidationResult common = GraphDocumentValidator.validate(validationInput(
                assetId, graph, nodeIds));
        diagnostics.addAll(common.diagnostics());
        BehaviorTreePlan.RootSchedule rootSchedule = compileRootSchedule(info);
        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId)
                .thenComparing(GraphDiagnostic::portId)
                .thenComparing(GraphDiagnostic::relatedNodeId)
                .thenComparing(GraphDiagnostic::message));
        return new Compilation(graph, nodeIds, info, structure, inbound, rootSchedule,
                List.copyOf(diagnostics));
    }

    private static Map<String, List<String>> compileStructure(
            List<String> nodeIds, NodeGraph graph, Map<String, NodeInfo> info) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        Set<String> claimedChildren = new HashSet<>();
        for (String sourceId : nodeIds) {
            NodeData source = graph.nodes.get(sourceId);
            NodeInfo sourceInfo = info.get(sourceId);
            if (source == null || sourceInfo == null || source.behaviorOutputs == null) continue;
            List<String> accepted = new ArrayList<>();
            List<String> behaviorPorts = source.behaviorOutputs.keySet().stream()
                    .sorted(Comparator.comparingInt((String portId) -> {
                                int index = BehaviorTreeConnections.childPortIndex(portId);
                                return index >= 0 ? index : Integer.MAX_VALUE;
                            })
                            .thenComparing(String::compareTo))
                    .toList();
            for (String sourcePortId : behaviorPorts) {
                Connection link = source.behaviorOutputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.ports.outputs.get(sourcePortId);
                if (sourcePort == null || sourcePort.type() != PortType.BEHAVIOR_STRUCTURE) {
                    continue;
                }
                if (link == null || !link.isValid()) continue;
                NodeInfo targetInfo = info.get(link.targetNodeId());
                PortDef targetPort = targetInfo != null
                        ? targetInfo.ports.inputs.get(link.targetPortName()) : null;
                if (targetPort == null || targetPort.type() != PortType.BEHAVIOR_STRUCTURE) continue;
                if (!claimedChildren.add(link.targetNodeId())) continue;
                if (reaches(children, link.targetNodeId(), sourceId)) {
                    claimedChildren.remove(link.targetNodeId());
                    continue;
                }
                accepted.add(link.targetNodeId());
            }
            if (!accepted.isEmpty()) children.put(sourceId, List.copyOf(accepted));
        }
        return Map.copyOf(children);
    }

    private static boolean reaches(Map<String, List<String>> children, String start, String target) {
        if (start.equals(target)) return true;
        List<String> pending = new ArrayList<>();
        pending.add(start);
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String current = pending.removeLast();
            if (!visited.add(current)) continue;
            if (current.equals(target)) return true;
            pending.addAll(children.getOrDefault(current, List.of()));
        }
        return false;
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
            List<String> nodeIds, NodeGraph graph, Map<String, NodeInfo> info) {
        Map<InputKey, DataLink> inbound = new LinkedHashMap<>();
        for (String sourceId : nodeIds) {
            NodeData source = graph.nodes.get(sourceId);
            NodeInfo sourceInfo = info.get(sourceId);
            if (source == null || sourceInfo == null || source.outputs == null) continue;
            for (String sourcePortId : new java.util.TreeSet<>(source.outputs.keySet())) {
                List<Connection> links = source.outputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.ports.outputs.get(sourcePortId);
                if (sourcePort == null || sourcePort.type().isFlow() || links == null) continue;
                List<Connection> ordered = links.stream()
                        .filter(link -> link != null && link.isValid())
                        .sorted(Comparator.comparing(Connection::targetNodeId)
                                .thenComparing(Connection::targetPortName)).toList();
                for (Connection link : ordered) {
                    NodeInfo targetInfo = info.get(link.targetNodeId());
                    if (targetInfo == null) continue;
                    PortDef targetPort = targetInfo.ports.inputs.get(link.targetPortName());
                    if (targetPort == null) continue;
                    if (targetPort.type().isFlow() || !PortType.isCompatible(sourcePort.type(), targetPort.type())) {
                        continue;
                    }
                    InputKey key = new InputKey(link.targetNodeId(), link.targetPortName());
                    inbound.putIfAbsent(key, new DataLink(sourceId, sourcePortId));
                }
            }
        }
        return inbound;
    }

    private static NodeGraph readDocument(JsonObject document, FlattenedGraph flattened) {
        NodeGraph graph = GSON.fromJson(document, NodeGraph.class);
        if (graph == null) graph = new NodeGraph();
        graph.graphKind = document.has("graph_kind") && document.get("graph_kind").isJsonPrimitive()
                ? document.get("graph_kind").getAsString() : GraphTypeRegistry.BEHAVIOR_TREE.id();
        graph.nodes = new LinkedHashMap<>();
        for (String nodeId : new java.util.TreeSet<>(flattened.nodes().keySet())) {
            NodeData node = GSON.fromJson(flattened.nodes().get(nodeId), NodeData.class);
            if (node == null) continue;
            node.id = nodeId;
            node.restoreDocumentDefaults();
            node.inputs = new LinkedHashMap<>(flattened.staticInputs()
                    .getOrDefault(nodeId, Map.of()));
            node.outputs = new LinkedHashMap<>();
            node.execOutputs = new LinkedHashMap<>();
            node.behaviorOutputs = new LinkedHashMap<>();
            flattened.behaviorOutputs().getOrDefault(nodeId, Map.of()).forEach(
                    (portId, target) -> node.behaviorOutputs.put(portId,
                            new Connection(target.targetNodeId(), target.targetPortName())));
            graph.nodes.put(nodeId, node);
        }
        for (Map.Entry<String, GraphFlattener.DataConnectionSource> entry
                : flattened.dataInputs().entrySet()) {
            int separator = entry.getKey().lastIndexOf('#');
            if (separator <= 0 || separator >= entry.getKey().length() - 1) continue;
            String targetNodeId = entry.getKey().substring(0, separator);
            String targetPortId = entry.getKey().substring(separator + 1);
            GraphFlattener.DataConnectionSource source = entry.getValue();
            NodeData sourceNode = graph.nodes.get(source.sourceNodeId());
            if (sourceNode != null && graph.nodes.containsKey(targetNodeId)) {
                sourceNode.addDataConnection(source.sourcePortName(), targetNodeId, targetPortId);
            }
        }
        return graph;
    }

    private static int storedConnectionCount(NodeGraph graph) {
        long count = 0;
        count = BehaviorTreeConnections.connectionCountUpTo(
                graph, GraphDocumentValidator.MAX_CONNECTIONS);
        if (count > GraphDocumentValidator.MAX_CONNECTIONS) {
            return GraphDocumentValidator.MAX_CONNECTIONS + 1;
        }
        for (NodeData node : graph.nodes.values()) {
            if (node == null) continue;
            if (node.execOutputs != null) count += node.execOutputs.size();
            if (node.outputs != null) {
                for (List<Connection> connections : node.outputs.values()) {
                    if (connections != null) count += connections.size();
                }
            }
            if (count > GraphDocumentValidator.MAX_CONNECTIONS) {
                return GraphDocumentValidator.MAX_CONNECTIONS + 1;
            }
        }
        return (int) count;
    }

    private static GraphDocumentValidator.Input validationInput(
            String assetId, NodeGraph graph, List<String> nodeIds) {
        List<GraphDocumentValidator.Node> nodes = nodeIds.stream()
                .map(nodeId -> new GraphDocumentValidator.Node(nodeId,
                        graph.nodes.get(nodeId) != null ? graph.nodes.get(nodeId).type : null))
                .toList();
        return new GraphDocumentValidator.Input(assetId, graph.getGraphTypeId(), nodes,
                storedConnectionCount(graph));
    }

    private static Compilation failedCompilation(GraphDiagnostic diagnostic) {
        NodeGraph graph = new NodeGraph();
        graph.graphKind = GraphTypeRegistry.BEHAVIOR_TREE.id();
        return new Compilation(graph, List.of(), Map.of(), Map.of(), Map.of(),
                BehaviorTreePlan.RootSchedule.DEFAULT, List.of(diagnostic));
    }

    private static GraphDiagnostic diagnostic(String assetId, String code, String message,
                                              String nodeId, String portId,
                                              String relatedNodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, portId, relatedNodeId);
    }

    interface NodeCatalog {
        NodeCatalog REGISTRY = new NodeCatalog() {
            @Override public boolean has(String typeId) { return NodeRegistry.INSTANCE.has(typeId); }
            @Override public NodeCapabilities capabilities(String typeId) {
                return NodeRegistry.INSTANCE.getCapabilities(typeId);
            }
            @Override public NodeDef definition(NodeData node) {
                return NodeRegistry.INSTANCE.resolveDefinition(node);
            }
        };

        boolean has(String typeId);
        NodeCapabilities capabilities(String typeId);
        @Nullable NodeDef definition(NodeData node);
    }

    private record InputKey(String nodeId, String portId) {
    }

    private record DataLink(String sourceNodeId, String sourcePortId) {
    }

    private record NodeInfo(NodeData node, NodeCapabilities capabilities, PortCatalog ports) {
    }

    private record Compilation(NodeGraph graph, List<String> nodeIds,
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
            return new PortCatalog(Map.copyOf(inputs), Map.copyOf(outputs));
        }

        private static void addPort(@Nullable PortDef port, Map<String, PortDef> target) {
            if (port == null) return;
            if (port.id() == null || port.id().isBlank() || port.type() == null) return;
            target.putIfAbsent(port.id(), port);
        }
    }
}
