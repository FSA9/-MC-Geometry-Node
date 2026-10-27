package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.datalibrary.library.DataLibraryView;
import com.mine.geometry_node.client.ui.editor.datalibrary.state.DatabaseViewState;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.feature.datalibrary.ClientDataLibraryRepository;
import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.ui.components.composite.navigation.UiSubTabs;
import com.mine.geometry_node.client.ui.editor.datalibrary.objects.WorldObjectsView;
import com.mine.geometry_node.client.ui.editor.datalibrary.global.GlobalDataView;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import static com.mine.geometry_node.client.ui.editor.datalibrary.presentation.DatabasePresentation.*;

/** One database Area owns its three pages; each page provides its own editing commands. */
public final class DatabaseWindow extends LinearLayout implements AreaEditorWindow, SurfaceRegistrationAware {
    private final DataLibraryView library;
    private final WorldObjectsView world;
    private final GlobalDataView global;
    private final UiSubTabs tabs;
    private final UiStateContext uiState;
    private final FrameLayout workspace;
    private String stateScope;
    private final Runnable scopeTask = () -> { if (!this.disposed) restoreTabScope(); };
    private final Runnable scopeChanged = () -> { removeCallbacks(scopeTask); post(scopeTask); };
    private DatabaseViewState.Page selectedPage = DatabaseViewState.Page.WORLD;
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
        super(context);
        uiState = java.util.Objects.requireNonNull(state);
        setOrientation(VERTICAL);
        ShapeDrawable background = new ShapeDrawable(); background.setColor(UiThemes.EDITOR.surface().root());
        setBackground(background);
        tabs = new UiSubTabs(context, UiThemes.EDITOR);
        tabs.addTab(DatabaseViewState.Page.WORLD.name(), tr("world"), () -> showPage(DatabaseViewState.Page.WORLD));
        tabs.addTab(DatabaseViewState.Page.LIBRARY.name(), tr("library"), () -> showPage(DatabaseViewState.Page.LIBRARY));
        tabs.addTab(DatabaseViewState.Page.GLOBAL_DATA.name(), tr("global_data"), () -> showPage(DatabaseViewState.Page.GLOBAL_DATA));
        addView(tabs, new LayoutParams(-1, UIUtils.dp2pxInt(UiSubTabs.HEIGHT)));

        workspace = new FrameLayout(context);
        world = new WorldObjectsView(context, state);
        library = new DataLibraryView(context, repository == null ? DataLibraryRepository.EMPTY : repository, state);
        library.onHide(); library.setVisibility(GONE);
        workspace.addView(world, new FrameLayout.LayoutParams(-1, -1));
        workspace.addView(library, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout pageHost = new FrameLayout(context);
        pageHost.addView(workspace, new FrameLayout.LayoutParams(-1, -1));
        global = new GlobalDataView(context, state); global.setVisibility(GONE);
        pageHost.addView(global, new FrameLayout.LayoutParams(-1, -1));
        addView(pageHost, new LayoutParams(-1, 0, 1));
        restoreTabScope();
        UiStateScopes.addChangeListener(scopeChanged);
        updateTabs();
    }

    private void saveTab() {
        if (stateScope != null) uiState.write(DatabaseViewState.TAB, stateScope, new DatabaseViewState.Tab(selectedPage));
    }
    private void restoreTabScope() {
        String next = UiStateScopes.current();
        if (java.util.Objects.equals(stateScope, next)) return;
        saveTab(); stateScope = next;
        showPage(uiState.read(DatabaseViewState.TAB, next).page());
    }
    public void showWorld(boolean value) {
        showPage(value ? DatabaseViewState.Page.WORLD : DatabaseViewState.Page.LIBRARY);
    }
    public DatabaseViewState.Page selectedPage() { return selectedPage; }
    public void showPage(DatabaseViewState.Page page) {
        java.util.Objects.requireNonNull(page);
        if (disposed || selectedPage == page) return;
        if (shown) hideCurrentPage();
        selectedPage = page;
        saveTab();
        world.setVisibility(page == DatabaseViewState.Page.WORLD ? VISIBLE : GONE);
        library.setVisibility(page == DatabaseViewState.Page.LIBRARY ? VISIBLE : GONE);
        workspace.setVisibility(page == DatabaseViewState.Page.GLOBAL_DATA ? GONE : VISIBLE);
        global.setVisibility(page == DatabaseViewState.Page.GLOBAL_DATA ? VISIBLE : GONE);
        if (shown) showCurrentPage();
        if (registration != null) registration.markInteracted();
        updateTabs();
    }
    private void hideCurrentPage() {
        switch (selectedPage) {
            case WORLD -> world.onHide();
            case LIBRARY -> library.onHide();
            case GLOBAL_DATA -> global.onHide();
        }
    }
    private void showCurrentPage() {
        switch (selectedPage) {
            case WORLD -> world.onShow();
            case LIBRARY -> library.onShow();
            case GLOBAL_DATA -> global.onShow();
        }
    }
    private void updateTabs() { tabs.setSelectedTab(selectedPage.name()); }
    @Override public View getView() { return this; }
    @Override public UiSurfaceId surfaceId() { return registration == null ? null : registration.id(); }
    @Override public void bindSurfaceRegistration(UiSurfaceRegistry.Registration value) { registration = value; }
    @Override public void onShow() {
        if (disposed || shown) return;
        restoreTabScope();
        shown = true; showCurrentPage();
    }
    @Override public void onHide() {
        saveTab();
        shown = false; world.onHide(); library.onHide(); global.onHide();
    }
    @Override public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true;
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        world.onDispose(); library.onDispose(); global.onDispose();
    }
}
