package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.asset.type.AssetTypeCatalog;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local binding draft. Repository selections are advisory; the server validates every new binding. */
final class EntityGraphBindings {
    private final LinkedHashSet<GraphBindingKey> baseline = new LinkedHashSet<>();
    private final LinkedHashSet<GraphBindingKey> bound = new LinkedHashSet<>();
    private final Map<GraphBindingKey, String> names = new LinkedHashMap<>();
    private final Map<GraphBindingKey, Presentation> presentation = new LinkedHashMap<>();
    private final Map<GraphBindingKey, Item> picked = new LinkedHashMap<>();

    record Presentation(String type, List<String> tags) {
        Presentation { type = GraphType.normalizeId(type); tags = TagSet.normalize(tags, TagPolicy.EXACT); }
    }
    record Item(GraphBindingKey key, String name, Presentation presentation) {}

    EntityGraphBindings(List<Map<String, Object>> bindings, List<Map<String, Object>> choices) {
        refreshConfirmed(bindings, choices);
    }

    boolean refreshConfirmed(List<Map<String, Object>> bindings, List<Map<String, Object>> choices) {
        List<Item> previous = items();
        baseline.clear();
        bound.clear();
        names.clear();
        presentation.clear();
        if (bindings != null) for (var entry : bindings) {
            GraphBindingKey key = key(entry);
            if (key != null) { bound.add(key); readPresentation(key, entry); }
        }
        baseline.addAll(bound);
        // The parent supplies its rebased draft, including selections made during an in-flight save.
        // Keep their metadata when the bounded presentation snapshot omits the selected asset.
        picked.keySet().retainAll(bound);
        for (Item item : picked.values()) {
            names.put(item.key(), item.name());
            presentation.put(item.key(), item.presentation());
        }
        if (choices != null) for (var entry : choices) {
            GraphBindingKey key = key(entry);
            if (key != null) {
                readPresentation(key, entry);
            }
        }
        picked.replaceAll((key, item) -> new Item(key, name(key), presentation(key)));
        return !previous.equals(items());
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

    static GraphBindingKey key(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.REMOTE || entry.isDirectory()
                || !AssetTypeCatalog.GRAPH_TYPE_ID.equals(entry.metadata().typeId())) return null;
        GraphType type = GraphTypeRegistry.INSTANCE.get(entry.metadata().variantId());
        if (type == null) return null;
        try { return new GraphBindingKey(type.runtimeKind(), entry.path()); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    List<GraphBindingKey> keys() { return List.copyOf(bound); }
    private List<Item> items() { return items(keys()); }
    private List<Item> items(List<GraphBindingKey> keys) {
        return keys.stream().map(key -> new Item(key, name(key), presentation(key))).toList();
    }
    String name(GraphBindingKey key) {
        String name = names.get(key);
        if (name != null && !name.isBlank()) return name;
        String path = key.graphId();
        int slash = path.lastIndexOf('/');
        return path.substring(slash + 1, path.length() - ".json".length());
    }
    private void readPresentation(GraphBindingKey key, Map<String, Object> entry) {
        if (entry.get("name") instanceof String name && !name.isBlank()) names.put(key, name);
        if (entry.get("graphType") instanceof String type && !type.isBlank()) {
            List<String> tags = entry.get("tags") instanceof List<?> values
                    ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
            presentation.put(key, new Presentation(type, tags));
        }
    }
    Presentation presentation(GraphBindingKey key) {
        // Runtime kind BLUEPRINT also executes quest graphs. Unknown is preferable to a false icon.
        return presentation.getOrDefault(key, new Presentation("", List.of()));
    }
    List<GraphBindingKey> filtered(EntityGraphFilter filter) {
        return bound.stream().filter(key -> filter.matches(name(key), presentation(key))).toList();
    }
    boolean add(AssetEntry entry) {
        GraphBindingKey key = key(entry);
        if (key == null || !bound.add(key)) return false;
        String label = names.getOrDefault(key, entry.name());
        Presentation previous = presentation(key);
        Presentation details = new Presentation(entry.metadata().variantId(),
                previous.type().equals(entry.metadata().variantId()) ? previous.tags() : List.of());
        picked.put(key, new Item(key, label, details));
        names.put(key, label);
        presentation.put(key, details);
        return true;
    }
    boolean remove(Set<GraphBindingKey> selected) { return bound.removeAll(selected); }
    boolean dirty() { return !baseline.equals(bound); }

    List<Map<String, Object>> graphs() {
        // Do not send presentation metadata such as name/selection back to the entity protocol.
        List<Map<String, Object>> result = new ArrayList<>();
        for (var key : bound) result.add(Map.of("id", key.graphId(), "kind", key.kind().name()));
        return List.copyOf(result);
    }
}
