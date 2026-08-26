package com.mine.geometry_node.core.engine.graph.binding;

import com.mine.geometry_node.core.engine.graph.GraphKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Mutable host binding partition with read-only views for runtime-specific callers. */
public final class GraphBindingSet {
    private final Set<GraphBindingKey> bindings = new LinkedHashSet<>();
    private final Map<GraphKind, Set<String>> graphIdsByKind = new EnumMap<>(GraphKind.class);

    public boolean add(GraphBindingKey key) {
        if (!bindings.add(key)) return false;
        graphIdsByKind.computeIfAbsent(key.kind(), ignored -> new LinkedHashSet<>()).add(key.graphId());
        return true;
    }

    public boolean remove(GraphBindingKey key) {
        if (!bindings.remove(key)) return false;
        Set<String> graphIds = graphIdsByKind.get(key.kind());
        if (graphIds != null) {
            graphIds.remove(key.graphId());
            if (graphIds.isEmpty()) graphIdsByKind.remove(key.kind());
        }
        return true;
    }

    public boolean contains(GraphBindingKey key) {
        return bindings.contains(key);
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    public void clear() {
        bindings.clear();
        graphIdsByKind.clear();
    }

    public boolean clear(GraphKind kind) {
        Set<String> graphIds = graphIdsByKind.remove(kind);
        if (graphIds == null || graphIds.isEmpty()) return false;
        bindings.removeIf(binding -> binding.kind() == kind);
        return true;
    }

    public Set<GraphBindingKey> all() {
        return Collections.unmodifiableSet(bindings);
    }

    public Set<String> graphIds(GraphKind kind) {
        Set<String> result = graphIdsByKind.get(kind);
        return result != null ? Collections.unmodifiableSet(result) : Set.of();
    }
}
