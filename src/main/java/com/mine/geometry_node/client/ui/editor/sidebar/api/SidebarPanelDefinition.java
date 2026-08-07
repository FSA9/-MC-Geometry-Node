package com.mine.geometry_node.client.ui.editor.sidebar.api;

import java.util.Objects;
import java.util.Set;

/**
 * Registration metadata and factory for one sidebar tab panel.
 */
public record SidebarPanelDefinition(
        String id,
        String titleTranslationKey,
        int order,
        Set<SidebarPanelScope> scopes,
        SidebarPanelFactory factory) {

    public SidebarPanelDefinition {
        id = normalizeId(id);
        if (id.isEmpty()) throw new IllegalArgumentException("Sidebar panel id cannot be blank");
        titleTranslationKey = Objects.requireNonNullElse(titleTranslationKey, "").trim();
        if (titleTranslationKey.isEmpty()) {
            throw new IllegalArgumentException("Sidebar panel title translation key cannot be blank");
        }
        scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
        if (scopes.isEmpty()) throw new IllegalArgumentException("Sidebar panel scopes cannot be empty");
        Objects.requireNonNull(factory, "factory");
    }

    public boolean supports(SidebarPanelScope scope) {
        return scopes.contains(scope);
    }

    public SidebarPanel create(SidebarPanelContext context) {
        if (!supports(context.scope())) {
            throw new IllegalArgumentException("Sidebar panel " + id + " does not support " + context.scope());
        }
        SidebarPanel panel = factory.create(context);
        if (panel == null || panel.getView() == null) {
            throw new IllegalStateException("Sidebar panel factory returned no view: " + id);
        }
        return panel;
    }

    private static String normalizeId(String id) {
        return id != null ? id.trim() : "";
    }
}
