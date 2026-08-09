package com.mine.geometry_node.client.ui.settings.page.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stores page definitions only; page instances remain owned by the window. */
public final class SettingsPageRegistry {
    private final Map<String, SettingsPageDefinition> definitions = new LinkedHashMap<>();

    public synchronized void register(SettingsPageDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate settings page id: " + definition.id());
        }
    }

    public synchronized SettingsPageDefinition find(String id) {
        return id != null ? definitions.get(id) : null;
    }

    public synchronized List<SettingsPageDefinition> definitions() {
        List<SettingsPageDefinition> result = new ArrayList<>(definitions.values());
        result.sort(Comparator.comparingInt(SettingsPageDefinition::order)
                .thenComparing(SettingsPageDefinition::id));
        return List.copyOf(result);
    }
}
