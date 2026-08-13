package com.mine.geometry_node.client.model.runtime;

import java.util.Set;

/** Per-instance node boundary. M9 will add pose arrays and a world-matrix cache behind this owner. */
public record ModelInstanceNodeState(Set<Integer> hiddenNodes, long revision) {
    public static final ModelInstanceNodeState IDENTITY = new ModelInstanceNodeState(Set.of(), 0);

    public ModelInstanceNodeState {
        hiddenNodes = hiddenNodes == null ? Set.of() : Set.copyOf(hiddenNodes);
        if (hiddenNodes.stream().anyMatch(index -> index == null || index < 0)) {
            throw new IllegalArgumentException("hidden node indices must not be negative");
        }
        if (revision < 0) throw new IllegalArgumentException("node state revision must not be negative");
    }

    public boolean visible(int nodeIndex) { return !hiddenNodes.contains(nodeIndex); }

}
