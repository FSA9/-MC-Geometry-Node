package com.mine.geometry_node.core.engine.system.entityeditor;

import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataValues;
import java.util.*;

/** Computes edits against the read baseline, then compares only the state the user actually edited. */
public final class EntityEditorChanges {
    public static final Set<String> EDITABLE = Set.of("attributes", "tags", "data", "equipment", "inventory", "equipmentDrops", "graphs");
    private static final Set<String> READ_ONLY = Set.of("living", "equipmentSupported", "equipmentCreative", "availableGraphs", "preview",
            "registryType", "dimension", "position");
    private EntityEditorChanges() {}

    public static Map<String, Object> between(Map<String, Object> baseline, Map<String, Object> replacement) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (var entry : replacement.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (READ_ONLY.contains(key)) {
                if (!same(baseline.get(key), value)) throw new IllegalArgumentException("Read-only field: " + key);
                continue;
            }
            if (key.equals("equipmentDrops")) {
                if (!(value instanceof List<?> drops) || drops.size() > ConservedItemStacks.MAX_DROPS)
                    throw new IllegalArgumentException("Invalid equipment drops");
                // Absent drops mean an empty staging area for older drafts/baselines.
                if (!same(baseline.getOrDefault(key, List.of()), value)) changes.put(key, value);
                continue;
            }
            if (!EDITABLE.contains(key) || !baseline.containsKey(key)) throw new IllegalArgumentException("Unsupported section: " + key);
            if (key.equals("attributes")) {
                Map<String, Object> before = dictionary(baseline.get(key)), next = dictionary(value), fields = new LinkedHashMap<>();
                for (var field : next.entrySet()) {
                    if (!before.containsKey(field.getKey())) throw new IllegalArgumentException("Unsupported attribute: " + field.getKey());
                    if (!same(before.get(field.getKey()), field.getValue())) fields.put(field.getKey(), field.getValue());
                }
                if (!fields.isEmpty()) changes.put(key, fields);
            } else if (!same(baseline.get(key), value)) changes.put(key, value);
        }
        // A pair is mandatory even if one side of a rearrangement happens to remain identical.
        if (inventoryAction(changes)) {
            if (!replacement.containsKey("equipment") || !replacement.containsKey("inventory"))
                throw new IllegalArgumentException("Equipment and inventory must be submitted together");
            changes.put("equipment", replacement.get("equipment")); changes.put("inventory", replacement.get("inventory"));
            if (replacement.containsKey("equipmentDrops")) changes.put("equipmentDrops", replacement.get("equipmentDrops"));
        }
        return WorldObjectDataValues.snapshot(changes);
    }

    public static boolean conflicts(Map<String, Object> baseline, Map<String, Object> current, Map<String, Object> changes) {
        if (inventoryAction(changes)) {
            for (String key : List.of("equipment", "inventory", "equipmentCreative"))
                if (!same(baseline.get(key), current.get(key))) return true;
        }
        for (String key : changes.keySet()) {
            if (key.equals("equipmentDrops")) continue; // A command, never persistent world state.
            if (key.equals("attributes")) {
                Map<String, Object> before = dictionary(baseline.get(key)), now = dictionary(current.get(key));
                for (String field : dictionary(changes.get(key)).keySet())
                    if (!now.containsKey(field) || !same(before.get(field), now.get(field))) return true;
                // Changing max health can clamp health; that dependent state must also be current.
                if (dictionary(changes.get(key)).containsKey("maxHealth") && !same(before.get("health"), now.get("health"))) return true;
                if (dictionary(changes.get(key)).containsKey("health") && !same(before.get("maxHealth"), now.get("maxHealth"))) return true;
            } else if (!same(baseline.get(key), current.get(key))) return true;
        }
        return false;
    }

    private static boolean inventoryAction(Map<String, Object> changes) {
        return changes.containsKey("equipment") || changes.containsKey("inventory") || changes.containsKey("equipmentDrops");
    }

    public static boolean same(Object first, Object second) {
        if (first == null || second == null) return first == second;
        return WorldObjectDataValues.equivalent(Map.of("value", first), Map.of("value", second));
    }

    public static Map<String, Object> dictionary(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String)))
            throw new IllegalArgumentException("Expected dictionary");
        @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }
}
