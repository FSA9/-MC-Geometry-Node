package com.mine.geometry_node.client.ui.workspace.registration;

import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelRegistry;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.editor.asset.sidebar.transfer.AssetTransferPanel;
import com.mine.geometry_node.client.ui.editor.game.sidebar.GameViewportSidebarPanel;

/**
 * Registers registration panels supplied by the base mod.
 */
public final class BuiltinSidebarPanels {
    private static boolean registered;

    private BuiltinSidebarPanels() {
    }

    public static synchronized void register() {
        if (registered) return;
        SidebarPanelRegistry.INSTANCE.register(GraphPropertiesPanel.DEFINITION);
        SidebarPanelRegistry.INSTANCE.register(AssetTransferPanel.DEFINITION);
        SidebarPanelRegistry.INSTANCE.register(GameViewportSidebarPanel.DEFINITION);
        registered = true;
    }
}
