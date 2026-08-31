package com.mine.geometry_node.core.engine.graph.compile.validation;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph;
import com.mine.geometry_node.core.node.NodeRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

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

        for (Node node : input.nodes()) {
            String typeId = node.typeId();
            if (typeId == null || typeId.isBlank() || !NodeRegistry.INSTANCE.has(typeId)) {
                diagnostics.add(diagnostic(assetId, "NODE_TYPE_MISSING",
                        "Node type is missing or unavailable", node.id()));
            }
        }

        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId));
        return new GraphValidationResult(diagnostics);
    }

    public static void requireValid(Input input) {
        GraphValidationResult result = validate(input);
        if (!result.isValid()) throw new GraphValidationException(result.diagnostics());
    }

    public static Input input(String assetId, FlattenedGraph flattened) {
        List<Node> nodes = flattened.nodes().entrySet().stream()
                .map(entry -> new Node(entry.getKey(), readNodeType(entry.getValue())))
                .toList();
        long connectionCount = flattened.dataInputs().size();
        connectionCount += flattened.executionOutputs().values().stream()
                .mapToLong(java.util.Map::size).sum();
        int boundedCount = connectionCount > MAX_CONNECTIONS
                ? MAX_CONNECTIONS + 1 : (int) connectionCount;
        return new Input(assetId, nodes, boundedCount);
    }

    private static String readNodeType(JsonObject node) {
        return node != null && node.has("node_type") && node.get("node_type").isJsonPrimitive()
                ? node.get("node_type").getAsString() : null;
    }

    private static GraphDiagnostic diagnostic(String assetId, String code,
                                              String message, String nodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, "", "");
    }

    public record Input(String assetId, Collection<Node> nodes,
                        int connectionCount) {
        public Input {
            assetId = assetId != null ? assetId : "";
            nodes = nodes != null ? List.copyOf(nodes) : List.of();
            connectionCount = Math.max(0, connectionCount);
        }
    }

    public record Node(String id, String typeId) {
    }
}
