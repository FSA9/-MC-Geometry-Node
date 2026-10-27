package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryEntryValues;

import com.mine.geometry_node.client.ui.workspace.drag.payload.DataLibraryDragPayload;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.base.scroll.UiHorizontalScrollView;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableViewport;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableBatchWork;
import com.mine.geometry_node.client.ui.editor.datalibrary.state.DatabaseViewState;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchableMenu;
import com.mine.geometry_node.core.utils.ColumnFilters;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableFilterHeader;
import com.mine.geometry_node.client.ui.components.composite.table.UiColumnFilterPopover;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragDropRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragState;
import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryTypes;
import com.mine.geometry_node.core.node.definition.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact editor for the server-authoritative Data Library. */
public final class DataLibraryView extends LinearLayout {
    private static final int BG = UiThemes.EDITOR.surface().canvas();
    private static final int INPUT_BG = UiThemes.EDITOR.control().input();
    private static final int BORDER = UiThemes.EDITOR.border().strong();
    private static final int TEXT = UiThemes.EDITOR.text().normal();
    private static final int MUTED = UiThemes.EDITOR.text().muted();
    private static final int TREE_INDENT_DP = 20;
    private static final int TREE_ROW_PADDING_DP = 4;
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;

    private final UiTableBatchWork mountWork;
    private long mountEpoch;
    private boolean mountIncomplete;
    private DataLibraryScrollAnchor mountLeftAnchor;
    private DataLibraryScrollAnchor mountRightAnchor;
    private java.util.Iterator<Runnable> mountSteps;
    private final DataLibraryRepository repository;
    private final UiStateContext uiState;
    private String stateScope;
    private boolean restoringState;
    private volatile boolean scopeSnapshotConfirmed = true;
    private boolean folderSnapshotReady() {
        return Objects.equals(stateScope, UiStateScopes.current())
                && scopeSnapshotConfirmed && repository.hasLoadedSnapshot();
    }
    private boolean canRunLibraryCommand() {
        return !disposed && !suspended && folderSnapshotReady();
    }
    private volatile long stateGeneration;
    private final DataLibraryTreeLayout groupsHost;
    private final DataLibraryTreeLayout rightHost;
    private final ScrollView scrollHost;
    private final ScrollView typeScroll;
    private final ScrollView rightScroll;
    private final DataLibraryValueEditors valueEditors;
    private final DataLibraryEntityTypes entityTypes;
    /** Shared by the header and every cached card; width changes only remeasure views. */
    private final UiTableColumns columns = new UiTableColumns(List.of(
            UiTableStyle.rowNumberColumn(),
            UiTableStyle.selectionColumn(),
            new UiTableColumns.Column("disclosure", DataLibraryEntryCard.DISCLOSURE_WIDTH,
                    DataLibraryEntryCard.DISCLOSURE_WIDTH, false),
            UiTableStyle.column("name", 240),
            UiTableStyle.column("type", 144),
            UiTableStyle.column("registry_type", 180),
            UiTableStyle.column("value", 256),
            UiTableStyle.column("tags", 128),
            UiTableStyle.column("data", 220)), UiTableColumns.Sizing.INDEPENDENT);
    private final LinearLayout breadcrumbTrail;
    private final LinearLayout typeNavigation;
    private final TextView statusView;
    private final TextView resultsView;
    private final UiActionButton scopeButton;
    private final DataLibraryToolbar toolbar;
    private UiColumnFilterPopover filterPopover;
    private final Map<String, UiTableFilterHeader> filterHeaders = new HashMap<>();
    private UiSearchableMenu activeMenu;
    private UiContextMenu renameMenu;
    private long renameGeneration;
    private final UiIconButton addFolderButton;
    private final View deleteButton;
    private final UiCheckBox selectAll;
    private final DataLibrarySelection selection = new DataLibrarySelection();
    private final Set<DataLibraryRepository.EntryKey> selected = selection.entries;
    private final Set<UUID> selectedFolders = selection.folders;
    private final Set<UUID> expandedFolders = new HashSet<>();
    private final Set<UUID> expandedEntries = new HashSet<>();
    private final Set<UUID> renderedEntries = selection.rendered;
    private UUID activeFolder;
    private final Map<View, UUID> folderDropRows = new java.util.LinkedHashMap<>();
    private final WorkspaceDragDropRegistry.DropTarget dropTarget = this::acceptInternalDrop;
    private final Runnable scopeTask = () -> {
        if (this.disposed || Objects.equals(stateScope, UiStateScopes.current())) return;
        switchScope();
        if (!this.suspended) { rebuild(); refresh(); }
    };
    private final Runnable scopeChanged = () -> { removeCallbacks(scopeTask); post(scopeTask); };
    private final Runnable entityTypeRefresh = this::refreshEntityTypeSnapshot;
    private final AtomicBoolean authoritativeQueued = new AtomicBoolean();
    private final Runnable repositoryChanged = () -> {
        authoritativeQueued.set(true);
        scheduleRefresh();
    };
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private final Runnable refreshTask = () -> {
        refreshScheduled.set(false);
        if (!this.disposed) requestRebuild();
    };
    private final Runnable retryRefresh = () -> {
        retryScheduled = false;
        if (!this.disposed && hasActiveDraft()) deferRefresh();
        else scheduleRefresh();
    };
    private final Map<UUID, CardBinding> cards = new HashMap<>();
    private final List<Runnable> selectionViews = new ArrayList<>();
    private DataLibrarySnapshot snapshot;
    private volatile boolean disposed;
    private volatile boolean suspended;
    private boolean reconciling;
    private boolean retryScheduled;
    private boolean structurePending;
    private long authoritativeRevision;
    private DataLibraryScrollAnchor pendingLeftAnchor;
    private DataLibraryScrollAnchor pendingRightAnchor;
    private View rootDropTarget;
    private boolean dropTargetRegistered;
    private String registryTypeFilter = "";
    private PortType typeFilter;
    private ColumnFilters columnFilters = ColumnFilters.empty();
    private boolean includeSubfolders;
    private boolean rootExpanded = true;
    private boolean internalDragActive;
    private boolean rebuildPending;
    private static DataLibraryRepository.Entry clipboardEntry;

    public DataLibraryView(Context context) {
        this(context, DataLibraryRepository.EMPTY);
    }

    public DataLibraryView(Context context, DataLibraryRepository repository) {
        this(context, repository, UiStateContext.ephemeral());
    }

