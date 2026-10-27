package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.graph.GraphKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Immutable compiled view of one server's graph repository. */
final class GraphRepositorySnapshot {
    static final GraphRepositorySnapshot EMPTY = new GraphRepositorySnapshot(Map.of());

    private final Map<String, GraphAssetDescriptor> graphs;
    private final Map<GraphKind, Set<String>> graphIdsByKind;

    GraphRepositorySnapshot(Map<String, GraphAssetDescriptor> graphs) {
        Map<String, GraphAssetDescriptor> canonical = new LinkedHashMap<>();
        for (GraphAssetDescriptor descriptor : graphs.values()) {
            GraphAssetDescriptor previous = canonical.putIfAbsent(descriptor.graphId(), descriptor);
            if (previous != null && previous != descriptor) {
                throw new IllegalArgumentException(
                        "Duplicate canonical graph id: " + descriptor.graphId());
            }
        }
        this.graphs = Map.copyOf(canonical);
        Map<GraphKind, Set<String>> byKind = new EnumMap<>(GraphKind.class);
        for (GraphAssetDescriptor descriptor : canonical.values()) {
            byKind.computeIfAbsent(descriptor.runtimeKind(), ignored -> new TreeSet<>())
                    .add(descriptor.graphId());
        }
        byKind.replaceAll((kind, ids) -> Collections.unmodifiableSet(ids));
        graphIdsByKind = Map.copyOf(byKind);
    }

    Map<String, GraphAssetDescriptor> graphs() {
        return graphs;
    }

    Set<String> graphIds(GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        return graphIdsByKind.getOrDefault(runtimeKind, Set.of());
    }
}
