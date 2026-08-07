package com.mine.geometry_node.client.ui.editor.sidebar;

import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelRegistry;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesPanel;

/**
 * Registers sidebar panels supplied by the base mod.
 */
public final class BuiltinSidebarPanels {
    private static boolean registered;

    private BuiltinSidebarPanels() {
    }

    public static synchronized void register() {
        if (registered) return;
        SidebarPanelRegistry.INSTANCE.register(GraphPropertiesPanel.DEFINITION);
        registered = true;
    }
}