    public DataLibraryView(Context context, DataLibraryRepository repository, UiStateContext state) {
        super(context);
        mountWork = new UiTableBatchWork(this);
        entityTypes = new DataLibraryEntityTypes(this);
        uiState = Objects.requireNonNull(state);
        columns.restoreCustomWidths(uiState.read(DatabaseViewState.LIBRARY_WIDTHS, DatabaseViewState.GLOBAL).columns());
        columns.addUserStateListener(() -> uiState.write(DatabaseViewState.LIBRARY_WIDTHS, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Widths(columns.exportCustomWidths())));
        this.repository = repository != null ? repository : DataLibraryRepository.EMPTY;
        selectAll = new UiCheckBox(context);
        this.valueEditors = new DataLibraryValueEditors(context, this.repository,
                this::overlayHost, this::refresh, this::scheduleRefresh,
                () -> !disposed && !suspended && Objects.equals(stateScope, UiStateScopes.current()) && folderSnapshotReady());
        this.repository.addChangeListener(repositoryChanged);
        setOrientation(VERTICAL);
        setBackground(solid(BG, 0));

        toolbar = new DataLibraryToolbar(context,
                () -> { if (!canRunLibraryCommand()) return; prepareCreation(); this.repository.createFolder(activeFolder); },
                anchor -> showTypeMenu(anchor, type -> {
                    if (!canRunLibraryCommand()) return;
                    prepareCreation();
                    this.repository.create(activeFolder, type);
                }, false),
                () -> {
                    if (!canRunLibraryCommand()) return;
                    if (selected.isEmpty() && selectedFolders.isEmpty()) return;
                    Set<DataLibraryRepository.EntryKey> entries = Set.copyOf(selected);
                    Set<UUID> folders = Set.copyOf(selectedFolders);
                    clearSelection();
                    this.repository.delete(entries, folders);
                },
                this::toggleSubfolderScope);
        scopeButton = toolbar.scopeButton;
        addFolderButton = toolbar.addFolderButton;
        deleteButton = toolbar.deleteButton;

        scrollHost = new UiScrollView(context);
        scrollHost.setFillViewport(true);
        groupsHost = new DataLibraryTreeLayout(context);
        groupsHost.setOrientation(VERTICAL);
        groupsHost.setPadding(px(4), px(4), px(4), px(28));
        scrollHost.addView(groupsHost, new FrameLayout.LayoutParams(-1, -2));
        typeNavigation = new LinearLayout(context);
        typeNavigation.setOrientation(VERTICAL);
        typeNavigation.setPadding(0, 0, 0, px(8));
        typeScroll = new UiScrollView(context);
        typeScroll.setFillViewport(true);
        typeScroll.addView(typeNavigation, new FrameLayout.LayoutParams(-1, -2));
        rightHost = new DataLibraryTreeLayout(context);
        rightHost.setOrientation(VERTICAL);
        rightHost.setPadding(0, 0, 0, px(8));
        rightScroll = new UiScrollView(context);
        rightScroll.setFillViewport(true);
        rightScroll.addView(rightHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout leftPanel = new LinearLayout(context);
        leftPanel.setOrientation(VERTICAL);
        leftPanel.setBackground(solid(UiThemes.EDITOR.surface().root(), 0));
        TextView leftTitle = label(context, tr("geometry_node.data_library.folders"), 11, MUTED);
        leftTitle.setGravity(Gravity.CENTER_VERTICAL);
        leftTitle.setPadding(px(10), 0, 0, 0);
        LinearLayout directoryHeading = new LinearLayout(context);
        directoryHeading.setGravity(Gravity.CENTER_VERTICAL);
        directoryHeading.addView(leftTitle, new LayoutParams(0, -1, 1));
        LayoutParams addFolderParams = fixed(24, 24);
        // Match the checkbox center, including the tree's reserved scrollbar gutter.
        addFolderParams.rightMargin = scrollHost.getVerticalScrollbarWidth() + groupsHost.getPaddingRight()
                + px(TREE_ROW_PADDING_DP) + (px(CHECKBOX_SIZE_DP) - addFolderParams.width) / 2;
        directoryHeading.addView(addFolderButton, addFolderParams);
        leftPanel.addView(directoryHeading, new LayoutParams(-1, px(30)));
        UiFixedSplitLayout navigationPanes = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.VERTICAL,
                UiFixedSplitLayout.Edge.FIRST, 360, 60, 80);
        navigationPanes.restorePreferredExtentDp(uiState.read(DatabaseViewState.LIBRARY_TREE_SPLIT, DatabaseViewState.GLOBAL).extentDp());
        navigationPanes.addUserStateListener(() -> uiState.write(DatabaseViewState.LIBRARY_TREE_SPLIT, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Split(navigationPanes.preferredExtentDp())));
        navigationPanes.setPanes(scrollHost, typeScroll);
        leftPanel.addView(navigationPanes, new LayoutParams(-1, 0, 1));

        LinearLayout rightPanel = new LinearLayout(context);
        rightPanel.setOrientation(VERTICAL);
        rightPanel.setBackground(solid(BG, 0));
        HorizontalScrollView breadcrumbScroll = new UiHorizontalScrollView(context);
        breadcrumbScroll.setHorizontalScrollBarEnabled(false);
        breadcrumbScroll.setBackground(solid(UiThemes.EDITOR.surface().panel(), 0));
        breadcrumbTrail = new LinearLayout(context);
        breadcrumbTrail.setBaselineAligned(false);
        breadcrumbTrail.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumbTrail.setPadding(px(10), 0, px(10), 0);
        breadcrumbScroll.addView(breadcrumbTrail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        breadcrumbScroll.setPadding(0, px(8), 0, px(8));
        LinearLayout pathRow = new LinearLayout(context);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        pathRow.addView(breadcrumbScroll, new LayoutParams(0, px(40), 1));
        rightPanel.addView(pathRow, new LayoutParams(-1, px(40)));
        statusView = label(context, "", 11, MUTED);
        statusView.setSingleLine(true);
        statusView.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(px(14), 0, px(14), 0);
        LinearLayout results = new LinearLayout(context);
        results.setGravity(Gravity.CENTER_VERTICAL);
        results.setPadding(px(14), 0, px(10), 0);
        results.setBackground(solid(UiThemes.EDITOR.surface().canvas(), 0));
        resultsView = label(context, "", UiTableStyle.CELL_TEXT_SIZE_DP, TEXT);
        resultsView.setGravity(Gravity.CENTER_VERTICAL);
        resultsView.setSingleLine(true);
        resultsView.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
        LayoutParams resultsParams = new LayoutParams(0, -1, 1);
        results.addView(resultsView, resultsParams);
        LayoutParams scopeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, px(26));
        scopeParams.rightMargin = px(6);
        results.addView(scopeButton, scopeParams);
        LayoutParams createParams = fixed(26, 26);
        createParams.rightMargin = px(6);
        results.addView(toolbar.newEntryButton, createParams);
        results.addView(deleteButton, fixed(26, 26));
        rightPanel.addView(results, new LayoutParams(-1, px(UiTableStyle.RESULT_BAR_HEIGHT)));
        LinearLayout table = new LinearLayout(context);
        table.setOrientation(VERTICAL);
        table.addView(createColumnHeader(), new LayoutParams(-1, px(UiTableStyle.HEADER_HEIGHT)));
        table.addView(rightScroll, new LayoutParams(-1, 0, 1));
        rightPanel.addView(new UiTableViewport(context, columns, table), new LayoutParams(-1, 0, 1));
        statusView.setBackground(solid(UiThemes.EDITOR.surface().panel(), 0));
        rightPanel.addView(statusView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(UiTableStyle.STATUS_BAR_HEIGHT)));
        groupsHost.setOnClickListener(v -> clearSelection());
        rightHost.setOnClickListener(v -> clearSelection());
        scrollHost.setOnClickListener(v -> clearSelection());
        rightScroll.setOnClickListener(v -> clearSelection());

