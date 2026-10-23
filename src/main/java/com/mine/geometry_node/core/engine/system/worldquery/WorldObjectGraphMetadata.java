package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraph;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;

import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetDescriptor;
import java.util.*;
import java.util.function.Function;

/** Converts existing bindings using only the caller's in-memory descriptor lookup. */
final class WorldObjectGraphMetadata {
    private WorldObjectGraphMetadata() {}

    static List<WorldObjectGraph> collect(Collection<GraphBindingKey> bindings,
                                          Function<String, GraphAssetDescriptor> lookup) {
        // Legacy attachments may exceed editor limits. Fail the provider explicitly, never hide bindings.
        if (bindings.size() > WorldObjectEntry.MAX_GRAPHS)
            throw new IllegalArgumentException("Too many bound graphs for world query metadata");
        List<WorldObjectGraph> graphs = new ArrayList<>(bindings.size());
        int bytes = 256;
        for (var binding : bindings) {
            var descriptor = lookup.apply(binding.graphId());
            boolean current = descriptor != null && descriptor.graphId().equals(binding.graphId())
                    && descriptor.runtimeKind() == binding.kind();
            var graph = new WorldObjectGraph(binding.graphId(),
                    current ? descriptor.type().id() : binding.kind().id(),
                    current ? descriptor.name() : binding.graphId());
            bytes += graph.estimatedBytes();
            if (bytes > WorldObjectPage.MAX_ENTRY_BYTES)
                throw new IllegalArgumentException("Bound graph metadata byte budget exceeded");
            graphs.add(graph);
        }
        return List.copyOf(graphs);
    }
}
