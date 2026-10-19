package com.mine.geometry_node.client.ui.components.composite.sidebar.api;

@FunctionalInterface
public interface SidebarPanelFactory {
    SidebarPanel create(SidebarPanelContext context);
}
