package com.mine.geometry_node.core.engine.graph;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical registry for graph types stored in graph JSON. Types are kept in
 * registration order so the editor presents a stable, intentional menu.
 */
public final class GraphTypeRegistry {
    public static final GraphType BLUEPRINT = new GraphType(
            "blueprint", "geometry_node.graph_properties.kind.blueprint", 0xFF88CCFF, GraphKind.BLUEPRINT, true);
    public static final GraphType QUEST = new GraphType(
            "quest", "geometry_node.graph_properties.kind.quest", 0xFFFF9E3D, GraphKind.BLUEPRINT, true);
    public static final GraphType BEHAVIOR_TREE = new GraphType(
            "behavior_tree", "geometry_node.graph_properties.kind.behavior_tree", 0xFF5C9E72,
            GraphKind.BEHAVIOR_TREE, false);

    public static final GraphTypeRegistry INSTANCE = new GraphTypeRegistry();

    private final Map<String, GraphType> types = new LinkedHashMap<>();

    private GraphTypeRegistry() {
        register(BLUEPRINT);
        register(QUEST);
        register(BEHAVIOR_TREE);
    }

    public synchronized void register(GraphType type) {
        if (type == null) return;
        GraphType existing = types.get(type.id());
        if (existing != null && !existing.equals(type)) {
            throw new IllegalStateException("Duplicate graph type: " + type.id());
        }
        types.put(type.id(), type);
    }

    @Nullable
    public synchronized GraphType get(@Nullable String id) {
        return types.get(GraphType.normalizeId(id));
    }

    public synchronized Collection<GraphType> all() {
        return Collections.unmodifiableList(new ArrayList<>(types.values()));
    }

    public synchronized Collection<GraphType> authorable() {
        List<GraphType> result = new ArrayList<>();
        for (GraphType type : types.values()) {
            if (type.authorable()) result.add(type);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized GraphType require(@Nullable String id) {
        GraphType type = get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown graph type: " + id);
        }
        return type;
    }
}
