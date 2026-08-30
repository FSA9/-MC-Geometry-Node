package com.mine.geometry_node.core.engine.graph.resource;

/** Selects one resource within a graph binding. */
public sealed interface GraphResourceSelector permits GraphResourceSelector.Graph, GraphResourceSelector.Node,
        GraphResourceSelector.Named {
    Kind kind();

    enum Kind { GRAPH, NODE, NAMED }

    record Graph() implements GraphResourceSelector {
        public static final Graph INSTANCE = new Graph();
        @Override public Kind kind() { return Kind.GRAPH; }
    }

    record Node(String stableNodeId) implements GraphResourceSelector {
        public Node {
            stableNodeId = requireValue(stableNodeId, "stableNodeId");
        }
        @Override public Kind kind() { return Kind.NODE; }
    }

    record Named(String key) implements GraphResourceSelector {
        public Named {
            key = requireValue(key, "key");
        }
        @Override public Kind kind() { return Kind.NAMED; }
    }

    private static String requireValue(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        if (normalized.length() > 512) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }
}
