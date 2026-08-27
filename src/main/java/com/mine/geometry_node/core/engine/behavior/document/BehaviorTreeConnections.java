package com.mine.geometry_node.core.engine.behavior.document;

import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the behavior hierarchy directly from explicit structure-port connections. */
public final class BehaviorTreeConnections {
    private BehaviorTreeConnections() {
    }

    public static List<String> childrenOf(NodeGraph graph, String parentId) {
        NodeData parent = graph != null ? graph.getNode(parentId) : null;
        if (parent == null || parent.behaviorOutputs == null) return List.of();
        return parent.behaviorOutputs.entrySet().stream()
                .filter(entry -> BehaviorNodeTypes.isChildPort(entry.getKey()))
                .filter(entry -> entry.getValue() != null && entry.getValue().isValid())
                .sorted(Comparator.comparingInt(entry ->
                        BehaviorNodeTypes.childPortIndex(entry.getKey())))
                .map(entry -> entry.getValue().targetNodeId())
                .toList();
    }

    public static Map<String, List<String>> relationships(NodeGraph graph) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (graph == null || graph.nodes == null) return result;
        graph.nodes.keySet().stream().sorted().forEach(parentId -> {
            List<String> children = childrenOf(graph, parentId);
            if (!children.isEmpty()) result.put(parentId, children);
        });
        return result;
    }

    public static ParentConnection parentOf(NodeGraph graph, String childId) {
        if (graph == null || graph.nodes == null || childId == null) return null;
        for (String parentId : graph.nodes.keySet().stream().sorted().toList()) {
            NodeData parent = graph.nodes.get(parentId);
            if (parent == null || parent.behaviorOutputs == null) continue;
            for (Map.Entry<String, Connection> entry : parent.behaviorOutputs.entrySet()) {
                Connection connection = entry.getValue();
                if (BehaviorNodeTypes.isChildPort(entry.getKey()) && connection != null
                        && childId.equals(connection.targetNodeId())) {
                    return new ParentConnection(parentId, entry.getKey(), connection);
                }
            }
        }
        return null;
    }

    public static int connectionCountUpTo(NodeGraph graph, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        int count = 0;
        if (graph == null || graph.nodes == null) return count;
        for (NodeData node : graph.nodes.values()) {
            if (node == null || node.behaviorOutputs == null) continue;
            count += node.behaviorOutputs.size();
            if (count > limit) return limit + 1;
        }
        return count;
    }

    public record ParentConnection(String parentId, String portId, Connection connection) {
    }
}
