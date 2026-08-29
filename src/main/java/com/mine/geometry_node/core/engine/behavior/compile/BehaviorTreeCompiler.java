package com.mine.geometry_node.core.engine.behavior.compile;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.engine.graph.compile.CompiledNodeTable;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompileContext;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.engine.graph.compile.GraphFlattener;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDiagnostic;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphDocumentValidator;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphValidationException;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphValidationResult;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorRootNode;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles editable behavior documents into immutable runtime plans. */
public final class BehaviorTreeCompiler implements GraphCompiler<BehaviorTreePlan> {
    public static final BehaviorTreeCompiler INSTANCE = new BehaviorTreeCompiler();

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
        String graphTypeId = readGraphType(document);
        GraphValidationResult common = GraphDocumentValidator.validate(
                GraphDocumentValidator.input(assetId, graphTypeId, flattened));
        diagnostics.addAll(common.diagnostics());
        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId)
                .thenComparing(GraphDiagnostic::portId)
                .thenComparing(GraphDiagnostic::relatedNodeId)
                .thenComparing(GraphDiagnostic::message));
        if (!diagnostics.isEmpty()) return failedCompilation(diagnostics);

        CompiledNodeTable nodes = CompiledNodeTable.build(flattened);
        Map<String, List<String>> structure = compileStructure(nodes.nodeIds(), flattened, nodes);
        BehaviorTreePlan.RootSchedule rootSchedule = compileRootSchedule(nodes);
        return new Compilation(nodes, structure, rootSchedule, List.of());
    }

    private static Map<String, List<String>> compileStructure(
            List<String> nodeIds, FlattenedGraph flattened, CompiledNodeTable nodes) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (String sourceId : nodeIds) {
            CompiledNodeTable.NodeDescriptor sourceInfo = nodes.descriptor(sourceId);
            Map<String, FlattenedGraph.TargetConnection> outputs =
                    flattened.executionOutputs().get(sourceId);
            if (sourceInfo == null || outputs == null) continue;
            List<String> accepted = new ArrayList<>();
            for (String sourcePortId : sourceInfo.outputs().keySet()) {
                FlattenedGraph.TargetConnection link = outputs.get(sourcePortId);
                PortDef sourcePort = sourceInfo.outputs().get(sourcePortId);
                if (sourcePort == null || sourcePort.type() != PortType.BEHAVIOR_STRUCTURE) {
                    continue;
                }
                if (!isValid(link)) continue;
                CompiledNodeTable.NodeDescriptor targetInfo = nodes.descriptor(link.targetNodeId());
                PortDef targetPort = targetInfo != null
                        ? targetInfo.inputs().get(link.targetPortName()) : null;
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
        CompiledNodeIndex nodes = compilation.nodes.index();
        int size = nodes.getNodeCount();
        @SuppressWarnings("unchecked") Set<NodeCapabilities.ResourceUse>[] resources = new Set[size];
        int[] parents = new int[size];
        java.util.Arrays.fill(parents, -1);
        int[][] children = new int[size][];
        int root = -1;

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            String nodeId = nodes.getNodeId(nodeIndex);
            CompiledNodeTable.NodeDescriptor descriptor = compilation.nodes.descriptor(nodeId);
            NodeCapabilities capabilities = descriptor.capabilities();
            resources[nodeIndex] = capabilities != null ? capabilities.resources() : Set.of();
            if (root < 0 && BehaviorRootNode.TYPE_ID.equals(descriptor.type())) root = nodeIndex;

            List<String> childIds = compilation.structure.getOrDefault(nodeId, List.of());
            children[nodeIndex] = childIds.stream().mapToInt(nodes::getNodeKey).toArray();
            for (int child : children[nodeIndex]) parents[child] = nodeIndex;
        }
        return BehaviorTreePlan.createCompiled(
                context != null ? context.assetId() : "", nodes, resources,
                root, parents, children,
                compilation.rootSchedule);
    }

    private static BehaviorTreePlan.RootSchedule compileRootSchedule(CompiledNodeTable table) {
        CompiledNodeTable.NodeDescriptor root = null;
        for (String nodeId : table.nodeIds()) {
            CompiledNodeTable.NodeDescriptor descriptor = table.descriptor(nodeId);
            if (!BehaviorRootNode.TYPE_ID.equals(descriptor.type())) continue;
            root = descriptor;
            break;
        }
        if (root == null) return BehaviorTreePlan.RootSchedule.DEFAULT;

        int interval = Math.max(1, table.index().getStaticInput(root.index(),
                BehaviorRootNode.RECHECK_TICK_PORT, Integer.class, 1));
        int offset = table.index().getStaticInput(root.index(), BehaviorRootNode.SCHEDULE_TICK_PORT,
                Integer.class, BehaviorTreePlan.RootSchedule.AUTO_OFFSET);
        offset = Math.max(BehaviorTreePlan.RootSchedule.AUTO_OFFSET, offset);
        return new BehaviorTreePlan.RootSchedule(interval, offset);
    }

    private static String readGraphType(JsonObject document) {
        if (document.has("graph_kind") && document.get("graph_kind").isJsonPrimitive()) {
            return document.get("graph_kind").getAsString();
        }
        return GraphTypeRegistry.BEHAVIOR_TREE.id();
    }

    private static Compilation failedCompilation(GraphDiagnostic diagnostic) {
        return failedCompilation(List.of(diagnostic));
    }

    private static Compilation failedCompilation(List<GraphDiagnostic> diagnostics) {
        return new Compilation(null, Map.of(), BehaviorTreePlan.RootSchedule.DEFAULT,
                List.copyOf(diagnostics));
    }

    private static GraphDiagnostic diagnostic(String assetId, String code, String message,
                                              String nodeId, String portId,
                                              String relatedNodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, portId, relatedNodeId);
    }

    private record Compilation(@Nullable CompiledNodeTable nodes,
                               Map<String, List<String>> structure,
                               BehaviorTreePlan.RootSchedule rootSchedule,
                               List<GraphDiagnostic> diagnostics) {
    }
}
