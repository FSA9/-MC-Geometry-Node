package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.config.ConfigCategory;
import com.mine.geometry_node.client.config.ConfigEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Page-local projection: filtered entries retain their ancestors, never unrelated siblings. */
final class ConfigCategoryTree {
    record Group(ConfigCategory category, List<ConfigEntry<?>> entries, List<Group> children) {}

    static List<Group> build(List<ConfigCategory> categories, List<ConfigEntry<?>> entries,
                             Predicate<ConfigCategory> retained, Predicate<ConfigEntry<?>> filter) {
        Map<ConfigCategory, List<ConfigEntry<?>>> selected = new LinkedHashMap<>();
        for (ConfigCategory category : categories) {
            if (retained.test(category)) includeAncestors(selected, category);
        }
        for (ConfigEntry<?> entry : entries) {
            if (entry.isVisibleInSettings() && filter.test(entry)) {
                includeAncestors(selected, entry.category());
                selected.get(entry.category()).add(entry);
            }
        }
        return children(selected, null);
    }

    private static void includeAncestors(Map<ConfigCategory, List<ConfigEntry<?>>> groups, ConfigCategory category) {
        for (var current = category; current != null; current = current.parent()) {
            groups.computeIfAbsent(current, ignored -> new ArrayList<>());
        }
    }

    private static List<Group> children(Map<ConfigCategory, List<ConfigEntry<?>>> selected, ConfigCategory parent) {
        return selected.keySet().stream()
                .filter(category -> java.util.Objects.equals(category.parent(), parent))
                .sorted(Comparator.comparingInt(ConfigCategory::order).thenComparing(ConfigCategory::id))
                .map(category -> new Group(category, selected.get(category).stream()
                        .sorted(Comparator.comparingInt((ConfigEntry<?> entry) -> entry.order()).thenComparing(ConfigEntry::id))
                        .toList(), children(selected, category)))
                .toList();
    }

    private ConfigCategoryTree() {}
}
