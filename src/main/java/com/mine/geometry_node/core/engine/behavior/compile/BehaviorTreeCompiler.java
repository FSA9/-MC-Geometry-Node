package com.mine.geometry_node.core.engine.behavior.compile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorValueSemantics;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorBlackboardDeclaration;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeConnections;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeCall;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeParameter;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeDiagnostic;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeValidationResult;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompileContext;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.BehaviorTreeStructure;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Compiles editable behavior documents into immutable runtime plans. */
public final class BehaviorTreeCompiler implements GraphCompiler<BehaviorTreePlan> {
    public static final BehaviorTreeCompiler INSTANCE = new BehaviorTreeCompiler(NodeCatalog.REGISTRY);
    private static final Gson GSON = new Gson();
    private static final int MAX_DIAGNOSTICS = 256;
    private static final int MAX_COMPILED_NODES = 8_192;
    private static final int MAX_COMPILED_CONNECTIONS = 32_768;
    private static final String NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_.-]{0,63}";

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
            throw new BehaviorCompilationException(compilation.diagnostics);
        }
        return buildPlan(context, compilation);
    }

    public BehaviorTreeValidationResult validate(GraphCompileContext context, JsonObject document) {
        return new BehaviorTreeValidationResult(inspectDocument(context, document).diagnostics);
    }

    private Compilation inspectDocument(GraphCompileContext context, JsonObject document) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        if (document == null) {
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MISSING",
                    "Behavior tree document is missing", "", "", ""));
        }
        try {
            return inspect(context, readDocument(document));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() != null ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            return failedCompilation(diagnostic(assetId, "DOCUMENT_MALFORMED",
                    "Behavior tree document cannot be decoded: " + reason, "", "", ""));
        }
    }

    private Compilation inspect(GraphCompileContext context, NodeGraph graph) {
        String assetId = context != null ? context.diagnosticAssetId() : "<anonymous>";
        List<BehaviorTreeDiagnostic> diagnostics = new ArrayList<>();
        List<String> nodeIds = graph.nodes.keySet().stream().sorted().toList();
        if (nodeIds.size() > MAX_COMPILED_NODES) {
            return failedCompilation(diagnostic(assetId, "NODE_LIMIT_EXCEEDED",
                    "Behavior tree contains more than " + MAX_COMPILED_NODES + " nodes",
                    "", "", ""));
        }
        if (storedConnectionCount(graph) > MAX_COMPILED_CONNECTIONS) {
            return failedCompilation(diagnostic(assetId, "CONNECTION_LIMIT_EXCEEDED",
                    "Behavior tree contains more than " + MAX_COMPILED_CONNECTIONS + " connections",
                    "", "", ""));
        }

        Map<String, NodeInfo> info = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            NodeData node = graph.nodes.get(nodeId);
            if (node == null || node.type == null || !nodes.has(node.type)) {
                add(diagnostics, diagnostic(assetId, "NODE_TYPE_MISSING",
                        "Node type is missing or unavailable", nodeId, "", ""));
                continue;
            }
            NodeCapabilities capabilities = nodes.capabilities(node.type);
            if (!capabilities.supports(GraphTypeRegistry.BEHAVIOR_TREE.id())) {
                add(diagnostics, diagnostic(assetId, "NODE_GRAPH_TYPE_UNSUPPORTED",
                        "Node type is not available in behavior trees: " + node.type,
                        nodeId, "", ""));
                continue;
            }
            NodeDef definition = nodes.definition(node);
            if (definition == null) {
                add(diagnostics, diagnostic(assetId, "NODE_DEFINITION_MISSING",
                        "Node type did not provide a definition", nodeId, "", ""));
                continue;
            }
            PortCatalog ports = PortCatalog.from(assetId, nodeId, definition, diagnostics);
            info.put(nodeId, new NodeInfo(node, capabilities, ports));
            validateCapabilities(assetId, nodeId, capabilities, diagnostics);
            validateStoredInputs(assetId, nodeId, node, ports, diagnostics);
            if (node.execOutputs != null && !node.execOutputs.isEmpty()) {
                node.execOutputs.keySet().stream().sorted().forEach(portId -> add(diagnostics,
                        diagnostic(assetId, "EXECUTION_CONNECTION_FORBIDDEN",
                                "Behavior trees cannot store blueprint execution-flow connections",
                                nodeId, portId, "")));
            }
        }

        validateStructureConnections(assetId, nodeIds, graph, info, diagnostics);
        Map<InputKey, DataLink> inbound = validateDataConnections(assetId, nodeIds, graph, info, diagnostics);
        BehaviorTreePlan.RootSchedule rootSchedule = compileRootSchedule(
                assetId, info, inbound, diagnostics);
        validateRequiredInputs(assetId, info, inbound, diagnostics);
        validateDataCycles(assetId, nodeIds, inbound, diagnostics);
        validateDepth(assetId, graph, diagnostics);
        BehaviorTreePlan.BlackboardSchema blackboard = compileBlackboard(
                assetId, graph.behaviorTree, diagnostics);
        BehaviorTreePlan.SubtreeSignature signature = compileSubtreeSignature(
                assetId, graph.behaviorTree, blackboard, diagnostics);
        BehaviorTreePlan.DependencyManifest dependencies = compileDependencies(
                assetId, context, info, blackboard, diagnostics);

        diagnostics.sort(Comparator.comparing(BehaviorTreeDiagnostic::code)
                .thenComparing(BehaviorTreeDiagnostic::nodeId)
                .thenComparing(BehaviorTreeDiagnostic::portId)
                .thenComparing(BehaviorTreeDiagnostic::relatedNodeId)
                .thenComparing(BehaviorTreeDiagnostic::message));
        return new Compilation(graph, nodeIds, info, inbound, blackboard, signature, dependencies,
                rootSchedule,
                List.copyOf(diagnostics));
    }

    private static void validateStructureConnections(
            String assetId, List<String> nodeIds, NodeGraph graph, Map<String, NodeInfo> info,
            List<BehaviorTreeDiagnostic> diagnostics) {
        for (String sourceId : nodeIds) {
            NodeData source = graph.nodes.get(sourceId);
            NodeInfo sourceInfo = info.get(sourceId);
            if (source == null || sourceInfo == null || source.behaviorOutputs == null) continue;
            for (String sourcePortId : new java.util.TreeSet<>(source.behaviorOutputs.keySet())) {
                Connection link = source.behaviorOutputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.ports.outputs.get(sourcePortId);
                if (sourcePort == null || sourcePort.type() != PortType.BEHAVIOR_STRUCTURE) {
                    add(diagnostics, diagnostic(assetId, "STRUCTURE_OUTPUT_PORT_INVALID",
                            "Stored behavior connection references an unavailable structure output",
                            sourceId, sourcePortId, ""));
                    continue;
                }
                if (link == null || !link.isValid()) {
                    add(diagnostics, diagnostic(assetId, "STRUCTURE_CONNECTION_MALFORMED",
                            "Behavior connection requires a target node and input port",
                            sourceId, sourcePortId, ""));
                    continue;
                }
                NodeInfo targetInfo = info.get(link.targetNodeId());
                PortDef targetPort = targetInfo != null
                        ? targetInfo.ports.inputs.get(link.targetPortName()) : null;
                if (targetPort == null || targetPort.type() != PortType.BEHAVIOR_STRUCTURE) {
                    add(diagnostics, diagnostic(assetId, "STRUCTURE_INPUT_PORT_INVALID",
                            "Behavior connection references an unavailable structure input",
                            link.targetNodeId(), link.targetPortName(), sourceId));
                }
            }
        }
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
        @SuppressWarnings("unchecked") Map<String, CompiledDataIndex.DataConnectionSource>[] dataInputs = new Map[size];
        @SuppressWarnings("unchecked") Set<String>[] ports = new Set[size];
        Map<String, List<Integer>> nodesByType = new LinkedHashMap<>();
        Set<String> allPortNames = new java.util.TreeSet<>();
        int root = -1;

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            String nodeId = ids[nodeIndex];
            NodeInfo nodeInfo = compilation.info.get(nodeId);
            types[nodeIndex] = nodeInfo.node.type;
            capabilities[nodeIndex] = nodeInfo.capabilities;
            nodesByType.computeIfAbsent(nodeInfo.node.type, ignored -> new ArrayList<>()).add(nodeIndex);
            if (BehaviorNodeTypes.ROOT.equals(nodeInfo.node.type)) root = nodeIndex;

            List<String> childIds = BehaviorTreeConnections.childrenOf(compilation.graph, nodeId);
            children[nodeIndex] = childIds.stream().mapToInt(indexes::get).toArray();
            for (int child : children[nodeIndex]) parents[child] = nodeIndex;

            Map<String, Object> effectiveInputs = new LinkedHashMap<>();
            for (PortDef input : nodeInfo.ports.inputs.values()) {
                Object value = nodeInfo.node.inputs.containsKey(input.id())
                        ? nodeInfo.node.inputs.get(input.id()) : input.defaultValue();
                if (value != null) effectiveInputs.put(input.id(),
                        BehaviorValueSemantics.freezeAs(value, input.type()));
            }
            staticInputs[nodeIndex] = Map.copyOf(effectiveInputs);

            Map<String, CompiledDataIndex.DataConnectionSource> inputIndex = new LinkedHashMap<>();
            for (Map.Entry<InputKey, DataLink> entry : compilation.inbound.entrySet()) {
                if (!entry.getKey().nodeId.equals(nodeId)) continue;
                DataLink link = entry.getValue();
                inputIndex.put(entry.getKey().portId,
                        new CompiledDataIndex.DataConnectionSource(indexes.get(link.sourceNodeId), link.sourcePortId));
            }
            dataInputs[nodeIndex] = Map.copyOf(inputIndex);
            Set<String> nodePorts = new LinkedHashSet<>();
            nodePorts.addAll(nodeInfo.ports.inputs.keySet());
            nodePorts.addAll(nodeInfo.ports.outputs.keySet());
            ports[nodeIndex] = Set.copyOf(nodePorts);
            allPortNames.addAll(nodePorts);
        }

        List<String> portNames = List.copyOf(allPortNames);
        Map<String, Integer> portKeys = new LinkedHashMap<>();
        for (int i = 0; i < portNames.size(); i++) portKeys.put(portNames.get(i), i);
        return BehaviorTreePlan.createCompiled(
                context != null ? context.assetId() : "", ids, indexes, types, capabilities,
                root, parents, children, staticInputs, dataInputs, ports, portKeys, portNames,
                nodesByType, compilation.blackboard, compilation.dependencies,
                compilation.subtreeSignature, compilation.rootSchedule);
    }

    private static BehaviorTreePlan.RootSchedule compileRootSchedule(
            String assetId, Map<String, NodeInfo> info, Map<InputKey, DataLink> inbound,
            List<BehaviorTreeDiagnostic> diagnostics) {
        Map.Entry<String, NodeInfo> rootEntry = null;
        for (Map.Entry<String, NodeInfo> entry : info.entrySet()) {
            if (!BehaviorNodeTypes.ROOT.equals(entry.getValue().node.type)) continue;
            rejectDynamicRootSchedule(assetId, entry.getKey(),
                    BehaviorNodeTypes.RECHECK_INTERVAL_PORT, inbound, diagnostics);
            rejectDynamicRootSchedule(assetId, entry.getKey(),
                    BehaviorNodeTypes.SCHEDULE_OFFSET_PORT, inbound, diagnostics);
            // buildPlan uses the last Root in the same deterministic node order.
            rootEntry = entry;
        }
        if (rootEntry == null) return BehaviorTreePlan.RootSchedule.DEFAULT;

        String nodeId = rootEntry.getKey();
        NodeInfo root = rootEntry.getValue();
        int interval = staticInteger(root, BehaviorNodeTypes.RECHECK_INTERVAL_PORT, 1);
        int offset = staticInteger(root, BehaviorNodeTypes.SCHEDULE_OFFSET_PORT,
                BehaviorTreePlan.RootSchedule.AUTO_OFFSET);
        if (interval < 1) {
            add(diagnostics, diagnostic(assetId, "ROOT_RECHECK_INTERVAL_INVALID",
                    "Root recheck interval must be at least 1", nodeId,
                    BehaviorNodeTypes.RECHECK_INTERVAL_PORT, ""));
            interval = 1;
        }
        if (offset < BehaviorTreePlan.RootSchedule.AUTO_OFFSET) {
            add(diagnostics, diagnostic(assetId, "ROOT_SCHEDULE_OFFSET_INVALID",
                    "Root schedule offset must be -1 (automatic) or greater", nodeId,
                    BehaviorNodeTypes.SCHEDULE_OFFSET_PORT, ""));
            offset = BehaviorTreePlan.RootSchedule.AUTO_OFFSET;
        }
        return new BehaviorTreePlan.RootSchedule(interval, offset);
    }

    private static void rejectDynamicRootSchedule(
            String assetId, String nodeId, String portId, Map<InputKey, DataLink> inbound,
            List<BehaviorTreeDiagnostic> diagnostics) {
        if (inbound.containsKey(new InputKey(nodeId, portId))) {
            add(diagnostics, diagnostic(assetId, "ROOT_SCHEDULE_STATIC_REQUIRED",
                    "Root scheduling inputs must use static values", nodeId, portId, ""));
        }
    }

    private static int staticInteger(NodeInfo node, String portId, int fallback) {
        Object value = node.node.inputs.get(portId);
        if (value == null) {
            PortDef port = node.ports.inputs.get(portId);
            value = port != null ? port.defaultValue() : null;
        }
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Map<InputKey, DataLink> validateDataConnections(
            String assetId, List<String> nodeIds, NodeGraph graph, Map<String, NodeInfo> info,
            List<BehaviorTreeDiagnostic> diagnostics) {
        Map<InputKey, DataLink> inbound = new LinkedHashMap<>();
        for (String sourceId : nodeIds) {
            NodeData source = graph.nodes.get(sourceId);
            NodeInfo sourceInfo = info.get(sourceId);
            if (source == null || sourceInfo == null || source.outputs == null) continue;
            for (String sourcePortId : new java.util.TreeSet<>(source.outputs.keySet())) {
                List<Connection> links = source.outputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.ports.outputs.get(sourcePortId);
                if (sourcePort == null) {
                    add(diagnostics, diagnostic(assetId, "DATA_OUTPUT_PORT_MISSING",
                            "Stored data connection references an unavailable output port",
                            sourceId, sourcePortId, ""));
                    continue;
                }
                if (sourcePort.type().isFlow()) {
                    add(diagnostics, diagnostic(assetId, "DATA_OUTPUT_PORT_INVALID",
                            "Structural or execution ports cannot be stored as data connections",
                            sourceId, sourcePortId, ""));
                    continue;
                }
                if (links == null) {
                    add(diagnostics, diagnostic(assetId, "DATA_CONNECTION_MALFORMED",
                            "Data connection list is missing", sourceId, sourcePortId, ""));
                    continue;
                }
                for (Connection link : links) {
                    if (link == null || !link.isValid()) {
                        add(diagnostics, diagnostic(assetId, "DATA_CONNECTION_MALFORMED",
                                "Data connection requires a target node and input port",
                                sourceId, sourcePortId, ""));
                    }
                }
                List<Connection> ordered = links.stream()
                        .filter(link -> link != null && link.isValid())
                        .sorted(Comparator.comparing(Connection::targetNodeId)
                                .thenComparing(Connection::targetPortName)).toList();
                for (Connection link : ordered) {
                    NodeInfo targetInfo = info.get(link.targetNodeId());
                    if (targetInfo == null) {
                        add(diagnostics, diagnostic(assetId, "DATA_TARGET_NODE_MISSING",
                                "Data connection target node is missing or unavailable",
                                sourceId, sourcePortId, link.targetNodeId()));
                        continue;
                    }
                    PortDef targetPort = targetInfo.ports.inputs.get(link.targetPortName());
                    if (targetPort == null) {
                        add(diagnostics, diagnostic(assetId, "DATA_INPUT_PORT_MISSING",
                                "Data connection target input port is unavailable",
                                link.targetNodeId(), link.targetPortName(), sourceId));
                        continue;
                    }
                    if (targetPort.type().isFlow() || !PortType.isCompatible(sourcePort.type(), targetPort.type())) {
                        add(diagnostics, diagnostic(assetId, "DATA_PORT_TYPE_MISMATCH",
                                "Data connection types are incompatible: " + sourcePort.type()
                                        + " -> " + targetPort.type(),
                                link.targetNodeId(), link.targetPortName(), sourceId));
                        continue;
                    }
                    InputKey key = new InputKey(link.targetNodeId(), link.targetPortName());
                    DataLink previous = inbound.putIfAbsent(key,
                            new DataLink(sourceId, sourcePortId));
                    if (previous != null) {
                        add(diagnostics, diagnostic(assetId, "DATA_INPUT_MULTIPLE_SOURCES",
                                "Data input has more than one source", link.targetNodeId(),
                                link.targetPortName(), sourceId));
                    }
                }
            }
        }
        return inbound;
    }

    private static void validateStoredInputs(String assetId, String nodeId, NodeData node,
                                             PortCatalog ports,
                                             List<BehaviorTreeDiagnostic> diagnostics) {
        if (node.inputs == null) return;
        for (String portId : new java.util.TreeSet<>(node.inputs.keySet())) {
            if (StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id().equals(portId)
                    || StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id().equals(portId)) continue;
            PortDef port = ports.inputs.get(portId);
            if (port == null) {
                add(diagnostics, diagnostic(assetId, "STORED_INPUT_PORT_MISSING",
                        "Stored value references an unavailable input port", nodeId, portId, ""));
                continue;
            }
            Object value = node.inputs.get(portId);
            if (value == null || !BehaviorValueSemantics.matches(value, port.type())) {
                add(diagnostics, diagnostic(assetId, "STORED_INPUT_TYPE_INVALID",
                        "Stored value is not compatible with input type " + port.type(),
                        nodeId, portId, ""));
            }
        }
    }

    private static void validateRequiredInputs(String assetId, Map<String, NodeInfo> info,
                                               Map<InputKey, DataLink> inbound,
                                               List<BehaviorTreeDiagnostic> diagnostics) {
        for (Map.Entry<String, NodeInfo> entry : info.entrySet()) {
            String nodeId = entry.getKey();
            NodeInfo nodeInfo = entry.getValue();
            for (PortDef input : nodeInfo.ports.inputs.values()) {
                if (input.type().isFlow() || input.defaultValue() != null
                        || nodeInfo.node.inputs.containsKey(input.id())
                        || inbound.containsKey(new InputKey(nodeId, input.id()))) continue;
                add(diagnostics, diagnostic(assetId, "REQUIRED_INPUT_MISSING",
                        "Input requires a stored value or data connection", nodeId, input.id(), ""));
            }
        }
    }

    private static void validateDataCycles(String assetId, List<String> nodeIds,
                                           Map<InputKey, DataLink> inbound,
                                           List<BehaviorTreeDiagnostic> diagnostics) {
        Map<String, Set<String>> outgoing = new TreeMap<>();
        Map<String, Integer> inboundCounts = new TreeMap<>();
        nodeIds.forEach(nodeId -> inboundCounts.put(nodeId, 0));
        inbound.forEach((target, source) -> {
            if (outgoing.computeIfAbsent(source.sourceNodeId,
                    ignored -> new java.util.TreeSet<>()).add(target.nodeId)) {
                inboundCounts.computeIfPresent(target.nodeId, (ignored, count) -> count + 1);
            }
        });
        Deque<String> ready = new ArrayDeque<>();
        inboundCounts.forEach((nodeId, count) -> {
            if (count == 0) ready.addLast(nodeId);
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String nodeId = ready.removeFirst();
            visited++;
            for (String target : outgoing.getOrDefault(nodeId, Set.of())) {
                int remaining = inboundCounts.computeIfPresent(target,
                        (ignored, count) -> count - 1);
                if (remaining == 0) ready.addLast(target);
            }
        }
        if (visited != nodeIds.size()) {
            String cycleNode = inboundCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey)
                    .findFirst().orElse("");
            add(diagnostics, diagnostic(assetId, "DATA_DEPENDENCY_CYCLE",
                    "Data dependency cycle includes node", cycleNode, "", ""));
        }
    }

    private static void validateCapabilities(String assetId, String nodeId,
                                             NodeCapabilities capabilities,
                                             List<BehaviorTreeDiagnostic> diagnostics) {
        if (!capabilities.supports(GraphTypeRegistry.BEHAVIOR_TREE.id())) return;
        if (capabilities.context() != NodeCapabilities.Context.DATA
                && capabilities.context() != NodeCapabilities.Context.BEHAVIOR_EXECUTION) {
            add(diagnostics, diagnostic(assetId, "NODE_CONTEXT_INVALID",
                    "Behavior-tree nodes must use data or behavior execution context",
                    nodeId, "", ""));
            return;
        }
        if (capabilities.context() == NodeCapabilities.Context.DATA) {
            if (capabilities.purity() != NodeCapabilities.Purity.PURE
                    && capabilities.purity() != NodeCapabilities.Purity.READ_ONLY) {
                add(diagnostics, diagnostic(assetId, "DATA_NODE_PURITY_INVALID",
                        "Shared data nodes must be pure or read-only", nodeId, "", ""));
            }
            if (!capabilities.children().equals(NodeCapabilities.ChildConstraint.LEAF)) {
                add(diagnostics, diagnostic(assetId, "DATA_NODE_CHILDREN_INVALID",
                        "Data nodes cannot own behavior children", nodeId, "", ""));
            }
            if (capabilities.lifecycle() != NodeCapabilities.Lifecycle.INSTANT
                    || capabilities.cancellation() != NodeCapabilities.Cancellation.NOT_APPLICABLE) {
                add(diagnostics, diagnostic(assetId, "DATA_NODE_LIFECYCLE_INVALID",
                        "Shared data nodes must be instant and non-cancellable", nodeId, "", ""));
            }
            if (!capabilities.resources().equals(Set.of(NodeCapabilities.ResourceUse.NONE))) {
                add(diagnostics, diagnostic(assetId, "DATA_NODE_RESOURCE_INVALID",
                        "Data nodes cannot lease behavior resources", nodeId, "", ""));
            }
        }
        if (capabilities.context() == NodeCapabilities.Context.BEHAVIOR_EXECUTION
                && capabilities.permissions().contains(NodeCapabilities.Permission.UNSPECIFIED)) {
            add(diagnostics, diagnostic(assetId, "BEHAVIOR_PERMISSION_UNSPECIFIED",
                    "Behavior nodes must explicitly declare permissions", nodeId, "", ""));
        }
        if (capabilities.context() == NodeCapabilities.Context.BEHAVIOR_EXECUTION
                && !capabilities.children().equals(NodeCapabilities.ChildConstraint.LEAF)) {
            if (!capabilities.resources().equals(Set.of(NodeCapabilities.ResourceUse.NONE))) {
                add(diagnostics, diagnostic(assetId, "CONTROL_NODE_RESOURCE_INVALID",
                        "Behavior control nodes cannot lease action resources", nodeId, "", ""));
            }
            if (!capabilities.permissions().equals(Set.of(NodeCapabilities.Permission.NONE))) {
                add(diagnostics, diagnostic(assetId, "CONTROL_NODE_PERMISSION_INVALID",
                        "Behavior control nodes cannot declare world side effects", nodeId, "", ""));
            }
        }
    }

    private static void validateDepth(String assetId, NodeGraph graph,
                                      List<BehaviorTreeDiagnostic> diagnostics) {
        String root = graph.nodes.entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && BehaviorNodeTypes.ROOT.equals(entry.getValue().type))
                .map(Map.Entry::getKey).sorted().findFirst().orElse(null);
        if (root == null) return;
        int depth = maxDepth(root, graph);
        if (depth > BehaviorRuntimeBudget.DEFAULT.maxTreeDepth()) {
            add(diagnostics, diagnostic(assetId, "TREE_DEPTH_EXCEEDED",
                    "Tree depth " + depth + " exceeds limit "
                            + BehaviorRuntimeBudget.DEFAULT.maxTreeDepth(), root, "", ""));
        }
    }

    private static int maxDepth(String rootId, NodeGraph graph) {
        int maximum = 0;
        Set<String> visited = new HashSet<>();
        Deque<NodeDepth> pending = new ArrayDeque<>();
        pending.push(new NodeDepth(rootId, 1));
        while (!pending.isEmpty()) {
            NodeDepth current = pending.pop();
            if (!visited.add(current.nodeId)) continue;
            maximum = Math.max(maximum, current.depth);
            for (String child : BehaviorTreeConnections.childrenOf(graph, current.nodeId)) {
                pending.push(new NodeDepth(child, current.depth + 1));
            }
        }
        return maximum;
    }

    private static BehaviorTreePlan.BlackboardSchema compileBlackboard(
            String assetId, @Nullable BehaviorTreeStructure structure,
            List<BehaviorTreeDiagnostic> diagnostics) {
        if (structure == null) return BehaviorTreePlan.BlackboardSchema.EMPTY;
        List<BehaviorBlackboardDeclaration> declarations = structure.blackboardDeclarations();
        if (declarations.size() > BehaviorRuntimeBudget.DEFAULT.maxBlackboardEntriesPerInstance()) {
            add(diagnostics, diagnostic(assetId, "BLACKBOARD_LIMIT_EXCEEDED",
                    "Blackboard declaration count exceeds "
                            + BehaviorRuntimeBudget.DEFAULT.maxBlackboardEntriesPerInstance(), "", "", ""));
        }
        List<BehaviorTreePlan.BlackboardKey> compiled = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (BehaviorBlackboardDeclaration declaration : declarations) {
            if (declaration == null) continue;
            String name = declaration.name != null ? declaration.name.trim() : "";
            if (!name.matches(NAME_PATTERN)) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_NAME_INVALID",
                        "Blackboard input name is invalid: " + name, "", name, ""));
                continue;
            }
            BlackboardScope scope;
            PortType type;
            try {
                scope = BlackboardScope.valueOf(text(declaration.scope).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_SCOPE_INVALID",
                        "Unknown blackboard scope: " + declaration.scope, "", name, ""));
                continue;
            }
            try {
                type = PortType.valueOf(text(declaration.type).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_TYPE_INVALID",
                        "Unknown blackboard value type: " + declaration.type, "", name, ""));
                continue;
            }
            if (type.isFlow() || type == PortType.ANY) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_TYPE_INVALID",
                        "Blackboard keys require a concrete data type", "", name, ""));
                continue;
            }
            String identity = scope.name() + '\0' + name;
            if (!keys.add(identity)) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_KEY_DUPLICATED",
                        "Blackboard input is declared more than once in the same scope", "", name, ""));
                continue;
            }
            if (declaration.defaultValue != null
                    && !BehaviorValueSemantics.matches(declaration.defaultValue, type)) {
                add(diagnostics, diagnostic(assetId, "BLACKBOARD_DEFAULT_TYPE_INVALID",
                        "Blackboard default does not match " + type, "", name, ""));
                continue;
            }
            compiled.add(new BehaviorTreePlan.BlackboardKey(name, scope, type,
                    declaration.writable, declaration.defaultValue != null
                    ? BehaviorValueSemantics.freezeAs(declaration.defaultValue, type) : null));
        }
        return new BehaviorTreePlan.BlackboardSchema(compiled);
    }

    private static BehaviorTreePlan.SubtreeSignature compileSubtreeSignature(
            String assetId, @Nullable BehaviorTreeStructure structure,
            BehaviorTreePlan.BlackboardSchema blackboard,
            List<BehaviorTreeDiagnostic> diagnostics) {
        if (structure == null) return BehaviorTreePlan.SubtreeSignature.EMPTY;
        List<BehaviorTreePlan.SubtreeParameter> compiled = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (BehaviorSubtreeParameter declaration : structure.parameters()) {
            String name = text(declaration.name).trim();
            String key = text(declaration.blackboardKey).trim();
            if (!name.matches(NAME_PATTERN) || !key.matches(NAME_PATTERN)) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_PARAMETER_INVALID",
                        "Subtree parameter names and blackboard keys must be valid identifiers",
                        "", name, ""));
                continue;
            }
            if (!names.add(name)) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_PARAMETER_DUPLICATED",
                        "Subtree parameter is declared more than once", "", name, ""));
                continue;
            }
            BehaviorTreePlan.BlackboardKey blackboardKey = blackboard.find(BlackboardScope.INSTANCE, key);
            if (blackboardKey == null) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_PARAMETER_KEY_MISSING",
                        "Subtree parameter references an undeclared INSTANCE blackboard key",
                        "", name, key));
                continue;
            }
            PortType type = declaration.type != null ? declaration.type : PortType.ANY;
            if (type != PortType.ANY && type != blackboardKey.type()) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_PARAMETER_TYPE_MISMATCH",
                        "Subtree parameter type does not match its blackboard key",
                        "", name, key));
                continue;
            }
            if (declaration.direction == BehaviorSubtreeParameter.Direction.OUTPUT
                    && !blackboardKey.writable()) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_OUTPUT_READ_ONLY",
                        "Subtree output must reference a writable blackboard key",
                        "", name, key));
                continue;
            }
            compiled.add(new BehaviorTreePlan.SubtreeParameter(name, declaration.direction,
                    blackboardKey.type(), key));
        }
        compiled.sort(Comparator.comparing(BehaviorTreePlan.SubtreeParameter::name));
        return new BehaviorTreePlan.SubtreeSignature(compiled);
    }

    private static BehaviorTreePlan.DependencyManifest compileDependencies(
            String assetId, GraphCompileContext context, Map<String, NodeInfo> info,
            BehaviorTreePlan.BlackboardSchema blackboard,
            List<BehaviorTreeDiagnostic> diagnostics) {
        List<BehaviorTreePlan.SubtreeDependency> compiled = new ArrayList<>();
        for (Map.Entry<String, NodeInfo> entry : info.entrySet()) {
            if (!BehaviorNodeTypes.SUBTREE.equals(entry.getValue().node.type)) continue;
            String callNodeId = entry.getKey();
            NodeData node = entry.getValue().node;
            Object rawDependencyId = node.inputs.get(BehaviorNodeTypes.SUBTREE_ASSET_PORT);
            String dependencyId = text(rawDependencyId instanceof String value ? value : "");
            BehaviorSubtreeCall call = node.behaviorSubtree != null
                    ? node.behaviorSubtree : new BehaviorSubtreeCall();
            call.restoreDocumentDefaults();
            if (dependencyId.isEmpty()) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_ASSET_ID_MISSING",
                        "Subtree call requires an asset id", callNodeId,
                        BehaviorNodeTypes.SUBTREE_ASSET_PORT, ""));
                continue;
            }
            if (context != null && !context.assetId().isEmpty()
                    && context.assetId().equals(dependencyId)) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_RECURSION",
                        "Behavior tree cannot directly call itself", callNodeId,
                        BehaviorNodeTypes.SUBTREE_ASSET_PORT, dependencyId));
                continue;
            }
            if (!validMapping(call.inputMapping) || !validMapping(call.outputMapping)) {
                add(diagnostics, diagnostic(assetId, "SUBTREE_MAPPING_INVALID",
                        "Subtree parameter mappings require valid non-empty names",
                        callNodeId, "", dependencyId));
                continue;
            }
            boolean localKeysValid = true;
            for (String localKey : call.inputMapping.values()) {
                if (blackboard.find(BlackboardScope.INSTANCE, localKey) == null) {
                    add(diagnostics, diagnostic(assetId, "SUBTREE_INPUT_SOURCE_MISSING",
                            "Subtree input mapping references an undeclared caller blackboard key",
                            callNodeId, localKey, dependencyId));
                    localKeysValid = false;
                }
            }
            for (String localKey : call.outputMapping.keySet()) {
                BehaviorTreePlan.BlackboardKey key = blackboard.find(BlackboardScope.INSTANCE, localKey);
                if (key == null || !key.writable()) {
                    add(diagnostics, diagnostic(assetId, "SUBTREE_OUTPUT_TARGET_INVALID",
                            "Subtree output target must be a writable caller blackboard key",
                            callNodeId, localKey, dependencyId));
                    localKeysValid = false;
                }
            }
            if (!localKeysValid) continue;
            compiled.add(new BehaviorTreePlan.SubtreeDependency(callNodeId, dependencyId,
                    new TreeMap<>(call.inputMapping), new TreeMap<>(call.outputMapping)));
        }
        compiled.sort(Comparator.comparing(BehaviorTreePlan.SubtreeDependency::callNodeId));
        return new BehaviorTreePlan.DependencyManifest(compiled);
    }

    private static boolean validMapping(Map<String, String> mapping) {
        if (mapping == null) return false;
        return mapping.entrySet().stream().allMatch(entry -> entry.getKey() != null
                && entry.getValue() != null && entry.getKey().matches(NAME_PATTERN)
                && entry.getValue().matches(NAME_PATTERN));
    }

    private static NodeGraph readDocument(JsonObject document) {
        NodeGraph graph = GSON.fromJson(document, NodeGraph.class);
        if (graph == null) graph = new NodeGraph();
        graph.graphKind = document.has("graph_kind") && document.get("graph_kind").isJsonPrimitive()
                ? document.get("graph_kind").getAsString() : GraphTypeRegistry.BLUEPRINT.id();
        if (graph.nodes == null) graph.nodes = new LinkedHashMap<>();
        JsonElement nodesElement = document.get("nodes");
        if (nodesElement != null && nodesElement.isJsonObject()) {
            graph.nodes = new LinkedHashMap<>();
            for (String nodeId : new java.util.TreeSet<>(nodesElement.getAsJsonObject().keySet())) {
                NodeData node = GSON.fromJson(nodesElement.getAsJsonObject().get(nodeId), NodeData.class);
                if (node == null) continue;
                node.id = nodeId;
                node.restoreDocumentDefaults();
                graph.nodes.put(nodeId, node);
            }
        }
        if (graph.behaviorTree != null) graph.behaviorTree.restoreDocumentDefaults();
        return graph;
    }

    private static String text(@Nullable String value) {
        return value != null ? value.trim() : "";
    }

    private static int storedConnectionCount(NodeGraph graph) {
        long count = 0;
        count = BehaviorTreeConnections.connectionCountUpTo(graph, MAX_COMPILED_CONNECTIONS);
        if (count > MAX_COMPILED_CONNECTIONS) return MAX_COMPILED_CONNECTIONS + 1;
        for (NodeData node : graph.nodes.values()) {
            if (node == null || node.outputs == null) continue;
            for (List<Connection> connections : node.outputs.values()) {
                if (connections == null) continue;
                count += connections.size();
                if (count > MAX_COMPILED_CONNECTIONS) return MAX_COMPILED_CONNECTIONS + 1;
            }
        }
        return (int) count;
    }

    private static Compilation failedCompilation(BehaviorTreeDiagnostic diagnostic) {
        NodeGraph graph = new NodeGraph();
        graph.graphKind = GraphTypeRegistry.BEHAVIOR_TREE.id();
        return new Compilation(graph, List.of(), Map.of(), Map.of(),
                BehaviorTreePlan.BlackboardSchema.EMPTY,
                BehaviorTreePlan.SubtreeSignature.EMPTY,
                BehaviorTreePlan.DependencyManifest.EMPTY,
                BehaviorTreePlan.RootSchedule.DEFAULT, List.of(diagnostic));
    }

    private static void add(List<BehaviorTreeDiagnostic> diagnostics,
                            BehaviorTreeDiagnostic diagnostic) {
        if (diagnostics.size() < MAX_DIAGNOSTICS) diagnostics.add(diagnostic);
    }

    private static BehaviorTreeDiagnostic diagnostic(String assetId, String code, String message,
                                                     String nodeId, String portId,
                                                     String relatedNodeId) {
        return new BehaviorTreeDiagnostic(assetId, code, message, nodeId, portId, relatedNodeId);
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

    private record NodeDepth(String nodeId, int depth) {
    }

    private record DataLink(String sourceNodeId, String sourcePortId) {
    }

    private record NodeInfo(NodeData node, NodeCapabilities capabilities, PortCatalog ports) {
    }

    private record Compilation(NodeGraph graph, List<String> nodeIds,
                               Map<String, NodeInfo> info, Map<InputKey, DataLink> inbound,
                               BehaviorTreePlan.BlackboardSchema blackboard,
                               BehaviorTreePlan.SubtreeSignature subtreeSignature,
                               BehaviorTreePlan.DependencyManifest dependencies,
                               BehaviorTreePlan.RootSchedule rootSchedule,
                               List<BehaviorTreeDiagnostic> diagnostics) {
    }

    private record PortCatalog(Map<String, PortDef> inputs, Map<String, PortDef> outputs) {
        private static PortCatalog from(String assetId, String nodeId, NodeDef definition,
                                        List<BehaviorTreeDiagnostic> diagnostics) {
            Map<String, PortDef> inputs = new LinkedHashMap<>();
            Map<String, PortDef> outputs = new LinkedHashMap<>();
            for (PortRow row : definition.rows()) {
                addPort(assetId, nodeId, row.leftPort(), inputs, "input", diagnostics);
                addPort(assetId, nodeId, row.rightPort(), outputs, "output", diagnostics);
            }
            return new PortCatalog(Map.copyOf(inputs), Map.copyOf(outputs));
        }

        private static void addPort(String assetId, String nodeId, @Nullable PortDef port,
                                    Map<String, PortDef> target, String direction,
                                    List<BehaviorTreeDiagnostic> diagnostics) {
            if (port == null) return;
            if (port.id() == null || port.id().isBlank() || port.type() == null) {
                add(diagnostics, diagnostic(assetId, "PORT_DEFINITION_INVALID",
                        "Node has an invalid " + direction + " port definition",
                        nodeId, port.id(), ""));
                return;
            }
            if (target.putIfAbsent(port.id(), port) != null) {
                add(diagnostics, diagnostic(assetId, "PORT_DEFINITION_DUPLICATED",
                        "Node declares the same " + direction + " port more than once",
                        nodeId, port.id(), ""));
            }
            if ("input".equals(direction) && port.defaultValue() != null
                    && !BehaviorValueSemantics.matches(port.defaultValue(), port.type())) {
                add(diagnostics, diagnostic(assetId, "PORT_DEFAULT_TYPE_INVALID",
                        "Node input default is not compatible with " + port.type(),
                        nodeId, port.id(), ""));
            }
        }
    }
}
