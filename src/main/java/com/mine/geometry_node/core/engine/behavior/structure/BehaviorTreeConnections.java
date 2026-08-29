package com.mine.geometry_node.core.engine.behavior.structure;

import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.port.StandardPorts;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queries ordered behavior-tree relationships stored in one editable graph scope. */
public final class BehaviorTreeConnections {
    private BehaviorTreeConnections() {
    }

    public static List<String> childrenOf(NodeGraph graph, String parentId) {
        NodeData parent = graph != null ? graph.getNode(parentId) : null;
        if (parent == null || parent.behaviorOutputs == null) return List.of();
        return parent.behaviorOutputs.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isValid())
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, Connection> entry) -> {
                            int index = childPortIndex(entry.getKey());
                            return index >= 0 ? index : Integer.MAX_VALUE;
                        })
                        .thenComparing(Map.Entry::getKey))
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
        return findParent(graph, childId, null, false);
    }

    public static ParentConnection parentOfInput(NodeGraph graph, String childId, String inputPortId) {
        return findParent(graph, childId, inputPortId, true);
    }

    private static ParentConnection findParent(NodeGraph graph, String childId,
                                               String inputPortId, boolean matchInputPort) {
        if (graph == null || graph.nodes == null || childId == null) return null;
        for (String parentId : graph.nodes.keySet().stream().sorted().toList()) {
            NodeData parent = graph.nodes.get(parentId);
            if (parent == null || parent.behaviorOutputs == null) continue;
            for (Map.Entry<String, Connection> entry : parent.behaviorOutputs.entrySet()) {
                Connection connection = entry.getValue();
                if (connection != null && childId.equals(connection.targetNodeId())
                        && (!matchInputPort || java.util.Objects.equals(
                        inputPortId, connection.targetPortName()))) {
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

    public static String childPort(int index) {
        if (index < 1) throw new IllegalArgumentException("Behavior child index must be positive");
        return StandardPorts.BEHAVIOR_CHILDREN.getIdWithIndex(index);
    }

    public static int childPortIndex(String portId) {
        String childrenPort = StandardPorts.BEHAVIOR_CHILDREN.getId();
        if (childrenPort.equals(portId)) return 0;
        String prefix = childrenPort + "_";
        if (portId == null || !portId.startsWith(prefix)) return -1;
        try {
            int index = Integer.parseInt(portId.substring(prefix.length()));
            return index > 0 ? index - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isChildPort(String portId) {
        return childPortIndex(portId) >= 0;
    }

    public record ParentConnection(String parentId, String portId, Connection connection) {
    }
}
