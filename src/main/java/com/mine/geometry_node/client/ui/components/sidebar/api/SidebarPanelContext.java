package com.mine.geometry_node.client.ui.components.sidebar.api;

import icyllis.modernui.core.Context;

import java.util.Objects;

public record SidebarPanelContext(Context uiContext, SidebarPanelScope scope) {
    public SidebarPanelContext {
        Objects.requireNonNull(uiContext, "uiContext");
        Objects.requireNonNull(scope, "scope");
    }
}