        UiFixedSplitLayout split = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 216, 100, 240);
        split.restorePreferredExtentDp(uiState.read(DatabaseViewState.LIBRARY_SPLIT, DatabaseViewState.GLOBAL).extentDp());
        split.addUserStateListener(() -> uiState.write(DatabaseViewState.LIBRARY_SPLIT, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Split(split.preferredExtentDp())));
        split.setBackground(solid(BG, 0));
        split.setPanes(leftPanel, rightPanel);
        addView(split, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        restoreState();
        rebuild();
        UiStateScopes.addChangeListener(scopeChanged);
    }

    /** Stable selection order, resolved against the authoritative repository rather than editing drafts. */
    public List<DataLibraryRepository.Entry> selectedEntries() {
        if (disposed) return List.of();
        Map<UUID, DataLibraryRepository.Entry> entries = new DataLibrarySnapshot(repository).entries;
        return selected.stream().map(key -> entries.get(key.id())).filter(Objects::nonNull).toList();
    }

    /** Complete repository path; the root is '/'. */
    public String activeFolderPath() {
        return repository.folderPath(activeFolder);
    }

    /** Cached registry identity; live entity lookup belongs to the game-thread metadata resolver. */
    public String registryType(DataLibraryRepository.Entry entry) {
        if (disposed || !Objects.equals(stateScope, UiStateScopes.current())) return "";
        return entityTypes.registryType(entry);
    }

    private void setTypeFilter(PortType type) {
        typeFilter = type;
        updateFilterIndicator();
        rebuild();
    }

    private void toggleSubfolderScope() {
        includeSubfolders = !includeSubfolders;
        updateScopeButton();
        rebuild();
    }

    private void updateScopeButton() {
        scopeButton.setSelected(includeSubfolders);
        scopeButton.setPalette(UiActionButton.Palette.toggle(includeSubfolders));
        updateFilterIndicator();
    }

    private void prepareCreation() {
        // New folders and entries must remain visible even when a filter was active.
        typeFilter = null;
        registryTypeFilter = "";
        columnFilters = ColumnFilters.empty();
        updateFilterIndicator();
        rootExpanded = true;
        Map<UUID, DataLibraryRepository.Folder> folders = new HashMap<>();
        for (DataLibraryRepository.Folder folder : repository.folders()) {
            if (folder != null && folder.id() != null) folders.put(folder.id(), folder);
        }
        collectAncestors(activeFolder, folders, expandedFolders);
        rebuild();
    }

    private void refresh() {
        if (!disposed) {
            long generation = stateGeneration;
            repository.refresh(() -> {
                if (generation != stateGeneration || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
                scopeSnapshotConfirmed = true;
                scheduleRefresh();
            });
        }
    }

    private void scheduleRefresh() {
        if (disposed || suspended || !refreshScheduled.compareAndSet(false, true)) return;
        post(refreshTask);
    }

    private void requestRebuild() {
        requestRebuild(true);
    }

    private void requestRebuild(boolean refreshTypes) {
        if (disposed || suspended) return;
        if (internalDragActive || reconciling) {
            rebuildPending = true;
            return;
        }
        DataLibrarySnapshot next = new DataLibrarySnapshot(folderSnapshotReady() ? repository : DataLibraryRepository.EMPTY);
        if (refreshTypes) refreshEntityTypes(next);
        observeAuthority();
        if (!mountIncomplete && !structurePending && next.sameStructure(snapshot) && !valueMembershipChanged(next)
                && !(folderSnapshotReady() && activeFolder != null && !next.folders.containsKey(activeFolder))) {
            snapshot = next;
            refreshCardValues(next);
            return;
        }
        rebuild(next);
    }

    private boolean valueMembershipChanged(DataLibrarySnapshot next) {
        if ((!columnFilters.value().active() && registryTypeFilter.isEmpty()) || snapshot == null) return false;
        for (var entry : next.entries.values()) {
            var previous = snapshot.entries.get(entry.id());
            if (previous == null || matches(previous) != matches(entry)) return true;
        }
        return false;
    }

    private void rebuild() {
        if (disposed || restoringState) return;
        cancelMount();
        saveState();
        // Explicit navigation keeps its original focus-loss submission semantics.
        View focused = findFocus();
        if (focused instanceof EditText) focused.clearFocus();
        observeAuthority();
        DataLibrarySnapshot next = new DataLibrarySnapshot(folderSnapshotReady() ? repository : DataLibraryRepository.EMPTY);
        refreshEntityTypes(next);
        rebuild(next);
    }

    private void refreshEntityTypeSnapshot() {
        if (disposed || suspended) return;
        if (Objects.equals(stateScope, UiStateScopes.current()) && snapshot != null) refreshEntityTypes(snapshot);
        // Local metadata only; no server query, view rebuild, or render refresh when IDs are unchanged.
        removeCallbacks(entityTypeRefresh);
        postDelayed(entityTypeRefresh, 1000);
    }

    private void refreshEntityTypes(DataLibrarySnapshot next) {
        entityTypes.refresh(next.entries.values(), () -> {
            if (disposed || suspended || !Objects.equals(stateScope, UiStateScopes.current()) || snapshot == null) return;
            // A draft can defer structural reconciliation while newer values are already displayed.
            // Re-read authority rather than restoring the older mounted snapshot on metadata arrival.
            if (!registryTypeFilter.isEmpty()) structurePending = true;
            requestRebuild(false);
        });
    }

    private void observeAuthority() {
        if (authoritativeQueued.getAndSet(false)) authoritativeRevision++;
    }

    private void rebuild(DataLibrarySnapshot next) {
        cancelMount();
        if (internalDragActive || reconciling) {
            rebuildPending = true;
            return;
        }
        // Deleted/type-changed entries must stop accepting edits even while another draft is open.
        retireMissingCards(next);
        selection.retain(next);
        folderDropRows.entrySet().removeIf(item -> {
            if (next.folders.containsKey(item.getValue())) return false;
            View row = item.getKey();
            if (row.getParent() instanceof ViewGroup parent) parent.removeView(row);
            return true;
        });
        if (hasActiveDraft()) {
            structurePending = true;
            refreshCardValues(next);
            updateSelectionViews();
            deferRefresh();
            return;
        }
        reconciling = true;
        structurePending = false;
        DataLibraryScrollAnchor leftAnchor = mountIncomplete && mountLeftAnchor != null ? mountLeftAnchor
                : pendingLeftAnchor != null ? pendingLeftAnchor : DataLibraryScrollAnchor.capture(scrollHost, folderRowsById());
        DataLibraryScrollAnchor rightAnchor = mountIncomplete && mountRightAnchor != null ? mountRightAnchor
                : pendingRightAnchor != null ? pendingRightAnchor : DataLibraryScrollAnchor.capture(rightScroll, cardViewsById());
        mountLeftAnchor = leftAnchor;
        mountRightAnchor = rightAnchor;
        pendingLeftAnchor = null;
        pendingRightAnchor = null;
        try {
            WorkspaceDragService.INSTANCE.cancelIfSource(this);
            snapshot = next;
            next.indexChildren();
            if (folderSnapshotReady() && activeFolder != null && !next.folders.containsKey(activeFolder)) {
                activeFolder = null;
                saveState();
            }
            expandedFolders.retainAll(next.folders.keySet());
            expandedEntries.retainAll(next.entries.keySet());
            groupsHost.removeAllViews();
            groupsHost.hideIndicator();
            rightHost.removeAllViews();
            rightHost.hideIndicator();
            folderDropRows.clear();
            renderedEntries.clear();
            selectionViews.clear();
            List<Runnable> actions = new ArrayList<>();
            actions.add(() -> {
                rootDropTarget = createRootRow();
                groupsHost.addView(rootDropTarget, rowParams(null));
            });
            Set<UUID> matchedEntries = new HashSet<>();
            Set<UUID> visibleFolders = new HashSet<>();
            for (var entry : next.entries.values()) {
                if (DataLibraryTypes.supports(entry.type()) && matches(entry)) {
                    matchedEntries.add(entry.id());
                    collectAncestors(entry.parentId(), next.folders, visibleFolders);
                }
            }
            if (rootExpanded) {
                addTreeLevel(actions, null, 1, next.childFolders, visibleFolders, new HashSet<>());
            }
            addEntries(actions, activeFolder, next.childEntries, matchedEntries);
            actions.add(this::updateTypeNavigation);
            actions.add(this::updateNavigationLabels);
            long epoch = ++mountEpoch;
            String scope = stateScope;
            mountIncomplete = true;
            // Every closure is independently guarded, including work already dequeued on cancellation.
            mountSteps = actions.stream().<Runnable>map(action -> () -> {
                if (mountCurrent(epoch, scope, next)) action.run();
            }).iterator();
            updateActions(); // Accurate full query count, independent of the mounted prefix.
            mountWork.start(mountSteps, () -> {
                if (!mountCurrent(epoch, scope, next)) return;
                mountIncomplete = false;
                mountSteps = null;
                mountLeftAnchor = null;
                mountRightAnchor = null;
                // Only the completed epoch may prune cache or restore the captured scroll position.
                cards.keySet().retainAll(renderedEntries);
                updateSelectionViews();
                if (!mountCurrent(epoch, scope, next)) return;
                pendingLeftAnchor = leftAnchor;
                pendingRightAnchor = rightAnchor;
                requestLayout();
            });
        } finally {
            reconciling = false;
        }
        if (rebuildPending) {
            rebuildPending = false;
            scheduleRefresh();
        }
    }

    private boolean mountCurrent(long epoch, String scope, DataLibrarySnapshot expected) {
        return epoch == mountEpoch && !disposed && !suspended && snapshot == expected
                && Objects.equals(scope, UiStateScopes.current()) && !authoritativeQueued.get();
    }

    private void cancelMount() {
        mountEpoch++;
        mountWork.cancel();
        mountSteps = null;
        if (mountIncomplete) structurePending = true;
    }

    private boolean hasActiveDraft() {
        View focused = findFocus();
        if (focused instanceof EditText && (isDescendant(focused, groupsHost) || isDescendant(focused, rightHost))) return true;
        return cards.keySet().stream().anyMatch(valueEditors::isEditing);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (pendingLeftAnchor != null && !disposed) pendingLeftAnchor.restore(scrollHost, folderRowsById());
        if (pendingRightAnchor != null && !disposed) pendingRightAnchor.restore(rightScroll, cardViewsById());
        pendingLeftAnchor = null;
        pendingRightAnchor = null;
    }

    private Map<UUID, View> folderRowsById() {
        Map<UUID, View> rows = new HashMap<>();
        folderDropRows.forEach((view, id) -> rows.put(id, view));
        return rows;
    }

    private Map<UUID, View> cardViewsById() {
        Map<UUID, View> rows = new HashMap<>();
        cards.forEach((id, binding) -> rows.put(id, binding.card));
        return rows;
    }

    private static boolean isDescendant(View view, ViewGroup ancestor) {
        for (View current = view; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private void deferRefresh() {
        if (retryScheduled || disposed || suspended) return;
        retryScheduled = true;
        postDelayed(retryRefresh, 100);
    }

    private void retireMissingCards(DataLibrarySnapshot next) {
        cards.entrySet().removeIf(item -> {
            CardBinding binding = item.getValue();
            var entry = next.entries.get(item.getKey());
            if (entry != null && entry.type() == binding.current.get().type()) return false;
            // Retire before detaching, which can synchronously submit a focused editor's draft.
            binding.current.set(null);
            if (binding.card.getParent() instanceof ViewGroup parent) parent.removeView(binding.card);
            selected.remove(new DataLibraryRepository.EntryKey(item.getKey()));
            return true;
        });
    }

    private void refreshCardValues(DataLibrarySnapshot next) {
        for (var binding : cards.values()) {
            var entry = next.entries.get(binding.current.get().id());
            if (entry != null && entry.type() == binding.current.get().type()) refreshCard(binding, entry);
        }
    }

    private void refreshCard(CardBinding binding, DataLibraryRepository.Entry entry) {
        boolean expanded = expandedEntries.contains(entry.id());
        boolean changed = !sameValue(binding.displayed, entry);
        boolean localPending = !sameValue(binding.current.get(), binding.displayed);
        boolean authoritative = binding.authoritativeRevision < authoritativeRevision;
        boolean editing = binding.card.findFocus() instanceof EditText || valueEditors.isEditing(entry.id());
        if (editing) {
            if (changed || binding.presentation.isExpanded() != expanded) deferRefresh();
            return;
        }
        // Another entry's acknowledgement must not confirm or reject this entry's queued writes.
        if (repository.isUpdatePending(entry.id()) || localPending && !changed && !authoritative) {
            updateRowSummary(binding.presentation, binding.current.get());
            if (binding.presentation.isExpanded() != expanded) {
                binding.presentation.setExpandedContent(expanded, expanded
                        ? valueEditors.createExpandedEditor(binding.current.get(), binding.current) : null);
            }
            return;
        }
        changed |= localPending && authoritative;
        binding.current.set(entry);
        binding.presentation.setName(entry.key());
        updateRowSummary(binding.presentation, entry);
        if (binding.presentation.isExpanded() != expanded || (expanded && changed)) {
            binding.presentation.setExpandedContent(expanded, expanded
                    ? valueEditors.createExpandedEditor(entry, binding.current) : null);
        }
        binding.displayed = entry;
        binding.authoritativeRevision = authoritativeRevision;
    }

    private static boolean sameValue(DataLibraryRepository.Entry left, DataLibraryRepository.Entry right) {
        if (left.type() != right.type()) return false;
        if (Objects.deepEquals(left.value(), right.value())) return true;
        try {
            return DataLibraryEntryValues.equivalent(left, right.value());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void updateSelectionViews() {
        for (var binding : cards.values()) {
            boolean checked = selected.contains(key(binding.current.get()));
            binding.presentation.setSelected(checked);
        }
        syncSelectionCheck(selectAll, renderedEntries.stream()
                .filter(id -> selected.contains(new DataLibraryRepository.EntryKey(id))).count(),
                renderedEntries.size());
        selectAll.setEnabled(!renderedEntries.isEmpty());
        selectionViews.forEach(Runnable::run);
        updateActions();
    }

    private void updateNavigationLabels() {
        List<DataLibraryRepository.Folder> path = new ArrayList<>();
        Map<UUID, DataLibraryRepository.Folder> folders = snapshot.folders;
        Set<UUID> visited = new HashSet<>();
        UUID cursor = activeFolder;
        while (cursor != null && visited.add(cursor)) {
            DataLibraryRepository.Folder folder = folders.get(cursor);
            if (folder == null) break;
            path.add(0, folder);
            cursor = folder.parentId();
        }
        breadcrumbTrail.removeAllViews();
        addBreadcrumb(tr("geometry_node.data_library.root"), null);
        for (var folder : path) {
            TextView separator = label(getContext(), "/", 11, MUTED);
            separator.setGravity(Gravity.CENTER);
            breadcrumbTrail.addView(separator, fixed(18, 24));
            addBreadcrumb(folder.name(), folder.id());
        }
    }

    private void updateTypeNavigation() {
        typeNavigation.removeAllViews();
        TextView title = label(getContext(), tr("geometry_node.data_library.types"), 11, MUTED);
        title.setPadding(px(10), 0, px(10), 0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        typeNavigation.addView(title, new LayoutParams(-1, px(30)));
        Map<PortType, Integer> counts = new java.util.EnumMap<>(PortType.class);
        for (var entry : snapshot.entries.values()) {
            if (DataLibraryTypes.supports(entry.type()) && inFolderScope(entry.parentId())) {
                counts.merge(entry.type(), 1, Integer::sum);
            }
        }
        addTypeNavigationRow(null, counts.values().stream().mapToInt(Integer::intValue).sum());
        counts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                addTypeNavigationRow(entry.getKey(), entry.getValue()));
    }

    private void addTypeNavigationRow(PortType type, int count) {
        String title = type == null ? tr("geometry_node.data_library.all_types") : displayTypeName(type);
        UiNavigationRow row = new UiNavigationRow(getContext(), title, count);
        row.setPalette(UiTableStyle.rowPalette(typeFilter == type, 0));
        row.button().setOnClickListener(view -> setTypeFilter(type));
        typeNavigation.addView(row, new LayoutParams(-1, px(33)));
    }

    /** Includes descendants only when requested; malformed directory cycles cannot widen the scope. */
    private boolean inFolderScope(UUID parentId) {
        if (Objects.equals(activeFolder, parentId)) return true;
        if (!includeSubfolders) return false;
        Set<UUID> visited = new HashSet<>();
        while (parentId != null && visited.add(parentId)) {
            DataLibraryRepository.Folder folder = snapshot.folders.get(parentId);
            if (folder == null) return false;
            parentId = folder.parentId();
            if (Objects.equals(activeFolder, parentId)) return true;
        }
        return false;
    }

    private void addBreadcrumb(String title, UUID folderId) {
        boolean current = Objects.equals(activeFolder, folderId);
        UiActionButton segment = UiTableStyle.rowButton(getContext());
        segment.setText(title);
        UIUtils.setLockedTextSize(segment, 12);
        segment.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        segment.setPadding(px(6), 0, px(6), 0);
        segment.setSingleLine(true);
        segment.setPalette(new UiActionButton.Palette(0, UiThemes.EDITOR.control().iconHover(),
                UiThemes.EDITOR.control().iconPressed(), current ? TEXT : MUTED));
        segment.setClickable(!current);
        segment.setFocusable(!current);
        if (!current) segment.setOnClickListener(view -> { activeFolder = folderId; rebuild(); });
        segment.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
        breadcrumbTrail.addView(segment, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(24)));
    }

    private View createColumnHeader() {
        UiTableRow header = UiTableStyle.header(getContext(), columns);
        header.addView(UiTableStyle.rowNumberCell(getContext(), 0), new ViewGroup.LayoutParams(-1, -1));
        selectAll.setContentDescription(tr("geometry_node.database.select_all"));
        selectAll.setOnCheckedChangeListener((button, checked) -> {
            if (disposed || suspended) return;
            selection.selectRendered(checked);
            updateSelectionViews();
        });
        header.addView(UiTableStyle.selectionCell(getContext(), selectAll), new ViewGroup.LayoutParams(-1, -1));
        header.addView(new View(getContext()), new ViewGroup.LayoutParams(-1, -1));
        addColumnLabel(header, "name", "geometry_node.data_library.column_name");
        addColumnLabel(header, "type", "geometry_node.database.type");
        addColumnLabel(header, "registry_type", "geometry_node.database.registry_type");
        addColumnLabel(header, "value", "geometry_node.data_library.column_value");
        addColumnLabel(header, "tags", "geometry_node.data_library.column_tags");
        header.addView(UiTableStyle.cell(getContext(), tr("geometry_node.database.data.title"), TEXT),
                new ViewGroup.LayoutParams(-1, -1));
        return header;
    }

    private void addColumnLabel(UiTableRow header, String column, String key) {
        UiTableFilterHeader cell = new UiTableFilterHeader(getContext(), tr(key), null,
                () -> showColumnFilter(column, filterHeaders.get(column).filterButton()));
        filterHeaders.put(column, cell);
        updateFilterIndicator();
        header.addView(cell,
                new ViewGroup.LayoutParams(-1, -1));
    }

    private void collectAncestors(UUID folderId, Map<UUID, DataLibraryRepository.Folder> foldersById,
                                  Set<UUID> destination) {
        Set<UUID> visited = new HashSet<>();
        while (folderId != null && visited.add(folderId)) {
            destination.add(folderId);
            DataLibraryRepository.Folder folder = foldersById.get(folderId);
            folderId = folder != null ? folder.parentId() : null;
        }
    }

    private void addTreeLevel(List<Runnable> actions, UUID parentId, int depth,
                              Map<UUID, List<DataLibraryRepository.Folder>> childFolders,
                              Set<UUID> visibleFolders, Set<UUID> visited) {
        for (DataLibraryRepository.Folder folder : childFolders.getOrDefault(parentId, List.of())) {
            if (!visited.add(folder.id())) continue;
            if (isFiltering() && !visibleFolders.contains(folder.id())) continue;
            boolean expanded = isFiltering() || expandedFolders.contains(folder.id());
            actions.add(() -> groupsHost.addView(createFolderRow(folder, depth, expanded), rowParams(null)));
            if (expanded) {
                addTreeLevel(actions, folder.id(), depth + 1, childFolders, visibleFolders, visited);
            }
        }
    }

    private void addEntries(List<Runnable> actions, UUID parentId,
                            Map<UUID, List<DataLibraryRepository.Entry>> childEntries,
                            Set<UUID> matchedEntries) {
        List<DataLibraryRepository.Entry> candidates = includeSubfolders
                ? snapshot.entries.values().stream().filter(entry -> inFolderScope(entry.parentId()))
                    .toList()
                : childEntries.getOrDefault(parentId, List.of());
        List<DataLibraryRepository.Entry> entries = candidates.stream()
                .filter(entry -> DataLibraryTypes.supports(entry.type()) && matchedEntries.contains(entry.id()))
                .sorted(Comparator.comparing(DataLibraryRepository.Entry::key, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DataLibraryRepository.Entry::id)).toList();
        int rowIndex = 0;
        for (var entry : entries) {
            int index = rowIndex++;
            actions.add(() -> addEntryRow(rightHost, entry, index));
        }
    }

    private void addEntryRow(LinearLayout rows, DataLibraryRepository.Entry entry, int rowIndex) {
        renderedEntries.add(entry.id());
        View card = createCard(entry);
        cards.get(entry.id()).presentation.setAlternate((rowIndex & 1) != 0);
        cards.get(entry.id()).presentation.setRowNumber(rowIndex + 1);
        if (card.getParent() instanceof ViewGroup parent) parent.removeView(card);
        rows.addView(card, new LayoutParams(-1, -2));
    }

    private View createFolderRow(DataLibraryRepository.Folder folder, int depth, boolean expanded) {
        DataLibraryDragSurface row = dragSurface(() -> dragPayloadForFolder(folder.id()));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(TREE_ROW_PADDING_DP + depth * TREE_INDENT_DP), 0, px(TREE_ROW_PADDING_DP), 0);
        boolean active = Objects.equals(activeFolder, folder.id());
        boolean selectedFolder = selectedFolders.contains(folder.id());
        // Tree rows are deliberately flat and borderless at every depth.  Keep the
        // unselected row transparent so the hierarchy reads as one continuous tree;
        // only the active/checked state gets a quiet fill.
        int rowFill = active || selectedFolder ? UiThemes.EDITOR.selection().fill() : 0;
        row.setBackground(solid(rowFill, 0f, 0));
        row.setOnClickListener(v -> { activeFolder = folder.id(); rebuild(); });
        UiIconButton disclosure = disclosureButton(expanded, TEXT);
        disclosure.setOnClickListener(v -> {
            if (!expandedFolders.remove(folder.id())) expandedFolders.add(folder.id());
            rebuild();
        });
        row.addView(disclosure, fixed(18, 28));

        TextView name = new UiDisplayText(getContext(), folder.name(), EDITOR_TEXT_SIZE_DP, TEXT);
        name.setSingleLine(true);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.contextMenu = event -> showRenameMenu(event, name, updatedName -> {
            DataLibraryRepository.Folder latest = repository.folders().stream()
                    .filter(candidate -> candidate.id().equals(folder.id())).findFirst().orElse(null);
            if (latest != null && !Objects.equals(latest.name(), updatedName)) {
                repository.updateFolder(latest, new DataLibraryRepository.Folder(
                        latest.id(), latest.parentId(), updatedName));
            }
        });
        LayoutParams nameLp = new LayoutParams(0, px(28), 1);
        nameLp.leftMargin = px(5);
        row.addView(name, nameLp);

        UiCheckBox check = new UiCheckBox(getContext());
        check.setContentDescription(tr("geometry_node.data_library.all"));
        Set<UUID> subtreeFolders = DataLibrarySelection.folderTreeIds(snapshot, folder.id());
        List<UUID> subtreeEntries = subtreeFolders.stream()
                .flatMap(id -> snapshot.childEntries.getOrDefault(id, List.of()).stream())
                .map(DataLibraryRepository.Entry::id).toList();
        Runnable syncCheck = () -> syncSelectionCheck(check,
                subtreeFolders.stream().filter(selectedFolders::contains).count()
                        + subtreeEntries.stream().filter(id -> selected.contains(new DataLibraryRepository.EntryKey(id))).count(),
                subtreeFolders.size() + subtreeEntries.size());
        syncCheck.run();
        check.setOnCheckedChangeListener((button, checked) -> {
            activeFolder = folder.id();
            selectFolderTree(folder.id(), checked);
            rebuild();
        });
        LayoutParams checkLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        checkLp.leftMargin = px(4);
        row.addView(check, checkLp);
        selectionViews.add(() -> {
            syncCheck.run();
            int fill = Objects.equals(activeFolder, folder.id()) || selectedFolders.contains(folder.id())
                    ? UiThemes.EDITOR.selection().fill() : 0;
            row.setBackground(solid(fill, 0f, 0));
        });
        folderDropRows.put(row, folder.id());
        return row;
    }

    private void selectFolderTree(UUID root, boolean checked) {
        selection.selectFolderTree(new DataLibrarySnapshot(repository), root, checked);
    }

    private View createRootRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(TREE_ROW_PADDING_DP), 0, px(TREE_ROW_PADDING_DP), 0);
        // Root follows the same flat tree treatment as child folders.
        row.setBackground(solid(activeFolder == null ? UiThemes.EDITOR.selection().fill() : 0, 0f, 0));
        row.setOnClickListener(v -> { clearSelection(); activeFolder = null; rebuild(); });
        UiIconButton disclosure = disclosureButton(rootExpanded, TEXT);
        disclosure.setOnClickListener(v -> {
            rootExpanded = !rootExpanded;
            rebuild();
        });
        row.addView(disclosure, fixed(18, 28));
        UiCheckBox check = new UiCheckBox(getContext());
        check.setContentDescription(tr("geometry_node.data_library.all"));
        Runnable syncCheck = () -> syncSelectionCheck(check,
                snapshot.entries.keySet().stream().filter(id -> selected.contains(new DataLibraryRepository.EntryKey(id))).count()
                        + snapshot.folders.keySet().stream().filter(selectedFolders::contains).count(),
                snapshot.entries.size() + snapshot.folders.size());
        syncCheck.run();
        selectionViews.add(syncCheck);
        check.setOnCheckedChangeListener((button, checked) -> selectAllVisible(checked));
        TextView name = label(getContext(), tr("geometry_node.data_library.root"), 11.5f, TEXT);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LayoutParams nameLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        nameLp.leftMargin = px(5);
        row.addView(name, nameLp);
        LayoutParams rootCheckLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        rootCheckLp.leftMargin = px(4);
        row.addView(check, rootCheckLp);
        return row;
    }

    private static void syncSelectionCheck(UiCheckBox check, long count, long total) {
        check.setChecked(total > 0 && count == total);
        check.setIndeterminate(count > 0 && count < total);
    }

    private UiIconButton disclosureButton(boolean expanded, int color) {
        UiIconButton button = new UiIconButton(getContext(),
                new SvgIconView(getContext(), SvgIconView.Icon.forExpandedState(expanded), color),
                UiIconButton.Style.forPreset(UiThemes.EDITOR, UiIconButton.Preset.INLINE).withSize(0, 0, 12));
        button.setContentDescription(tr(expanded ? "geometry_node.data_library.collapse_entry"
                : "geometry_node.data_library.expand_entry"));
        return button;
    }

    private static LayoutParams rowParams(PortType type) {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                type == null ? px(28) : ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(2);
        return params;
    }

    private boolean matches(DataLibraryRepository.Entry entry) {
        if (typeFilter != null && entry.type() != typeFilter) return false;
        if (!matchesColumns(entry, columnFilters)) return false;
        return registryTypeFilter.isEmpty() || entityTypes.registryType(entry)
                .toLowerCase(Locale.ROOT).contains(registryTypeFilter.toLowerCase(Locale.ROOT));
    }

    private boolean isFiltering() {
        return typeFilter != null || columnFilters.active() || !registryTypeFilter.isEmpty();
    }

    static boolean matchesColumns(DataLibraryRepository.Entry entry, ColumnFilters filters) {
        Double numeric = entry.value() instanceof Number number ? number.doubleValue() : null;
        return filters.matches(entry.key(), entry.type().name(), numeric, null, null, null, null, null, entry.tags());
    }

    private void setColumnFilters(ColumnFilters filters) {
        if (disposed || suspended || !Objects.equals(stateScope, UiStateScopes.current())) return;
        // Filtering preserves selection in other folders, including entries hidden by this query.
        columnFilters = filters;
        updateFilterIndicator();
        rebuild();
    }

    private void setRegistryTypeFilter(String value) {
        if (disposed || suspended || !Objects.equals(stateScope, UiStateScopes.current())) return;
        registryTypeFilter = Objects.requireNonNullElse(value, "").trim();
        updateFilterIndicator();
        rebuild();
    }

    private void updateFilterIndicator() {
        filterHeaders.forEach((key, header) -> header.setFilterActive(switch (key) {
            case "name" -> !columnFilters.name().isEmpty();
            case "type" -> columnFilters.types().active();
            case "registry_type" -> !registryTypeFilter.isEmpty();
            case "value" -> columnFilters.value().active();
            case "tags" -> !columnFilters.includedTags().isEmpty() || !columnFilters.excludedTags().isEmpty();
            default -> false;
        }));
    }

    private void showColumnFilter(String key, View anchor) {
        if (disposed || suspended || !Objects.equals(stateScope, UiStateScopes.current())) return;
        dismissMenus();
        if (filterPopover == null) filterPopover = new UiColumnFilterPopover(getContext(), overlayHost());
        switch (key) {
            case "name" -> filterPopover.showText(anchor, tr("geometry_node.data_library.column_name"),
                    columnFilters.name(), value -> setColumnFilters(columnFilters.withName(value)));
            case "type" -> {
                Map<String, String> options = new java.util.LinkedHashMap<>();
                for (PortType type : PortType.values()) {
                    if (DataLibraryTypes.supports(type)) options.put(type.name(), displayTypeName(type));
                }
                filterPopover.showChoices(anchor, tr("geometry_node.database.type"), options,
                        columnFilters.types(), value -> setColumnFilters(columnFilters.withTypes(value)));
            }
            case "registry_type" -> filterPopover.showText(anchor, tr("geometry_node.database.registry_type"),
                    registryTypeFilter, this::setRegistryTypeFilter);
            case "value" -> filterPopover.showNumber(anchor, tr("geometry_node.data_library.column_value"),
                    columnFilters.value(), value -> setColumnFilters(columnFilters.withValue(value)));
            case "tags" -> filterPopover.showTags(anchor, columnFilters.includedTags(), columnFilters.excludedTags(),
                    (included, excluded) -> setColumnFilters(columnFilters.withTags(included, excluded)));
            default -> { }
        }
    }

    private void dismissFilters() { if (filterPopover != null) filterPopover.dismiss(); }

    private void dismissMenus() {
        renameGeneration++;
        if (activeMenu != null) { activeMenu.dismiss(); activeMenu = null; }
        if (renameMenu != null) { renameMenu.dismiss(); renameMenu = null; }
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        dismissFilters();
        dismissMenus();
    }

    private EditText styledNameEditor(String value) {
        EditText editor = new UiTextInput(getContext(), new UiTextInput.Style(
                EDITOR_TEXT_SIZE_DP, 2, 2, 1, TEXT, UiThemes.EDITOR.text().hint(), INPUT_BG, BORDER));
        editor.setSingleLine(true);
        editor.setText(value == null ? "" : value);
        editor.setTextColor(TEXT);
        editor.setPadding(px(2), 0, px(2), 0);
        UIUtils.setLockedTextSize(editor, EDITOR_TEXT_SIZE_DP);
        return editor;
    }

    private void showTypeMenu(View anchor, Consumer<PortType> consumer,
                              boolean includeAll) {
        if (!canRunLibraryCommand()) return;
        dismissMenus();
        long generation = stateGeneration;
        String scope = stateScope;
        List<String> options = new ArrayList<>();
        if (includeAll) options.add("*");
        Set<PortType> supportedTypes = DataLibraryTypes.supported();
        supportedTypes.stream().sorted(Comparator.comparing(PortType::name))
                .map(Enum::name).forEach(options::add);
        Map<String, String> labels = new HashMap<>();
        for (PortType type : supportedTypes) labels.put(type.name(), displayTypeName(type));
        if (includeAll) labels.put("*", tr("geometry_node.data_library.all_types"));
        UiSearchableMenu menu = new UiSearchableMenu(
                getContext(), tr("geometry_node.data_library.choose_type"), options, labels, value -> {
            if (consumer == null || !canRunLibraryCommand() || generation != stateGeneration
                    || !Objects.equals(scope, stateScope)) return;
            consumer.accept("*".equals(value) ? null : PortType.valueOf(value));
        });
        activeMenu = menu;
        menu.showAt(anchor, overlayHost());
    }

    private View createCard(DataLibraryRepository.Entry entry) {
        CardBinding cached = cards.get(entry.id());
        if (cached != null) {
            refreshCard(cached, entry);
            return cached.card;
        }
        AtomicReference<DataLibraryRepository.Entry> current = new AtomicReference<>(entry);
        DataLibraryDragSurface card = dragSurface(() -> dragPayloadForEntry(current.get()));
        card.setOnClickListener(v -> {
            selection.selectEntry(entry.id(), card.shiftPressed, card.ctrlPressed);
            updateSelectionViews();
        });
        DataLibraryEntryCard presentation = new DataLibraryEntryCard(card, columns, entry.key(),
                displayTypeName(entry.type()), entry.type(),
                selected.contains(key(entry)), () -> toggleEntry(entry.id()), checked -> {
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            selection.anchor = entry.id();
            updateSelectionViews();
        });
        TextView name = presentation.nameView();
        String dataScope = stateScope;
        presentation.setManageDataAction(() -> {
            if (disposed || suspended || !Objects.equals(dataScope, stateScope)
                    || !Objects.equals(dataScope, UiStateScopes.current())) return;
            DataLibraryRepository.Entry live = repository.findEntry(entry.id());
            if (live == null) return;
            selected.clear(); selected.add(key(live)); selection.folders.clear();
            selection.anchor = live.id(); updateSelectionViews();
            var binding = com.mine.geometry_node.client.ui.components.integration.metadata.RepositoryDataDictionaryBindings.library(
                    repository, live.id(), () -> !disposed && !suspended
                            && Objects.equals(dataScope, stateScope) && Objects.equals(dataScope, UiStateScopes.current()));
            com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor.show(
                    this, live.key() + " · " + tr("geometry_node.database.shared_entry"), binding);
        });
        card.contextMenu = event -> showRenameMenu(event, name, updatedName -> {
            if (current.get() == null) return;
            DataLibraryRepository.Entry latest = repository.findEntry(entry.id());
            if (latest != null && latest.type() == entry.type() && !Objects.equals(latest.key(), updatedName)) {
                repository.update(latest, new DataLibraryRepository.Entry(
                        latest.id(), latest.parentId(), latest.type(), updatedName, latest.value(), latest.tags(), latest.data()));
            }
        });
        CardBinding binding = new CardBinding(presentation, current, entry);
        binding.authoritativeRevision = authoritativeRevision;
        cards.put(entry.id(), binding);
        refreshCard(binding, entry);
        return card;
    }

    private void updateRowSummary(DataLibraryEntryCard presentation, DataLibraryRepository.Entry entry) {
        String summary = DataLibraryValueEditors.compactValueSummary(entry).replace('\n', ' ').replace('\r', ' ');
        presentation.setSummary(entry, summary, entityTypes.registryType(entry));
    }

    private static final class CardBinding {
        final DataLibraryDragSurface card;
        final DataLibraryEntryCard presentation;
        final AtomicReference<DataLibraryRepository.Entry> current;
        DataLibraryRepository.Entry displayed;
        long authoritativeRevision;

        CardBinding(DataLibraryEntryCard presentation, AtomicReference<DataLibraryRepository.Entry> current,
                    DataLibraryRepository.Entry displayed) {
            this.card = presentation.surface();
            this.presentation = presentation;
            this.current = current;
            this.displayed = displayed;
        }
    }

    private void toggleEntry(UUID entryId) {
        View focused = findFocus();
        if (focused != null) focused.clearFocus();
        if (!expandedEntries.remove(entryId)) expandedEntries.add(entryId);
        CardBinding binding = cards.get(entryId);
        DataLibraryRepository.Entry latest = repository.findEntry(entryId);
        observeAuthority();
        if (binding != null && latest != null && latest.type() == binding.current.get().type()) {
            refreshCard(binding, latest);
        } else {
            scheduleRefresh();
        }
    }

    private void showRenameMenu(MotionEvent event, TextView name, Consumer<String> rename) {
        if (disposed || suspended) return;
        dismissMenus();
        ViewGroup host = overlayHost();
        int[] location = new int[2];
        host.getLocationOnScreen(location);
        UiContextMenu menu = new UiContextMenu(getContext());
        long generation = renameGeneration;
        menu.addMenuItem(tr("geometry_node.asset_library.action.rename"),
                () -> post(() -> {
                    if (generation == renameGeneration) beginRename(name, rename);
                }));
        renameMenu = menu;
        menu.showAt(event.getRawX() - location[0], event.getRawY() - location[1], host);
    }

    private void beginRename(TextView name, Consumer<String> rename) {
        if (disposed || suspended || !Objects.equals(stateScope, UiStateScopes.current()) || !isDescendant(name, this)) return;
        if (!(name.getParent() instanceof ViewGroup parent)) return;
        long generation = stateGeneration;
        String scope = stateScope;
        EditText input = styledNameEditor(name.getText().toString());
        int index = parent.indexOfChild(name);
        ViewGroup.LayoutParams params = name.getLayoutParams();
        // A UiTableRow assigns columns by child index: replace the name in place while editing.
        parent.removeView(name);
        parent.addView(input, index, params);
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<Boolean> finish = commit -> {
            if (!finished.compareAndSet(false, true)) return;
            boolean canCommit = !disposed && !suspended && generation == stateGeneration
                    && Objects.equals(scope, UiStateScopes.current()) && isDescendant(input, this);
            String updatedName = input.getText().toString().trim();
            parent.removeView(input);
            parent.addView(name, Math.min(index, parent.getChildCount()), params);
            if (commit && canCommit && !updatedName.isEmpty()) rename.accept(updatedName);
            scheduleRefresh();
        };
        input.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEY_ENTER || keyCode == KeyEvent.KEY_ESCAPE) {
                finish.accept(keyCode == KeyEvent.KEY_ENTER);
                return true;
            }
            return false;
        });
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused) finish.accept(true);
        });
        input.requestFocus();
        input.selectAll();
    }

    private DataLibraryDragSurface dragSurface(Supplier<DataLibraryDragPayload> payload) {
        return new DataLibraryDragSurface(getContext(), this, payload,
                () -> internalDragActive = true, this::finishInternalDrag,
                event -> updateDropIndicator(event.getRawX(), event.getRawY()));
    }

    private DataLibraryDragPayload dragPayloadForEntry(DataLibraryRepository.Entry entry) {
        Set<UUID> ids = selected.stream().map(DataLibraryRepository.EntryKey::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!ids.contains(entry.id())) ids = Set.of(entry.id());
        return ids.size() == 1 ? DataLibraryDragPayload.entry(entry.id(), entry.type(), entry.key())
                : DataLibraryDragPayload.entries(entry.id(), entry.type(), entry.key(), ids);
    }

    private DataLibraryDragPayload dragPayloadForFolder(UUID id) {
        Set<UUID> roots = DataLibraryDropPolicy.draggedFolderRoots(id, selectedFolders,
                new DataLibrarySnapshot(repository));
        UUID anchor = roots.contains(id) ? id : roots.iterator().next();
        return roots.size() == 1 ? DataLibraryDragPayload.folder(anchor)
                : DataLibraryDragPayload.folders(anchor, roots);
    }

    private void finishInternalDrag() {
        internalDragActive = false;
        groupsHost.hideIndicator();
        if (!rebuildPending) return;
        rebuildPending = false;
        scheduleRefresh();
    }

    private void updateDropIndicator(float rawX, float rawY) {
        if (!canRunLibraryCommand()) {
            groupsHost.hideIndicator();
            return;
        }
        WorkspaceDragState.Session session = WorkspaceDragService.INSTANCE.current();
        DataLibraryDragPayload payload = session != null
                && session.payload() instanceof DataLibraryDragPayload dataLibraryPayload
                ? dataLibraryPayload : null;
        DropDestination destination = dropDestinationAt(rawX, rawY);
        if (destination == null || payload == null || snapshot == null
                || !DataLibraryDropPolicy.canMove(snapshot, payload, destination.parentId())) {
            groupsHost.hideIndicator();
            return;
        }
        groupsHost.updateIndicator(destination.lineY(), destination.lineLeft());
    }

    private boolean acceptInternalDrop(WorkspaceDragState.Session session, float rawX, float rawY) {
        if (!canRunLibraryCommand() || !(session.payload() instanceof DataLibraryDragPayload payload)) return false;
        DropDestination destination = dropDestinationAt(rawX, rawY);
        if (destination == null || !canMove(payload, destination.parentId())) return false;
        return movePayload(payload, destination.parentId());
    }

    private DropDestination dropDestinationAt(float rawX, float rawY) {
        if (!isRawPointInside(scrollHost, rawX, rawY)) return null;
        int[] location = new int[2];
        groupsHost.getLocationOnScreen(location);
        return dropDestinationInTree(rawX - location[0], rawY - location[1]);
    }

    /** Tree-local coordinates keep the visible line and committed parent identical while scrolling. */
    private DropDestination dropDestinationInTree(float x, float y) {
        if (rootDropTarget == null || x < 0 || x >= groupsHost.getWidth() || y < 0) return null;
        int rootIndent = rootDropTarget.getLeft() + rootDropTarget.getPaddingLeft() + px(TREE_INDENT_DP);
        if (y <= rootDropTarget.getBottom()) {
            return new DropDestination(null, rootDropTarget.getBottom(), rootIndent);
        }
        Map<UUID, DataLibraryRepository.Folder> folders = snapshot.folders;
        int lastBottom = rootDropTarget.getBottom();
        for (Map.Entry<View, UUID> candidate : folderDropRows.entrySet()) {
            View row = candidate.getKey();
            lastBottom = Math.max(lastBottom, row.getBottom());
            if (y > row.getBottom()) continue;
            DataLibraryRepository.Folder folder = folders.get(candidate.getValue());
            if (folder == null) return null;
            int indent = row.getLeft() + row.getPaddingLeft();
            int edge = Math.min(px(6), row.getHeight() / 4);
            boolean before = y < row.getTop() + edge;
            boolean after = y >= row.getBottom() - edge;
            if (!before && !after && x >= indent) {
                // Center of a folder: insert into it, one indentation level deeper.
                return new DropDestination(folder.id(), row.getBottom(), indent + px(TREE_INDENT_DP));
            }
            // Row boundaries (and its left gutter) accept siblings. Moving left promotes them.
            while (folder.parentId() != null && x < indent - px(TREE_INDENT_DP / 2f)) {
                DataLibraryRepository.Folder ancestor = folders.get(folder.parentId());
                if (ancestor == null) return null;
                folder = ancestor;
                indent -= px(TREE_INDENT_DP);
            }
            return new DropDestination(folder.parentId(), before ? row.getTop() : row.getBottom(), indent);
        }
        // Empty space after the tree is an explicit root-level destination.
        return new DropDestination(null, lastBottom, rootIndent);
    }

    private boolean movePayload(DataLibraryDragPayload payload, UUID targetParent) {
        if (!canRunLibraryCommand()) return false;
        DataLibrarySnapshot latest = new DataLibrarySnapshot(repository);
        if (!DataLibraryDropPolicy.canMove(latest, payload, targetParent)) return false;
        rootExpanded = true;
        rebuildPending = true;
        if (targetParent != null) expandedFolders.add(targetParent);
        if (payload.isEntry()) {
            for (UUID id : payload.entryIds()) repository.moveEntry(id, targetParent);
        } else {
            for (UUID id : payload.folderIds()) repository.moveFolder(id, targetParent);
        }
        return true;
    }

    private boolean canMove(DataLibraryDragPayload payload, UUID targetParent) {
        return canRunLibraryCommand()
                && DataLibraryDropPolicy.canMove(new DataLibrarySnapshot(repository), payload, targetParent);
    }

    private static boolean isRawPointInside(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0] && rawX < location[0] + view.getWidth()
                && rawY >= location[1] && rawY < location[1] + view.getHeight();
    }

    private record DropDestination(UUID parentId, int lineY, int lineLeft) {}

    private void clearSelection() {
        if (selected.isEmpty() && selectedFolders.isEmpty()) return;
        selection.clear();
        updateSelectionViews();
    }

    private void selectAllVisible(boolean checked) {
        selection.selectAll(new DataLibrarySnapshot(repository), checked);
        updateSelectionViews();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (disposed || suspended) return false;
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (disposed || suspended) return false;
        if (event.isCanceled() || findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
        KeyBinding selectAllBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SELECT_ALL));
        if (UiInputBindings.matches(selectAllBinding, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !mountWork.isRunning()
                    && Objects.equals(stateScope, UiStateScopes.current()) && folderSnapshotReady()) {
                selection.selectRendered(true);
                updateSelectionViews();
            }
            return true;
        }
        KeyBinding deleteBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_DELETE));
        if (UiInputBindings.matches(deleteBinding, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !mountWork.isRunning()
                    && Objects.equals(stateScope, UiStateScopes.current()) && folderSnapshotReady()
                    && deleteButton.isEnabled()) deleteButton.performClick();
            return true;
        }
        KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
        KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
        if (copy != null && UiInputBindings.matches(copy, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                clipboardEntry = selected.size() == 1
                        ? repository.findEntry(selected.iterator().next().id()) : null;
            }
            return true;
        }
        if (paste != null && UiInputBindings.matches(paste, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && clipboardEntry != null && selected.size() == 1) {
                DataLibraryRepository.Entry target = repository.findEntry(selected.iterator().next().id());
                if (target != null && target.type() == clipboardEntry.type()) valueEditors.updateValue(
                        new AtomicReference<>(target), clipboardEntry.value());
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateActions() {
        deleteButton.setEnabled(!selected.isEmpty() || !selectedFolders.isEmpty());
        if (snapshot != null) {
            long count = snapshot.entries.values().stream().filter(entry -> DataLibraryTypes.supports(entry.type())
                    && inFolderScope(entry.parentId()) && matches(entry)).count();
            resultsView.setText(tr("geometry_node.database.all_objects") + " · " + count);
            String scope = tr(includeSubfolders ? "geometry_node.data_library.recursive_scope"
                    : "geometry_node.data_library.current_folder_only");
            statusView.setText(Component.translatable("geometry_node.data_library.result_status",
                    count, selected.size(), scope).getString());
        }
    }

    private static DataLibraryRepository.EntryKey key(DataLibraryRepository.Entry entry) {
        return new DataLibraryRepository.EntryKey(entry.id());
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String displayTypeName(PortType type) {
        if (type == null) return "";
        return tr(type.getTranslationKey());
    }

    private ViewGroup overlayHost() {
        View current = this;
        ViewGroup fallback = this;
        while (current.getParent() instanceof ViewGroup parent) {
            fallback = parent;
            if (parent instanceof FrameLayout) return parent;
            current = parent;
        }
        return fallback;
    }

    private static TextView label(Context context, String text, float size, int color) {
        return UIUtils.createLockedTextView(context, text, size, color);
    }

    private static LayoutParams fixed(int widthDp, int heightDp) {
        return new LayoutParams(px(widthDp), px(heightDp));
    }

    private static int px(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private static ShapeDrawable solid(int color, float radius) {
        return solid(color, radius, 0);
    }

    private static ShapeDrawable solid(int color, float radius, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radius));
        if (strokeColor != 0) drawable.setStroke(Math.max(1, px(1)), strokeColor);
        return drawable;
    }

    private void saveState() {
        if (restoringState || stateScope == null) return;
        uiState.write(DatabaseViewState.LIBRARY, stateScope, new DatabaseViewState.Library(
                typeFilter == null ? "" : typeFilter.name(), "", includeSubfolders,
                activeFolder == null ? "" : activeFolder.toString(), registryTypeFilter));
        uiState.write(DatabaseViewState.LIBRARY_COLUMN_FILTERS, stateScope, columnFilters);
    }

    private void switchScope() {
        entityTypes.cancel(true);
        cancelMount();
        removeCallbacks(refreshTask); refreshScheduled.set(false);
        dismissFilters(); dismissMenus();
        saveState();
        scopeSnapshotConfirmed = false; authoritativeQueued.set(false);
        // Retire editor references before detaching: blur or delayed picker callbacks must not write into another scope.
        cards.values().forEach(binding -> binding.current.set(null));
        cards.clear(); selectionViews.clear(); renderedEntries.clear();
        rightHost.removeAllViews(); groupsHost.removeAllViews(); typeNavigation.removeAllViews();
        folderDropRows.clear(); snapshot = null;
        clearSelection(); updateSelectionViews();
        expandedFolders.clear(); expandedEntries.clear();
        mountLeftAnchor = null; mountRightAnchor = null;
        pendingLeftAnchor = null; pendingRightAnchor = null;
        restoreState();
        structurePending = true;
    }
    private void restoreState() {
        stateScope = UiStateScopes.current();
        stateGeneration++;
        var value = uiState.read(DatabaseViewState.LIBRARY, stateScope);
        restoringState = true;
        try {
            registryTypeFilter = value.registryType();
            try { typeFilter = PortType.valueOf(value.type()); }
            catch (IllegalArgumentException ignored) { typeFilter = null; }
            columnFilters = uiState.read(DatabaseViewState.LIBRARY_COLUMN_FILTERS, stateScope);
            if (!value.tag().isEmpty()) {
                Set<String> included = new LinkedHashSet<>(columnFilters.includedTags());
                included.add(value.tag());
                columnFilters = columnFilters.withTags(included, columnFilters.excludedTags());
            }
            includeSubfolders = value.recursive();
            try { activeFolder = UUID.fromString(value.activeFolder()); }
            catch (IllegalArgumentException ignored) { activeFolder = null; }
            updateScopeButton(); updateFilterIndicator();
        } finally { restoringState = false; }
    }

    public void onShow() {
        if (disposed) return;
        if (!Objects.equals(stateScope, UiStateScopes.current())) {
            switchScope();
        }
        suspended = false;
        removeCallbacks(entityTypeRefresh);
        postDelayed(entityTypeRefresh, 1000);
        if (!dropTargetRegistered) {
            WorkspaceDragDropRegistry.register(dropTarget);
            dropTargetRegistered = true;
        }
        refresh();
        scheduleRefresh();
    }

    public void onDispose() {
        if (disposed) return;
        saveState();
        disposed = true;
        removeCallbacks(entityTypeRefresh);
        entityTypes.cancel(true);
        cancelMount();
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        dismissFilters();
        dismissMenus();
        removeCallbacks(refreshTask);
        removeCallbacks(retryRefresh);
        refreshScheduled.set(false);
        retryScheduled = false;
        cards.clear();
        selectionViews.clear();
        cancelInternalDrag();
        unregisterDropTarget();
        repository.removeChangeListener(repositoryChanged);
    }
    public void onHide() {
        saveState();
        suspended = true;
        removeCallbacks(entityTypeRefresh);
        entityTypes.cancel(false);
        cancelMount();
        dismissFilters();
        dismissMenus();
        removeCallbacks(refreshTask);
        refreshScheduled.set(false);
        removeCallbacks(retryRefresh);
        retryScheduled = false;
        cancelInternalDrag();
        unregisterDropTarget();
    }

    private void cancelInternalDrag() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        internalDragActive = false;
        rebuildPending = false;
        groupsHost.hideIndicator();
    }

    private void unregisterDropTarget() {
        if (!dropTargetRegistered) return;
        groupsHost.hideIndicator();
        WorkspaceDragDropRegistry.unregister(dropTarget);
        dropTargetRegistered = false;
    }
}
