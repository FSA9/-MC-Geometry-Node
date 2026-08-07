package com.mine.geometry_node.client.ui.editor.sidebar.api;

@FunctionalInterface
public interface SidebarPanelFactory {
    SidebarPanel create(SidebarPanelContext context);
}
