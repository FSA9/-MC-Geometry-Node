package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.composite.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.datalibrary.inspector.DatabaseSelection;
import com.mine.geometry_node.client.ui.editor.datalibrary.inspector.DatabaseSelectionSource;
import com.mine.geometry_node.client.ui.editor.datalibrary.state.DatabaseViewState;
import com.mine.geometry_node.client.ui.editor.datalibrary.inspector.DatabaseDetailsPanel;
import com.mine.geometry_node.client.ui.editor.datalibrary.objects.WorldObjectsView;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import com.mine.geometry_node.client.ui.workspace.state.UiStateKey;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import java.util.UUID;

/** Global entities share the query table and registered, collapsible database sidebar. */
final class EntityCatalogView extends FrameLayout implements GlobalDataSection, DatabaseSelectionSource {
    private final WorldObjectsView table;
    private final DatabaseDetailsPanel details;
    private final UiStateContext state;
    private final UiFixedSplitLayout split;
    private final SidebarLayoutController sidebarLayout;
    private final UiStateKey<DatabaseViewState.Sidebar> sidebarKey;
    private DatabaseSelection selection;

    EntityCatalogView(Context context, UiStateContext state) {
        this(context, state, null);
    }
    EntityCatalogView(Context context, UiStateContext state, UUID faction) {
        super(context);
        this.state = state;
        sidebarKey = faction == null ? DatabaseViewState.ENTITY_SIDEBAR : DatabaseViewState.FACTION_MEMBERS_SIDEBAR;
        table = faction == null ? new WorldObjectsView(context, this::setSelection, state, WorldObjectsView.Mode.ENTITIES)
                : new WorldObjectsView(context, this::setSelection, state, faction);
        EditorSidebar sidebar = new EditorSidebar(context);
        sidebar.installRegisteredPanels(new SidebarPanelContext(context, SidebarPanelScope.GLOBAL_ENTITIES, this));
        details = sidebar.requirePanel(DatabaseDetailsPanel.PANEL_ID, DatabaseDetailsPanel.class);
        split = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.LAST, 320, 180, 320);
        split.setPanes(table, sidebar);
        var saved = state.read(sidebarKey, UiStateScopes.GLOBAL);
        split.restorePreferredExtentDp(saved.extentDp());
        split.addUserStateListener(this::saveSidebar);
        sidebarLayout = new SidebarLayoutController(split, sidebar, visible -> saveSidebar());
        sidebar.setOnCollapseRequested(() -> sidebarLayout.setVisible(false, true));
        sidebarLayout.initialize(saved.visible());
        sidebarLayout.onOwnerHidden();
        addView(split, new LayoutParams(-1, -1));
    }
    @Override public DatabaseSelection selection() { return selection; }
    private void setSelection(DatabaseSelection value) {
        selection = value;
        if (details != null) details.refresh();
    }
    private void saveSidebar() {
        state.write(sidebarKey, UiStateScopes.GLOBAL,
                new DatabaseViewState.Sidebar(split.preferredExtentDp(), sidebarLayout.isVisible()));
    }
    @Override public View view() { return this; }
    @Override public void onShow() { sidebarLayout.onOwnerShown(); table.onShow(); setSelection(table.presentationSelection()); }
    @Override public void onHide() { table.onHide(); sidebarLayout.onOwnerHidden(); saveSidebar(); }
    @Override public void onDispose() { onHide(); table.onDispose(); }
}
