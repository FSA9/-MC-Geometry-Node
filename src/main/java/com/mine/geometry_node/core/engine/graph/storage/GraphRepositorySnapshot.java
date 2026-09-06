package com.mine.geometry_node.core.engine.graph.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable compiled view of one server's graph repository. */
record GraphRepositorySnapshot(Map<String, GraphAssetDescriptor> graphs) {
    static final GraphRepositorySnapshot EMPTY = new GraphRepositorySnapshot(Map.of());

    GraphRepositorySnapshot {
        Map<String, GraphAssetDescriptor> canonical = new LinkedHashMap<>();
        for (GraphAssetDescriptor descriptor : graphs.values()) {
            GraphAssetDescriptor previous = canonical.putIfAbsent(descriptor.graphId(), descriptor);
            if (previous != null && previous != descriptor) {
                throw new IllegalArgumentException(
                        "Duplicate canonical graph id: " + descriptor.graphId());
            }
        }
        graphs = Map.copyOf(canonical);
    }
}
