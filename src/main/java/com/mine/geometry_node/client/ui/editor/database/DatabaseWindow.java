package com.mine.geometry_node.client.ui.editor.database;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.datalibrary.ClientDataLibraryRepository;
import com.mine.geometry_node.client.datalibrary.DataLibraryRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;
import icyllis.modernui.view.Gravity;
import net.minecraft.network.chat.Component;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.*;

/** Two database views inside the existing Area; neither owns an independent workspace header. */
public final class DatabaseWindow extends LinearLayout implements AreaEditorWindow, SurfaceRegistrationAware {
    private final DataLibraryRepository repository;
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
    public DatabaseWindow(Context context, DataLibraryRepository repository) {
        this(context, repository, UiStateContext.ephemeral());
    }
    public DatabaseWindow(Context context, UiStateContext state) {
        this(context, ClientDataLibraryRepository.INSTANCE, state);
    }
    public DatabaseWindow(Context context, DataLibraryRepository repository, UiStateContext state) {
        super(context); uiState = java.util.Objects.requireNonNull(state); this.repository = repository == null ? DataLibraryRepository.EMPTY : repository;
        setOrientation(VERTICAL); setBackground(background(UiThemes.EDITOR.surface().root()));
        LinearLayout tabs = new LinearLayout(context);
        tabs.setBackground(background(UiThemes.EDITOR.surface().panel()));
        worldTab = tab(context, tr("world"), true);
        libraryTab = tab(context, tr("library"), false);
        addTab(tabs, worldTab);
        addTab(tabs, libraryTab);
        addView(tabs, new LayoutParams(-1, px(36)));

        FrameLayout main = new FrameLayout(context);
        world = new WorldObjectsView(context, value -> {
            if (!disposed && worldSelected) setSelection(value);
        }, state);
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
        library.setSelectionListener(entries -> { if (!disposed && !worldSelected) setSelection(librarySelection(entries)); });
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
        View underline = new View(getContext()); underline.setBackground(background(UiThemes.EDITOR.selection().outline()));
        host.addView(underline, new FrameLayout.LayoutParams(-1, px(2), Gravity.BOTTOM));
        tabUnderlines.put(tab, underline);
        tabs.addView(host, new LayoutParams(px(124), -1));
    }
    private void updateTabs() {
        for (UiActionButton tab : List.of(worldTab, libraryTab)) {
            boolean active = tab == worldTab ? worldSelected : !worldSelected;
            tab.setPalette(new UiActionButton.Palette(active ? UiThemes.EDITOR.surface().header() : UiThemes.EDITOR.surface().panel(),
                    UiThemes.EDITOR.control().iconHover(), UiThemes.EDITOR.control().iconPressed(), active ? UiThemes.EDITOR.text().strong() : UiThemes.EDITOR.text().muted()));
            tabUnderlines.get(tab).setVisibility(active ? VISIBLE : INVISIBLE);
        }
    }
    private DatabaseSelection librarySelection(List<DataLibraryRepository.Entry> entries) {
        if (entries.isEmpty()) return null;
        if (entries.size() > 1) return new DatabaseSelection(tr("selected", entries.size()), tr("library"), "", 0,
                List.of(new DatabaseSelection.Field(tr("folder"), String.join("\n", entries.stream()
                        .map(entry -> repository.folderPath(entry.parentId())).distinct().toList()))), List.of(), List.of());
        var entry = entries.getFirst();
        String selectionScope = stateScope;
        String type = Component.translatable(entry.type().getTranslationKey()).getString();
        boolean registryValue = switch (entry.type()) {
            case ENTITY, ENTITY_TEMPLATE, ITEM, ITEM_STACK, BLOCK_STATE -> true;
            default -> false;
        };
        var metadata = List.of(new DatabaseSelection.Field(tr("type"), registryValue ? library.registryType(entry) : type),
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
                                tag -> submitLibraryTagEdit(entry, selectionScope, tag, true),
                                tag -> submitLibraryTagEdit(entry, selectionScope, tag, false)) : null),
                new DatabaseSelection.Section(tr("node_reference"), tr("reference_hint"), DatabaseSelection.Kind.REFERENCE,
                        List.of(new DatabaseSelection.Field(tr("id"), entry.id().toString()))));
        return new DatabaseSelection(entry.key(), type + " · " + tr("shared_entry"),
                "", entry.type().getColor(), List.of(), List.of(), sections, libraryPreview(entry),
                new DatabaseSelection.DataEditor(selectionScope + ":library:" + entry.id(), entry.data().size(),
                        com.mine.geometry_node.client.ui.editor.database.metadata.DatabaseDataBindings.library(
                                repository, entry.id(), () -> !disposed && shown && !worldSelected
                                        && java.util.Objects.equals(selectionScope, stateScope)
                                        && java.util.Objects.equals(selectionScope, UiStateScopes.current())
                                        && isSingleLibrarySelection(entry.id()))));
    }
    private boolean isSingleLibrarySelection(java.util.UUID id) {
        var selected = library.selectedEntries();
        return selected.size() == 1 && selected.getFirst().id().equals(id);
    }
    private void submitLibraryTagEdit(DataLibraryRepository.Entry expected, String scope, String tag, boolean add) {
        if (disposed || !shown || worldSelected || !sidebarLayout.isVisible()
                || !java.util.Objects.equals(scope, stateScope)
                || !java.util.Objects.equals(scope, UiStateScopes.current()))
            throw new IllegalStateException("Inactive library tag editor");
        var selected = library.selectedEntries();
        if (selected.size() != 1 || !selected.getFirst().id().equals(expected.id()))
            throw new IllegalStateException("Stale library tag selection");
        if (add) repository.addTag(expected, tag); else repository.removeTag(expected, tag);
    }
    private static DatabaseSelection.Preview libraryPreview(DataLibraryRepository.Entry entry) {
        return switch (entry.type()) {
            case ENTITY -> entry.value() instanceof java.util.UUID id
                    ? new DatabaseSelection.EntityPreview(id, "", null) : null;
            case ENTITY_TEMPLATE -> {
                var template = EntityTemplateValue.from(entry.value());
                yield template.isEmpty() ? null : new DatabaseSelection.EntityPreview(null, "", template);
            }
            case ITEM, ITEM_STACK, BLOCK_STATE -> {
                var stack = DataLibraryValuePresentation.previewStack(entry);
                yield stack.isEmpty() ? null : new DatabaseSelection.ItemPreview(stack);
            }
            default -> null;
        };
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
