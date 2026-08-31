package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.definition.node.NodeDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable connection indexes for one read-only view of the current graph. */
final class GraphReadSnapshot {
    record Edge(String kind, String outputNodeId, String outputPortId, String inputNodeId, String inputPortId) {
        static final Comparator<Edge> ORDER = Comparator.comparing(Edge::outputNodeId)
                .thenComparing(Edge::outputPortId).thenComparing(Edge::inputNodeId)
                .thenComparing(Edge::inputPortId).thenComparing(Edge::kind);
    }

    private final NodeGraph graph;
    private final Map<String, NodeData> nodes;
    private final Map<String, NodeDef> definitions;
    private final List<Edge> edges;
    private final Map<String, List<Edge>> outgoing;
    private final Map<String, List<Edge>> incoming;
    private final Map<String, List<Edge>> direct;

    private GraphReadSnapshot(NodeGraph graph, Map<String, NodeData> nodes, Map<String, NodeDef> definitions,
                              List<Edge> edges, Map<String, List<Edge>> outgoing,
                              Map<String, List<Edge>> incoming, Map<String, List<Edge>> direct) {
        this.graph = graph;
        this.nodes = Collections.unmodifiableMap(nodes);
        this.definitions = Collections.unmodifiableMap(definitions);
        this.edges = List.copyOf(edges);
        this.outgoing = freezeIndex(outgoing);
        this.incoming = freezeIndex(incoming);
        this.direct = freezeIndex(direct);
    }

    static GraphReadSnapshot capture(NodeGraph graph) {
        Map<String, NodeData> nodes = new LinkedHashMap<>();
        if (graph != null && graph.nodes != null) {
            graph.nodes.entrySet().stream().filter(entry -> entry.getKey() != null)
                    .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                        if (entry.getValue() != null) nodes.put(entry.getKey(), entry.getValue());
                    });
        }
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, NodeData> entry : nodes.entrySet()) {
            String sourceId = entry.getKey();
            NodeData node = entry.getValue();
            if (node.execOutputs != null) {
                node.execOutputs.forEach((portId, connection) -> addEdge(edges, "flow", sourceId, portId, connection));
            }
            if (node.outputs != null) {
                node.outputs.forEach((portId, connections) -> {
                    if (connections != null) {
                        for (Connection connection : connections) addEdge(edges, "data", sourceId, portId, connection);
                    }
                });
            }
        }
        edges.sort(Edge.ORDER);
        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<Edge>> incoming = new LinkedHashMap<>();
        Map<String, LinkedHashSet<Edge>> directSets = new LinkedHashMap<>();
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.outputNodeId(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.inputNodeId(), ignored -> new ArrayList<>()).add(edge);
            directSets.computeIfAbsent(edge.outputNodeId(), ignored -> new LinkedHashSet<>()).add(edge);
            directSets.computeIfAbsent(edge.inputNodeId(), ignored -> new LinkedHashSet<>()).add(edge);
        }
        Map<String, List<Edge>> direct = new LinkedHashMap<>();
        directSets.forEach((nodeId, nodeEdges) -> direct.put(nodeId, List.copyOf(nodeEdges)));
        Map<String, NodeDef> definitions = new LinkedHashMap<>();
        nodes.forEach((nodeId, node) -> {
            NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
            if (definition != null) definitions.put(nodeId, definition);
        });
        return new GraphReadSnapshot(graph, nodes, definitions, edges, outgoing, incoming, direct);
    }

    NodeGraph graph() { return graph; }

    Map<String, NodeData> nodes() { return nodes; }

    NodeData node(String nodeId) { return nodes.get(nodeId); }

    NodeDef definition(String nodeId) { return definitions.get(nodeId); }

    List<Edge> edges() { return edges; }

    List<Edge> outgoing(String nodeId) { return outgoing.getOrDefault(nodeId, List.of()); }

    List<Edge> incoming(String nodeId) { return incoming.getOrDefault(nodeId, List.of()); }

    List<Edge> direct(String nodeId) { return direct.getOrDefault(nodeId, List.of()); }

    Set<String> neighborhood(String nodeId, int depth) {
        if (!nodes.containsKey(nodeId)) return Set.of();
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        visited.add(nodeId);
        queue.add(new NodeDepth(nodeId, 0));
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (current.depth() >= depth) continue;
            for (Edge edge : outgoing(current.nodeId())) addNeighbor(edge.inputNodeId(), current.depth(), visited, queue);
            for (Edge edge : incoming(current.nodeId())) addNeighbor(edge.outputNodeId(), current.depth(), visited, queue);
        }
        return Set.copyOf(visited);
    }

    List<Edge> inducedEdges(Set<String> nodeIds) {
        return edges.stream().filter(edge -> nodeIds.contains(edge.outputNodeId()) && nodeIds.contains(edge.inputNodeId())).toList();
    }

    private static void addNeighbor(String nodeId, int parentDepth, Set<String> visited, ArrayDeque<NodeDepth> queue) {
        if (nodeId != null && visited.add(nodeId)) queue.addLast(new NodeDepth(nodeId, parentDepth + 1));
    }

    private static void addEdge(List<Edge> edges, String kind, String sourceId, String sourcePort, Connection target) {
        if (sourceId == null || sourceId.isBlank() || sourcePort == null || sourcePort.isBlank() || target == null) return;
        String targetNode = target.targetNodeId();
        String targetPort = target.targetPortName();
        if (targetNode == null || targetNode.isBlank() || targetPort == null || targetPort.isBlank()) return;
        edges.add(new Edge(kind, sourceId, sourcePort, targetNode, targetPort));
    }

    private static Map<String, List<Edge>> freezeIndex(Map<String, List<Edge>> source) {
        source.replaceAll((key, value) -> List.copyOf(value));
        return Collections.unmodifiableMap(source);
    }

    private record NodeDepth(String nodeId, int depth) {}
}
