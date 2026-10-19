package com.mine.geometry_node.client.ui.editor.database;

import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.composite.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.database.sidebar.DatabaseDetailsPanel;
import com.mine.geometry_node.client.ui.editor.database.world.WorldObjectsView;
import com.mine.geometry_node.client.ui.editor.datalibrary.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Context;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import icyllis.modernui.view.Gravity;
import net.minecraft.network.chat.Component;
import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.*;

/** Two database views inside the existing Area; neither owns an independent workspace header. */
public final class DatabaseWindow extends LinearLayout implements AreaEditorWindow, SurfaceRegistrationAware {
    private final DataLibraryUiRepository repository;
    private final DataLibraryWindow library;
    private final WorldObjectsView world;
    private final UiActionButton worldTab, libraryTab;
    private final Map<UiActionButton,View> tabUnderlines = new LinkedHashMap<>();
    private final SidebarLayoutController sidebarLayout;
    private final DatabaseDetailsPanel details;
    private final UiStateContext uiState;
    private final UiFixedSplitLayout workspace;
    private String stateScope;
    private final Runnable scopeTask = () -> {
        if (!this.disposed) restoreTabScope();
    };
    private final Runnable scopeChanged = () -> { removeCallbacks(scopeTask); post(scopeTask); };
    private final Runnable libraryDetailsTask = this::refreshLibraryDetails;
    private final Runnable libraryChanged = () -> { removeCallbacks(libraryDetailsTask); post(libraryDetailsTask); };
    private DatabaseSelection selection;
    private boolean worldSelected = true;
    private boolean shown, disposed;
    private UiSurfaceRegistry.Registration registration;

