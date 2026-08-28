package com.mine.geometry_node.core.engine.graph.compile.validation;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.node.NodeRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minimal fatal validation shared by every executable graph compiler. */
public final class GraphDocumentValidator {
    public static final int MAX_NODES = 8_192;
    public static final int MAX_CONNECTIONS = 32_768;

    private GraphDocumentValidator() {
    }

    public static GraphValidationResult validate(Input input) {
        List<GraphDiagnostic> diagnostics = new ArrayList<>();
        String assetId = input.assetId();
        if (input.nodes().size() > MAX_NODES) {
            diagnostics.add(diagnostic(assetId, "NODE_LIMIT_EXCEEDED",
                    "Graph contains more than " + MAX_NODES + " nodes", ""));
        }
        if (input.connectionCount() > MAX_CONNECTIONS) {
            diagnostics.add(diagnostic(assetId, "CONNECTION_LIMIT_EXCEEDED",
                    "Graph contains more than " + MAX_CONNECTIONS + " connections", ""));
        }

        GraphType graphType = GraphTypeRegistry.INSTANCE.get(input.graphTypeId());
        for (Node node : input.nodes()) {
            String typeId = node.typeId();
            if (typeId == null || typeId.isBlank() || !NodeRegistry.INSTANCE.has(typeId)) {
                diagnostics.add(diagnostic(assetId, "NODE_TYPE_MISSING",
                        "Node type is missing or unavailable", node.id()));
            } else if (graphType != null
                    && !NodeRegistry.INSTANCE.getCapabilities(typeId).supports(graphType.id())) {
                diagnostics.add(diagnostic(assetId, "NODE_GRAPH_TYPE_UNSUPPORTED",
                        "Node type is not available in " + graphType.id(), node.id()));
            }
        }

        String cycleNode = findDataCycle(input.nodes(), input.dataEdges());
        if (cycleNode != null) {
            diagnostics.add(diagnostic(assetId, "DATA_DEPENDENCY_CYCLE",
                    "Data dependency cycle includes node", cycleNode));
        }
        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId));
        return new GraphValidationResult(diagnostics);
    }

    public static void requireValid(Input input) {
        GraphValidationResult result = validate(input);
        if (!result.isValid()) throw new GraphValidationException(result.diagnostics());
    }

    private static String findDataCycle(Collection<Node> nodes, Collection<DataEdge> edges) {
        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Integer> inbound = new HashMap<>();
        for (Node node : nodes) inbound.put(node.id(), 0);
        for (DataEdge edge : edges) {
            if (!inbound.containsKey(edge.sourceNodeId()) || !inbound.containsKey(edge.targetNodeId())) continue;
            if (outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new HashSet<>())
                    .add(edge.targetNodeId())) {
                inbound.computeIfPresent(edge.targetNodeId(), (ignored, count) -> count + 1);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        inbound.forEach((nodeId, count) -> {
            if (count == 0) ready.addLast(nodeId);
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String nodeId = ready.removeFirst();
            visited++;
            for (String target : outgoing.getOrDefault(nodeId, Set.of())) {
                int remaining = inbound.computeIfPresent(target, (ignored, count) -> count - 1);
                if (remaining == 0) ready.addLast(target);
            }
        }
        if (visited == inbound.size()) return null;
        return inbound.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey).sorted().findFirst().orElse("");
    }

    private static GraphDiagnostic diagnostic(String assetId, String code,
                                              String message, String nodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, "", "");
    }

    public record Input(String assetId, String graphTypeId, Collection<Node> nodes,
                        Collection<DataEdge> dataEdges, int connectionCount) {
        public Input {
            assetId = assetId != null ? assetId : "";
            graphTypeId = graphTypeId != null ? graphTypeId : "";
            nodes = nodes != null ? List.copyOf(nodes) : List.of();
            dataEdges = dataEdges != null ? List.copyOf(dataEdges) : List.of();
            connectionCount = Math.max(0, connectionCount);
        }
    }

    public record Node(String id, String typeId) {
    }

    public record DataEdge(String sourceNodeId, String targetNodeId) {
    }
}
