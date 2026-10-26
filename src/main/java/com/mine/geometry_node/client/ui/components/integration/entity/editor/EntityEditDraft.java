package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorChanges;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import java.util.*;
import java.util.function.Consumer;

/** Detached server snapshot plus one entity transaction. The data child stages into this same draft. */
final class EntityEditDraft {
    private Map<String, Object> baseline;
    private final LinkedHashMap<String, Object> values;
    private final Runnable changed;
    private final LocalDataBinding data = new LocalDataBinding();

    EntityEditDraft(Map<String, Object> snapshot, Runnable changed) {
        baseline = GraphMetadataDataValues.snapshot(snapshot);
        values = new LinkedHashMap<>(GraphMetadataDataValues.snapshot(snapshot));
        this.changed = Objects.requireNonNull(changed);
    }
    Map<String, Object> snapshot() { return GraphMetadataDataValues.snapshot(values); }
    Map<String, Object> changes() {
        return com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorChanges.between(baseline, values);
    }
    boolean dirty() { return !GraphMetadataDataValues.equivalent(baseline, values); }
    boolean flag(String key) { return Boolean.TRUE.equals(values.get(key)); }
    @SuppressWarnings("unchecked") Map<String, Object> dictionary(String key) {
        Object value = values.get(key); return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> graphs(String key) {
        Object value = values.get(key); return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }
    List<String> tags() {
        Object value = values.get("tags");
        return value instanceof List<?> tags ? tags.stream().map(String.class::cast).toList() : List.of();
    }
    void set(String key, Object value) {
        setAll(Map.of(key, value));
    }
    /** Publish a detached batch once, after all sections are consistent. */
    void setAll(Map<String, Object> replacements) {
        Map<String, Object> detached = GraphMetadataDataValues.snapshot(replacements);
        boolean different = detached.entrySet().stream().anyMatch(entry ->
                !values.containsKey(entry.getKey()) || !EntityEditorChanges.same(values.get(entry.getKey()), entry.getValue()));
        if (!different) return;
        values.putAll(detached);
        changed.run();
    }
    /** Accept a full reply without publishing another edit. sentSnapshot is the full local state sent. */
    boolean acknowledge(Map<String, Object> authoritative, Map<String, Object> sentSnapshot) {
        Map<String, Object> confirmed = GraphMetadataDataValues.snapshot(authoritative);
        Map<String, Object> merged = new LinkedHashMap<>(confirmed);
        boolean unavailableAttributeEdit = false;
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            if (!EntityEditorChanges.EDITABLE.contains(key)) continue;
            // Drops are commands. Equipment input is blocked while a drop batch is in flight.
            if (key.equals("equipmentDrops") && sentSnapshot.get(key) instanceof List<?> drops && !drops.isEmpty()) {
                merged.put(key, List.of());
            } else if (key.equals("attributes")) {
                Map<String, Object> sent = sentSnapshot.get(key) instanceof Map<?, ?>
                        ? EntityEditorChanges.dictionary(sentSnapshot.get(key)) : Map.of();
                Map<String, Object> current = dictionary(key);
                Map<String, Object> attributes = new LinkedHashMap<>(confirmed.get(key) instanceof Map<?, ?>
                        ? EntityEditorChanges.dictionary(confirmed.get(key)) : Map.of());
                Set<String> fields = new LinkedHashSet<>(sent.keySet()); fields.addAll(current.keySet());
                for (String field : fields) {
                    if (current.containsKey(field) == sent.containsKey(field)
                            && EntityEditorChanges.same(current.get(field), sent.get(field))) continue;
                    // A capability can disappear while a previous write is in flight. Never
                    // recreate it from a queued edit; the session requests explicit recovery.
                    if (!attributes.containsKey(field) && current.containsKey(field)) {
                        unavailableAttributeEdit = true;
                        continue;
                    }
                    if (current.containsKey(field)) attributes.put(field, current.get(field));
                    else attributes.remove(field);
                }
                merged.put(key, attributes);
            } else if (!sentSnapshot.containsKey(key) || !EntityEditorChanges.same(entry.getValue(), sentSnapshot.get(key))) {
                merged.put(key, entry.getValue());
            }
        }
        Map<String, Object> detached = GraphMetadataDataValues.snapshot(merged);
        baseline = confirmed;
        values.clear(); values.putAll(detached);
        data.notifyChanged();
        return unavailableAttributeEdit;
    }
    void attribute(String key, Object value) {
        var attributes = new LinkedHashMap<>(dictionary("attributes")); attributes.put(key, value); set("attributes", attributes);
    }
    void addTag(String value) { set("tags", TagSet.add(tags(), List.of(value), TagPolicy.EXACT)); }
    void removeTag(String value) { set("tags", TagSet.remove(tags(), List.of(value), TagPolicy.EXACT)); }
    DataDictionaryBinding dataBinding() { return data; }

    private final class LocalDataBinding implements DataDictionaryBinding {
        private final Set<Runnable> listeners = new LinkedHashSet<>();
        void notifyChanged() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public State snapshot() { return new State(dictionary("data"), false, true, true, false, ""); }
        @Override public void refresh() {}
        @Override public void apply(Map<String, Object> expected, Map<String, Object> replacement, Consumer<String> completion) {
            if (!GraphMetadataDataValues.equivalent(expected, dictionary("data"))) { completion.accept(EntityEditorViews.tr("error.conflict")); return; }
            try { set("data", replacement); }
            catch (IllegalArgumentException invalid) { completion.accept(EntityEditorViews.tr("error.invalid")); return; }
            completion.accept(null);
            notifyChanged();
        }
        @Override public void addChangeListener(Runnable listener) { listeners.add(listener); }
        @Override public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
    }
}
