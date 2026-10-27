package com.mine.geometry_node.client.ui.shell.registration;

import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelRegistry;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.editor.asset.sidebar.transfer.AssetTransferPanel;
import com.mine.geometry_node.client.ui.editor.game.sidebar.GameViewportSidebarPanel;

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
        SidebarPanelRegistry.INSTANCE.register(AssetTransferPanel.DEFINITION);
        SidebarPanelRegistry.INSTANCE.register(GameViewportSidebarPanel.DEFINITION);
        SidebarPanelRegistry.INSTANCE.register(com.mine.geometry_node.client.ui.editor.game.sidebar.ScenePropertiesPanel.DEFINITION);
        registered = true;
    }
}
