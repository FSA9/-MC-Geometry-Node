package com.mine.geometry_node.client.ui.editor.game.sidebar;

import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.*;
import com.mine.geometry_node.client.ui.components.integration.scene.properties.ScenePropertiesView;
import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.editor.game.ViewportMode;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Viewport adapter for the shared schema form and existing scene edit transaction service. */
public final class ScenePropertiesPanel extends ScenePropertiesView implements SidebarPanel {
    public static final String PANEL_ID = "scene_properties";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(PANEL_ID,
            "geometry_node.game_view.scene.properties", 90, Set.of(SidebarPanelScope.GAME_VIEWPORT),
            c -> new ScenePropertiesPanel(c.uiContext(), c.requireOwner(GameViewportWindow.class)), SvgIconView.Icon.PROPERTIES);

    public ScenePropertiesPanel(Context context, GameViewportWindow owner) {
        this(context, ClientSceneService.INSTANCE, () -> owner.mode() == ViewportMode.EDIT);
    }
    ScenePropertiesPanel(Context context, ClientSceneService service, BooleanSupplier editingMode) {
        super(context, service, editingMode, snapshot -> snapshot.object(snapshot.active()));
    }
    @Override public View getView() { return this; }
    @Override public void onSelected() { refresh(); }
    @Override public void onDeselected() { dismissMenu(); }
}
