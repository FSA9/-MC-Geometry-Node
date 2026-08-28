package com.mine.geometry_node.core.engine.graph.compile.validation;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
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

        diagnostics.sort(Comparator.comparing(GraphDiagnostic::code)
                .thenComparing(GraphDiagnostic::nodeId));
        return new GraphValidationResult(diagnostics);
    }

    public static void requireValid(Input input) {
        GraphValidationResult result = validate(input);
        if (!result.isValid()) throw new GraphValidationException(result.diagnostics());
    }

    private static GraphDiagnostic diagnostic(String assetId, String code,
                                              String message, String nodeId) {
        return new GraphDiagnostic(assetId, code, message, nodeId, "", "");
    }

    public record Input(String assetId, String graphTypeId, Collection<Node> nodes,
                        int connectionCount) {
        public Input {
            assetId = assetId != null ? assetId : "";
            graphTypeId = graphTypeId != null ? graphTypeId : "";
            nodes = nodes != null ? List.copyOf(nodes) : List.of();
            connectionCount = Math.max(0, connectionCount);
        }
    }

    public record Node(String id, String typeId) {
    }
}
