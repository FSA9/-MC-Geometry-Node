package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local binding draft. The server-provided available list is the add-binding capability boundary. */
final class EntityGraphBindings {
    private final LinkedHashSet<GraphBindingKey> baseline = new LinkedHashSet<>();
    private final LinkedHashSet<GraphBindingKey> bound = new LinkedHashSet<>();
    private final LinkedHashMap<GraphBindingKey, String> available = new LinkedHashMap<>();

    EntityGraphBindings(List<Map<String, Object>> bindings, List<Map<String, Object>> choices) {
        if (bindings != null) for (var entry : bindings) {
            GraphBindingKey key = key(entry);
            if (key != null) bound.add(key);
        }
        baseline.addAll(bound);
        if (choices != null) for (var entry : choices) {
            GraphBindingKey key = key(entry);
            if (key != null) available.putIfAbsent(key,
                    entry.get("name") instanceof String name && !name.isBlank() ? name : key.graphId());
        }
    }

    static GraphBindingKey key(Map<String, Object> entry) {
        if (entry == null || !(entry.get("id") instanceof String id)
                || !(entry.get("kind") instanceof String kind)) return null;
        // Network snapshots use GraphKind.name(); accepting id() also supports local UI maps.
        GraphKind parsed;
        try { parsed = GraphKind.valueOf(kind); }
        catch (IllegalArgumentException ignored) { parsed = GraphKind.fromId(kind); }
        if (parsed == GraphKind.UNKNOWN) return null;
        try { return new GraphBindingKey(parsed, id); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    List<GraphBindingKey> keys() { return List.copyOf(bound); }
    List<GraphBindingKey> candidates() {
        return available.keySet().stream().filter(key -> !bound.contains(key)).toList();
    }
    String name(GraphBindingKey key) {
        String name = available.get(key);
        if (name != null && !name.equals(key.graphId())) return name;
        String path = key.graphId();
        int slash = path.lastIndexOf('/');
        return path.substring(slash + 1, path.length() - ".json".length());
    }
    boolean add(GraphBindingKey key) { return available.containsKey(key) && bound.add(key); }
    boolean remove(Set<GraphBindingKey> selected) { return bound.removeAll(selected); }
    boolean dirty() { return !baseline.equals(bound); }

    List<Map<String, Object>> graphs() {
        // Do not send presentation metadata such as name/selection back to the entity protocol.
        List<Map<String, Object>> result = new ArrayList<>();
        for (var key : bound) result.add(Map.of("id", key.graphId(), "kind", key.kind().name()));
        return List.copyOf(result);
    }
}