    public DatabaseWindow(Context context) { this(context, ClientDataLibraryRepository.INSTANCE); }
    public DatabaseWindow(Context context, DataLibraryUiRepository repository) {
        this(context, repository, UiStateContext.ephemeral());
    }
    public DatabaseWindow(Context context, UiStateContext state) {
        this(context, ClientDataLibraryRepository.INSTANCE, state);
    }
    public DatabaseWindow(Context context, DataLibraryUiRepository repository, UiStateContext state) {
        super(context); uiState = java.util.Objects.requireNonNull(state); this.repository = repository == null ? DataLibraryUiRepository.EMPTY : repository;
        setOrientation(VERTICAL); setBackground(background(UiTheme.Surface.ROOT));
        LinearLayout tabs = new LinearLayout(context);
        tabs.setBackground(background(UiTheme.Surface.PANEL));
        worldTab = tab(context, tr("world"), true);
        libraryTab = tab(context, tr("library"), false);
        addTab(tabs, worldTab);
        addTab(tabs, libraryTab);
        addView(tabs, new LayoutParams(-1, px(36)));

        FrameLayout main = new FrameLayout(context);
        world = new WorldObjectsView(context, this::setSelection, state);
        library = new DataLibraryWindow(context, this.repository, state);
        library.onHide(); library.setVisibility(GONE);
        main.addView(world, new FrameLayout.LayoutParams(-1, -1));
        main.addView(library, new FrameLayout.LayoutParams(-1, -1));
        workspace = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.LAST, 320, 180, 320);
        EditorSidebar sidebar = new EditorSidebar(context);
        sidebar.installRegisteredPanels(new SidebarPanelContext(context, SidebarPanelScope.DATA_LIBRARY, this));
        details = sidebar.requirePanel(DatabaseDetailsPanel.PANEL_ID, DatabaseDetailsPanel.class);
        workspace.setPanes(main, sidebar);
        var savedSidebar = uiState.read(DatabaseViewState.SIDEBAR, DatabaseViewState.GLOBAL);
        workspace.restorePreferredExtentDp(savedSidebar.extentDp());
        workspace.addUserStateListener(this::saveSidebar);
        sidebarLayout = new SidebarLayoutController(workspace, sidebar, visible -> saveSidebar());
        sidebar.setOnCollapseRequested(() -> sidebarLayout.setVisible(false, true));
        sidebarLayout.initialize(savedSidebar.visible()); sidebarLayout.onOwnerHidden();
        addView(workspace, new LayoutParams(-1, 0, 1));
        library.setSelectionListener(entries -> { if (!worldSelected) setSelection(librarySelection(entries)); });
        this.repository.addChangeListener(libraryChanged);
        restoreTabScope();
        UiStateScopes.addChangeListener(scopeChanged);
        updateTabs();
    }

    private void saveSidebar() {
        uiState.write(DatabaseViewState.SIDEBAR, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Sidebar(workspace.preferredExtentDp(), sidebarLayout.isVisible()));
    }
    private void saveTab() {
        if (stateScope != null) uiState.write(DatabaseViewState.TAB, stateScope, new DatabaseViewState.Tab(worldSelected));
    }
    private void restoreTabScope() {
        String next = UiStateScopes.current();
        if (java.util.Objects.equals(stateScope, next)) return;
        saveTab(); stateScope = next;
        showWorld(uiState.read(DatabaseViewState.TAB, next).world());
    }
    public DatabaseSelection selection() { return selection; }
    private void refreshLibraryDetails() {
        if (!disposed && shown && !worldSelected) setSelection(librarySelection(library.selectedEntries()));
    }
    private void setSelection(DatabaseSelection selection) {
        this.selection = selection;
        if (details != null) details.refresh();
    }
    public void showWorld(boolean value) {
        if (disposed || worldSelected == value) return;
        if (shown) { if (worldSelected) world.onHide(); else library.onHide(); }
        worldSelected = value;
        saveTab();
        world.setVisibility(value ? VISIBLE : GONE); library.setVisibility(value ? GONE : VISIBLE);
        if (shown) { if (value) world.onShow(); else library.onShow(); }
        setSelection(value ? world.selection() : librarySelection(library.selectedEntries()));
        if (registration != null) registration.markInteracted();
        updateTabs();
    }
    private UiActionButton tab(Context context, String text, boolean world) {
        UiActionButton tab = UiTableStyle.rowButton(context);
        tab.setText(text);
        tab.setSingleLine(true);
        UIUtils.setLockedTextSize(tab, 12);
        tab.setOnClickListener(view -> showWorld(world));
        return tab;
    }
    private void addTab(LinearLayout tabs, UiActionButton tab) {
        FrameLayout host = new FrameLayout(getContext());
        tab.setPadding(px(16), 0, px(16), 0);
        host.addView(tab, new FrameLayout.LayoutParams(-1, -1));
        View underline = new View(getContext()); underline.setBackground(background(UiTheme.Control.FOCUS));
        host.addView(underline, new FrameLayout.LayoutParams(-1, px(2), Gravity.BOTTOM));
        tabUnderlines.put(tab, underline);
        tabs.addView(host, new LayoutParams(px(124), -1));
    }
    private void updateTabs() {
        for (UiActionButton tab : List.of(worldTab, libraryTab)) {
            boolean active = tab == worldTab ? worldSelected : !worldSelected;
            tab.setPalette(new UiActionButton.Palette(active ? UiTheme.Surface.HEADER : UiTheme.Surface.PANEL,
                    UiTheme.Control.ICON_HOVER, UiTheme.Control.ICON_PRESSED, active ? UiTheme.Text.STRONG : UiTheme.Text.MUTED));
            tabUnderlines.get(tab).setVisibility(active ? VISIBLE : INVISIBLE);
        }
    }
    private DatabaseSelection librarySelection(List<DataLibraryUiRepository.Entry> entries) {
        if (entries.isEmpty()) return null;
        if (entries.size() > 1) return new DatabaseSelection(tr("selected", entries.size()), tr("library"), "", 0,
                List.of(new DatabaseSelection.Field(tr("folder"), String.join("\n", entries.stream()
                        .map(entry -> repository.folderPath(entry.parentId())).distinct().toList()))), List.of(), List.of());
        var entry = entries.getFirst();
        String type = Component.translatable("geometry_node.port." + entry.type().name().toLowerCase(Locale.ROOT)).getString();
        if(type.startsWith("geometry_node."))type=entry.type().name();
        var metadata = List.of(new DatabaseSelection.Field(tr("type"), type),
                new DatabaseSelection.Field(tr("folder"), repository.folderPath(entry.parentId())),
                new DatabaseSelection.Field(tr("id"), entry.id().toString()));
        var sections = List.of(
                new DatabaseSelection.Section(tr("stored_value"), type, DatabaseSelection.Kind.VALUE,
                        List.of(new DatabaseSelection.Field("", DataLibraryWindow.valueDetails(entry)))),
                new DatabaseSelection.Section(tr("entry_information"), "", DatabaseSelection.Kind.METADATA, metadata),
                new DatabaseSelection.Section(tr("tags"), "", DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag -> new DatabaseSelection.Field("", tag)).toList(),
                        repository.canEditTags(entry) ? new DatabaseSelection.TagEditor(entry.id().toString(),
                                !repository.isUpdatePending(entry.id()),
                                repository.isUpdatePending(entry.id()) ? tr("tag_update_pending")
                                        : repository.tagUpdateMessage(entry.id()).isEmpty() ? ""
                                        : Component.translatable("geometry_node." + repository.tagUpdateMessage(entry.id())).getString(),
                                tag -> repository.addTag(entry, tag), tag -> repository.removeTag(entry, tag)) : null),
                new DatabaseSelection.Section(tr("node_reference"), tr("reference_hint"), DatabaseSelection.Kind.REFERENCE,
                        List.of(new DatabaseSelection.Field(tr("id"), entry.id().toString()))));
        return new DatabaseSelection(entry.key(), type + " · " + tr("shared_entry"),
                "/assets/geometry_node/icons/ui/database/library.svg", entry.type().getColor(), List.of(), List.of(), sections);
    }
    @Override public View getView() { return this; }
    @Override public UiSurfaceId surfaceId() { return registration == null ? null : registration.id(); }
    @Override public void bindSurfaceRegistration(UiSurfaceRegistry.Registration value) { registration = value; }
    @Override public void onShow() {
        if (disposed || shown) return;
        restoreTabScope();
        shown = true; sidebarLayout.onOwnerShown();
        if (worldSelected) world.onShow(); else library.onShow();
        if (!worldSelected) setSelection(librarySelection(library.selectedEntries()));
    }
    @Override public void onHide() {
        saveTab(); saveSidebar();
        shown = false; sidebarLayout.onOwnerHidden(); world.onHide(); library.onHide();
    }
    @Override public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true;
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        repository.removeChangeListener(libraryChanged); removeCallbacks(libraryDetailsTask);
        world.onDispose(); library.onDispose();
    }
}
