package com.mine.geometry_node.client.ui.editor.sidebar.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side registry for editor sidebar tab panels.
 */
public final class SidebarPanelRegistry {
    public static final SidebarPanelRegistry INSTANCE = new SidebarPanelRegistry();

    private static final Comparator<SidebarPanelDefinition> DISPLAY_ORDER = Comparator
            .comparingInt(SidebarPanelDefinition::order)
            .thenComparing(SidebarPanelDefinition::id);

    private final Map<String, SidebarPanelDefinition> definitions = new LinkedHashMap<>();

    private SidebarPanelRegistry() {
    }

    public synchronized void register(SidebarPanelDefinition definition) {
        if (definition == null) return;
        SidebarPanelDefinition existing = definitions.get(definition.id());
        if (existing != null && existing != definition) {
            throw new IllegalStateException("Duplicate sidebar panel id: " + definition.id());
        }
        definitions.put(definition.id(), definition);
    }

    public synchronized boolean unregister(String id) {
        return definitions.remove(normalizeId(id)) != null;
    }

    @Nullable
    public synchronized SidebarPanelDefinition get(String id) {
        return definitions.get(normalizeId(id));
    }

    public synchronized List<SidebarPanelDefinition> definitionsFor(SidebarPanelScope scope) {
        List<SidebarPanelDefinition> result = new ArrayList<>();
        for (SidebarPanelDefinition definition : definitions.values()) {
            if (definition.supports(scope)) result.add(definition);
        }
        result.sort(DISPLAY_ORDER);
        return List.copyOf(result);
    }

    private static String normalizeId(String id) {
        return id != null ? id.trim() : "";
    }
}
