package com.mine.geometry_node.client.ui.components.sidebar.api;

import icyllis.modernui.core.Context;

import java.util.Objects;

public record SidebarPanelContext(Context uiContext, SidebarPanelScope scope, Object owner) {
    public SidebarPanelContext(Context uiContext, SidebarPanelScope scope) {
        this(uiContext, scope, null);
    }

    public <T> T requireOwner(Class<T> type) {
        if (!type.isInstance(owner)) throw new IllegalStateException("Sidebar owner must be " + type.getName());
        return type.cast(owner);
    }
    public SidebarPanelContext {
        Objects.requireNonNull(uiContext, "uiContext");
        Objects.requireNonNull(scope, "scope");
    }
}
