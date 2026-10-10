package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import com.mine.geometry_node.client.ui.editor.asset.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers.SelectHintRenderer;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragDropRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragState;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryTypes;
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
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
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
public final class DataLibraryWindow extends LinearLayout implements AreaEditorWindow {
    private static final int BG = 0xFF1E1E1E;
    private static final int INPUT_BG = 0xFF202020;
    private static final int BORDER = 0xFF444444;
    private static final int DIVIDER = 0xFF383838;
    private static final int TEXT = 0xFFD2D2D2;
    private static final int MUTED = 0xFF969696;
    private static final int ACCENT = 0xFF5C8CCB;
    private static final int TREE_INDENT_DP = 20;
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;
    private static final EnumSet<PortType> SUPPORTED_TYPES =
            EnumSet.copyOf(DataLibraryTypes.supported());

    private final DataLibraryUiRepository repository;
    private final DataLibraryTreeLayout groupsHost;
    private final DataLibraryTreeLayout rightHost;
    private final ScrollView scrollHost;
    private final ScrollView rightScroll;
    private final DataLibraryValueEditors valueEditors;
    private final TextView breadcrumbView;
    private final TextView statusView;
    private final UiSearchInput searchInput;
    private final UiActionButton typeButton;
    private final UiIconButton addFolderButton;
    private final View deleteButton;
    private final DataLibrarySelection selection = new DataLibrarySelection();
    private final Set<DataLibraryUiRepository.EntryKey> selected = selection.entries;
    private final Set<UUID> selectedFolders = selection.folders;
    private final Set<UUID> expandedFolders = new HashSet<>();
    private final Set<UUID> expandedEntries = new HashSet<>();
    /** Expanded state for the type blocks shown inside each folder. */
    private final Set<String> expandedTypeGroups = new HashSet<>();
    private final Set<UUID> renderedEntries = selection.rendered;
    private UUID activeFolder;
    private final Map<View, UUID> folderDropRows = new java.util.LinkedHashMap<>();
    private final WorkspaceDragDropRegistry.DropTarget dropTarget = this::acceptInternalDrop;
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
    private String search = "";
    private PortType typeFilter;
    private boolean rootExpanded = true;
    private boolean internalDragActive;
    private boolean rebuildPending;
    private static DataLibraryUiRepository.Entry clipboardEntry;

    public DataLibraryWindow(Context context) {
        this(context, DataLibraryUiRepository.EMPTY);
    }

    public DataLibraryWindow(Context context, DataLibraryUiRepository repository) {
        super(context);
        this.repository = repository != null ? repository : DataLibraryUiRepository.EMPTY;
        this.valueEditors = new DataLibraryValueEditors(context, this.repository,
                this::overlayHost, this::refresh, this::scheduleRefresh, () -> !disposed);
        this.repository.addChangeListener(repositoryChanged);
        setOrientation(VERTICAL);
        setBackground(solid(BG, 0));

        DataLibraryToolbar toolbar = new DataLibraryToolbar(context,
                () -> { prepareCreation(); this.repository.createFolder(activeFolder); },
                anchor -> showTypeMenu(anchor, type -> {
                    prepareCreation();
                    String groupKey = (activeFolder == null ? "root" : activeFolder.toString()) + ":" + type.name();
                    expandedTypeGroups.remove("!" + groupKey);
                    this.repository.create(activeFolder, type);
                }, false),
                () -> {
                    if (selected.isEmpty() && selectedFolders.isEmpty()) return;
                    Set<DataLibraryUiRepository.EntryKey> entries = Set.copyOf(selected);
                    Set<UUID> folders = Set.copyOf(selectedFolders);
                    clearSelection();
                    this.repository.delete(entries, folders);
                },
                anchor -> showTypeMenu(anchor, this::setTypeFilter, true),
                this::applySearch, this::clearSearch);
        searchInput = toolbar.searchInput;
        typeButton = toolbar.typeButton;
        addFolderButton = toolbar.addFolderButton;
        deleteButton = toolbar.deleteButton;
        addView(toolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(76)));

        scrollHost = new ScrollView(context);
        scrollHost.setFillViewport(true);
        groupsHost = new DataLibraryTreeLayout(context);
        groupsHost.setOrientation(VERTICAL);
        groupsHost.setPadding(px(4), px(4), px(4), px(8));
        scrollHost.addView(groupsHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightHost = new DataLibraryTreeLayout(context);
        rightHost.setOrientation(VERTICAL);
        rightHost.setPadding(px(8), px(4), px(8), px(8));
        rightScroll = new ScrollView(context);
        rightScroll.setFillViewport(true);
        rightScroll.addView(rightHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout leftPanel = new LinearLayout(context);
        leftPanel.setOrientation(VERTICAL);
        leftPanel.setPadding(px(8), px(8), px(4), px(6));
        leftPanel.setBackground(solid(BG, 0, BORDER));
        TextView leftTitle = label(context, tr("geometry_node.workspace.area.data_library"), 11, MUTED);
        leftTitle.setPadding(px(2), 0, 0, px(5));
        leftPanel.addView(leftTitle, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(22)));
        leftPanel.addView(scrollHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout rightPanel = new LinearLayout(context);
        rightPanel.setOrientation(VERTICAL);
        rightPanel.setPadding(px(4), px(8), px(8), px(6));
        rightPanel.setBackground(solid(BG, 0, BORDER));
        breadcrumbView = label(context, tr("geometry_node.data_library.root"), 11, MUTED);
        breadcrumbView.setPadding(px(8), 0, 0, px(5));
        rightPanel.addView(breadcrumbView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(22)));
        rightPanel.addView(rightScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        groupsHost.setOnClickListener(v -> clearSelection());
        rightHost.setOnClickListener(v -> clearSelection());
        scrollHost.setOnClickListener(v -> clearSelection());
        rightScroll.setOnClickListener(v -> clearSelection());
        statusView = label(context, "", 9.5f, MUTED);
        statusView.setVisibility(View.GONE);

        LinearLayout split = new LinearLayout(context);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.setBackground(solid(BG, 0));
        split.addView(leftPanel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.24f));
        split.addView(ResizableDivider.weighted(context,
                ResizableDivider.Orientation.HORIZONTAL), new LinearLayout.LayoutParams(
                px(5), ViewGroup.LayoutParams.MATCH_PARENT));
        split.addView(rightPanel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.76f));
        addView(split, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        rebuild();
    }

    private void setTypeFilter(PortType type) {
        typeFilter = type;
        typeButton.setText(type == null ? tr("geometry_node.data_library.all_types") : displayTypeName(type));
        rebuild();
    }

    private void applySearch() {
        search = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        searchInput.clearFocus();
        rootExpanded = true;
        rebuild();
    }

    private void clearSearch() {
        searchInput.setText("");
        search = "";
        rebuild();
    }

    private void prepareCreation() {
        // New folders and entries must remain visible even when a filter was active.
        typeFilter = null;
        typeButton.setText(tr("geometry_node.data_library.all_types"));
        rootExpanded = true;
        Map<UUID, DataLibraryUiRepository.Folder> folders = new HashMap<>();
        for (DataLibraryUiRepository.Folder folder : repository.folders()) {
            if (folder != null && folder.id() != null) folders.put(folder.id(), folder);
        }
        collectAncestors(activeFolder, folders, expandedFolders);
        clearSearch();
    }

    private void refresh() {
        if (!disposed) repository.refresh(this::scheduleRefresh);
    }

    private void scheduleRefresh() {
        if (disposed || suspended || !refreshScheduled.compareAndSet(false, true)) return;
        post(refreshTask);
    }

    private void requestRebuild() {
        if (disposed || suspended) return;
        if (internalDragActive || reconciling) {
            rebuildPending = true;
            return;
        }
        DataLibrarySnapshot next = new DataLibrarySnapshot(repository);
        observeAuthority();
        if (!structurePending && next.sameStructure(snapshot)) {
            snapshot = next;
            refreshCardValues(next);
            return;
        }
        rebuild(next);
    }

    private void rebuild() {
        if (disposed) return;
        // Explicit navigation keeps its original focus-loss submission semantics.
        View focused = findFocus();
        if (focused instanceof EditText && focused != searchInput) focused.clearFocus();
        observeAuthority();
        rebuild(new DataLibrarySnapshot(repository));
    }

    private void observeAuthority() {
        if (authoritativeQueued.getAndSet(false)) authoritativeRevision++;
    }

    private void rebuild(DataLibrarySnapshot next) {
        if (internalDragActive || reconciling) {
            rebuildPending = true;
            return;
        }
        // Deleted/type-changed entries must stop accepting edits even while another draft is open.
        retireMissingCards(next);
        folderDropRows.entrySet().removeIf(item -> {
            if (next.folders.containsKey(item.getValue())) return false;
            View row = item.getKey();
            if (row.getParent() instanceof ViewGroup parent) parent.removeView(row);
            return true;
        });
        if (hasActiveDraft()) {
            structurePending = true;
            refreshCardValues(next);
            deferRefresh();
            return;
        }
        reconciling = true;
        structurePending = false;
        DataLibraryScrollAnchor leftAnchor = DataLibraryScrollAnchor.capture(scrollHost, folderRowsById());
        DataLibraryScrollAnchor rightAnchor = DataLibraryScrollAnchor.capture(rightScroll, cardViewsById());
        try {
            WorkspaceDragService.INSTANCE.cancelIfSource(this);
            snapshot = next;
            next.indexChildren();
            selection.retain(next);
            if (activeFolder != null && !next.folders.containsKey(activeFolder)) activeFolder = null;
            expandedFolders.retainAll(next.folders.keySet());
            expandedEntries.retainAll(next.entries.keySet());
            groupsHost.removeAllViews();
            groupsHost.hideIndicator();
            rightHost.removeAllViews();
            rightHost.hideIndicator();
            folderDropRows.clear();
            renderedEntries.clear();
            selectionViews.clear();
            rootDropTarget = createRootRow();
            groupsHost.addView(rootDropTarget, rowParams(null));
            Set<UUID> matchedEntries = new HashSet<>();
            Set<UUID> visibleFolders = new HashSet<>();
            for (var entry : next.entries.values()) {
                if (SUPPORTED_TYPES.contains(entry.type()) && matches(entry)) {
                    matchedEntries.add(entry.id());
                    collectAncestors(entry.parentId(), next.folders, visibleFolders);
                }
            }
            if (!search.isEmpty()) {
                for (var folder : next.folders.values()) {
                    if (folder.name().toLowerCase(Locale.ROOT).contains(search)) {
                        visibleFolders.add(folder.id());
                        collectAncestors(folder.parentId(), next.folders, visibleFolders);
                    }
                }
            }
            if (rootExpanded) {
                addTreeLevel(null, 1, next.childFolders, next.childEntries,
                        matchedEntries, visibleFolders, new HashSet<>());
            }
            addEntriesGrouped(activeFolder, 0, next.childEntries, matchedEntries);
            updateNavigationLabels();
            if (rightHost.getChildCount() == 0) {
                TextView empty = label(getContext(), tr(!isFiltering()
                        ? "geometry_node.data_library.empty" : "geometry_node.data_library.no_results"), 12, MUTED);
                empty.setGravity(Gravity.CENTER);
                rightHost.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(90)));
            }
            // Keep only mounted cards; hidden native previews must release their registrations.
            cards.keySet().retainAll(renderedEntries);
            updateSelectionViews();
            pendingLeftAnchor = leftAnchor;
            pendingRightAnchor = rightAnchor;
        } finally {
            reconciling = false;
        }
        if (rebuildPending) {
            rebuildPending = false;
            scheduleRefresh();
        }
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
            if (binding.card.getParent() instanceof ViewGroup parent) parent.removeView(binding.card);
            selected.remove(new DataLibraryUiRepository.EntryKey(item.getKey()));
            return true;
        });
    }

    private void refreshCardValues(DataLibrarySnapshot next) {
        for (var binding : cards.values()) {
            var entry = next.entries.get(binding.current.get().id());
            if (entry != null && entry.type() == binding.current.get().type()) refreshCard(binding, entry);
        }
    }

    private void refreshCard(CardBinding binding, DataLibraryUiRepository.Entry entry) {
        boolean expanded = expandedEntries.contains(entry.id());
        boolean changed = !sameValue(binding.displayed, entry);
        boolean localPending = !sameValue(binding.current.get(), binding.displayed);
        boolean authoritative = binding.authoritativeRevision < authoritativeRevision;
        boolean editing = binding.card.findFocus() instanceof EditText || valueEditors.isEditing(entry.id());
        if (editing) {
            if (changed || binding.presentation.isExpanded() != expanded) deferRefresh();
            return;
        }
        // A local write may still be in flight while its blur refresh sees the old snapshot.
        if (!changed && localPending && !authoritative) {
            if (binding.presentation.isExpanded() != expanded) {
                binding.presentation.setExpandedContent(expanded, expanded
                        ? valueEditors.createExpandedEditor(binding.current.get(), binding.current) : null);
            }
            return;
        }
        changed |= localPending && authoritative;
        binding.current.set(entry);
        binding.presentation.setName(entry.key());
        if (binding.presentation.isExpanded() != expanded || (expanded && changed)) {
            binding.presentation.setExpandedContent(expanded, expanded
                    ? valueEditors.createExpandedEditor(entry, binding.current) : null);
        }
        binding.displayed = entry;
        binding.authoritativeRevision = authoritativeRevision;
    }

    private static boolean sameValue(DataLibraryUiRepository.Entry left, DataLibraryUiRepository.Entry right) {
        if (left.type() != right.type()) return false;
        if (Objects.deepEquals(left.value(), right.value())) return true;
        try {
            return DataLibraryValueChanges.equivalent(left, right.value());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void updateSelectionViews() {
        for (var binding : cards.values()) {
            boolean checked = selected.contains(key(binding.current.get()));
            binding.presentation.setSelected(checked);
        }
        selectionViews.forEach(Runnable::run);
        updateActions();
    }

    private void updateNavigationLabels() {
        // Render the complete folder path so the right pane mirrors the asset
        // browser breadcrumb instead of showing only the immediate parent.
        List<String> path = new ArrayList<>();
        Map<UUID, DataLibraryUiRepository.Folder> folders = snapshot.folders;
        Set<UUID> visited = new HashSet<>();
        UUID cursor = activeFolder;
        while (cursor != null && visited.add(cursor)) {
            DataLibraryUiRepository.Folder folder = folders.get(cursor);
            if (folder == null) break;
            path.add(0, folder.name());
            cursor = folder.parentId();
        }
        StringBuilder current = new StringBuilder(tr("geometry_node.data_library.root"));
        for (String segment : path) current.append("  > ").append(segment);
        breadcrumbView.setText(current);
    }

    private void collectAncestors(UUID folderId, Map<UUID, DataLibraryUiRepository.Folder> foldersById,
                                  Set<UUID> destination) {
        Set<UUID> visited = new HashSet<>();
        while (folderId != null && visited.add(folderId)) {
            destination.add(folderId);
            DataLibraryUiRepository.Folder folder = foldersById.get(folderId);
            folderId = folder != null ? folder.parentId() : null;
        }
    }

    private void addTreeLevel(UUID parentId, int depth,
                              Map<UUID, List<DataLibraryUiRepository.Folder>> childFolders,
                              Map<UUID, List<DataLibraryUiRepository.Entry>> childEntries,
                              Set<UUID> matchedEntries, Set<UUID> visibleFolders, Set<UUID> visited) {
        for (DataLibraryUiRepository.Folder folder : childFolders.getOrDefault(parentId, List.of())) {
            if (!visited.add(folder.id())) continue;
            if (isFiltering() && !visibleFolders.contains(folder.id())) continue;
            boolean expanded = isFiltering() || expandedFolders.contains(folder.id());
            groupsHost.addView(createFolderRow(folder, depth, expanded), rowParams(null));
            if (expanded) {
                addTreeLevel(folder.id(), depth + 1, childFolders, childEntries,
                        matchedEntries, visibleFolders, visited);
            }
        }
    }

    private void addEntriesGrouped(UUID parentId, int depth,
                                   Map<UUID, List<DataLibraryUiRepository.Entry>> childEntries,
                                   Set<UUID> matchedEntries) {
        Map<PortType, List<DataLibraryUiRepository.Entry>> byType = new java.util.LinkedHashMap<>();
        for (DataLibraryUiRepository.Entry entry : childEntries.getOrDefault(parentId, List.of())) {
            if (!SUPPORTED_TYPES.contains(entry.type())) continue;
            if (isFiltering() && !matchedEntries.contains(entry.id())) continue;
            byType.computeIfAbsent(entry.type(), ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<PortType, List<DataLibraryUiRepository.Entry>> group : byType.entrySet()) {
            PortType type = group.getKey();
            String groupKey = (parentId == null ? "root" : parentId.toString()) + ":" + type.name();
            boolean expanded = !expandedTypeGroups.contains("!" + groupKey);
            DataLibraryDragSurface header = dragSurface(() -> {
                DataLibraryUiRepository.Entry first = group.getValue().getFirst();
                Set<UUID> ids = new LinkedHashSet<>();
                for (DataLibraryUiRepository.Entry entry : group.getValue()) ids.add(entry.id());
                return DataLibraryDragPayload.entries(first.id(), first.type(), first.key(), ids);
            });
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(px(8 + depth * 14), 0, px(5), 0);
            header.setBackground(solid(0xFF292929, 2, type.getColor()));
            SvgIconView disclosure = new SvgIconView(getContext(),
                    SvgIconView.Icon.forExpandedState(expanded), type.getColor());
            disclosure.setOnClickListener(v -> { String marker = "!" + groupKey; if (!expandedTypeGroups.remove(marker)) expandedTypeGroups.add(marker); rebuild(); });
            header.addView(disclosure, fixed(18, 26));
            TextView title = label(getContext(), displayTypeName(type), 10.5f, type.getColor());
            title.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(title, new LayoutParams(0, px(26), 1));
            UiCheckBox check = new UiCheckBox(getContext());
            check.setContentDescription(tr("geometry_node.data_library.all"));
            check.setChecked(group.getValue().stream().allMatch(entry -> selected.contains(key(entry))));
            check.setOnCheckedChangeListener((button, checked) -> {
                for (DataLibraryUiRepository.Entry entry : group.getValue()) {
                    if (checked) selected.add(key(entry)); else selected.remove(key(entry));
                }
                updateSelectionViews();
            });
            selectionViews.add(() -> check.setChecked(group.getValue().stream()
                    .allMatch(entry -> selected.contains(key(entry)))));
            header.addView(check, fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP));
            header.setOnClickListener(v -> { String marker = "!" + groupKey; if (!expandedTypeGroups.remove(marker)) expandedTypeGroups.add(marker); rebuild(); });
            LayoutParams hp = rowParams(null); hp.leftMargin = px(depth * 14); rightHost.addView(header, hp);
            if (!expanded) continue;
            DataLibraryCardGridLayout grid = new DataLibraryCardGridLayout(getContext(), 164, 6, 0);
            LayoutParams gp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.leftMargin = px(depth * 14); gp.bottomMargin = px(3);
            rightHost.addView(grid, gp);
            for (DataLibraryUiRepository.Entry entry : group.getValue()) {
                renderedEntries.add(entry.id());
                View card = createCard(entry);
                if (card.getParent() instanceof ViewGroup parent) parent.removeView(card);
                grid.addView(card, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private View createFolderRow(DataLibraryUiRepository.Folder folder, int depth, boolean expanded) {
        DataLibraryDragSurface row = dragSurface(() -> dragPayloadForFolder(folder.id()));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(4 + depth * TREE_INDENT_DP), 0, px(4), 0);
        boolean active = Objects.equals(activeFolder, folder.id());
        boolean selectedFolder = selectedFolders.contains(folder.id());
        // Tree rows are deliberately flat and borderless at every depth.  Keep the
        // unselected row transparent so the hierarchy reads as one continuous tree;
        // only the active/checked state gets a quiet fill.
        int rowFill = active ? 0xFF344454 : (selectedFolder ? 0xFF303A42 : 0x00000000);
        row.setBackground(solid(rowFill, 0f, 0x00000000));
        row.setOnClickListener(v -> { activeFolder = folder.id(); rebuild(); });
        SvgIconView disclosure = new SvgIconView(getContext(), SvgIconView.Icon.forExpandedState(expanded), TEXT);
        disclosure.setClickable(true);
        disclosure.setOnClickListener(v -> {
            if (!expandedFolders.remove(folder.id())) expandedFolders.add(folder.id());
            rebuild();
        });
        row.addView(disclosure, fixed(18, 28));

        TextView name = label(getContext(), folder.name(), EDITOR_TEXT_SIZE_DP, TEXT);
        name.setSingleLine(true);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.contextMenu = event -> showRenameMenu(event, name, updatedName -> {
            DataLibraryUiRepository.Folder latest = repository.folders().stream()
                    .filter(candidate -> candidate.id().equals(folder.id())).findFirst().orElse(null);
            if (latest != null && !Objects.equals(latest.name(), updatedName)) {
                repository.updateFolder(latest, new DataLibraryUiRepository.Folder(
                        latest.id(), latest.parentId(), updatedName));
            }
        });
        LayoutParams nameLp = new LayoutParams(0, px(28), 1);
        nameLp.leftMargin = px(5);
        row.addView(name, nameLp);

        UiCheckBox check = new UiCheckBox(getContext());
        check.setChecked(selectedFolders.contains(folder.id()));
        check.setOnCheckedChangeListener((button, checked) -> {
            activeFolder = folder.id();
            selectFolderTree(folder.id(), checked);
            rebuild();
        });
        LayoutParams checkLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        checkLp.leftMargin = px(4);
        row.addView(check, checkLp);
        selectionViews.add(() -> {
            check.setChecked(selectedFolders.contains(folder.id()));
            int fill = Objects.equals(activeFolder, folder.id()) ? 0xFF344454
                    : selectedFolders.contains(folder.id()) ? 0xFF303A42 : 0x00000000;
            row.setBackground(solid(fill, 0f, 0x00000000));
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
        row.setPadding(px(4), 0, px(4), 0);
        // Root follows the same flat tree treatment as child folders.
        row.setBackground(solid(activeFolder == null ? 0xFF344454 : 0x00000000, 0f, 0x00000000));
        row.setOnClickListener(v -> { clearSelection(); activeFolder = null; rebuild(); });
        SvgIconView disclosure = new SvgIconView(getContext(),
                SvgIconView.Icon.forExpandedState(rootExpanded), TEXT);
        disclosure.setContentDescription(tr(rootExpanded
                ? "geometry_node.data_library.collapse_entry" : "geometry_node.data_library.expand_entry"));
        disclosure.setOnClickListener(v -> {
            rootExpanded = !rootExpanded;
            rebuild();
        });
        row.addView(disclosure, fixed(18, 28));
        UiCheckBox check = new UiCheckBox(getContext());
        check.setContentDescription(tr("geometry_node.data_library.all"));
        boolean allSelected = true;
        boolean hasSelectable = false;
        for (DataLibraryUiRepository.Entry entry : snapshot.entries.values()) {
            if (entry == null) continue;
            hasSelectable = true;
            if (!selected.contains(key(entry))) { allSelected = false; break; }
        }
        if (allSelected) {
            for (DataLibraryUiRepository.Folder folder : snapshot.folders.values()) {
                if (folder == null) continue;
                hasSelectable = true;
                if (!selectedFolders.contains(folder.id())) { allSelected = false; break; }
            }
        }
        check.setChecked(hasSelectable && allSelected);
        selectionViews.add(() -> check.setChecked(snapshot != null
                && (!snapshot.entries.isEmpty() || !snapshot.folders.isEmpty())
                && snapshot.entries.values().stream().allMatch(entry -> selected.contains(key(entry)))
                && selectedFolders.containsAll(snapshot.folders.keySet())));
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

    private static LayoutParams rowParams(PortType type) {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                type == null ? px(28) : ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(2);
        return params;
    }

    private boolean matches(DataLibraryUiRepository.Entry entry) {
        if (typeFilter != null && entry.type() != typeFilter) return false;
        return search.isEmpty() || entry.key().toLowerCase(Locale.ROOT).contains(search)
                || entry.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(search))
                || entry.type().name().toLowerCase(Locale.ROOT).contains(search)
                || displayTypeName(entry.type()).toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean isFiltering() {
        return typeFilter != null || !search.isEmpty();
    }

    private EditText styledNameEditor(String value) {
        EditText editor = new EditText(getContext());
        editor.setSingleLine(true);
        editor.setText(value == null ? "" : value);
        editor.setTextColor(TEXT);
        editor.setBackground(null);
        editor.setPadding(px(2), 0, px(2), 0);
        UIUtils.setLockedTextSize(editor, EDITOR_TEXT_SIZE_DP);
        return editor;
    }

    private void showTypeMenu(View anchor, Consumer<PortType> consumer,
                              boolean includeAll) {
        List<String> options = new ArrayList<>();
        if (includeAll) options.add("*");
        SUPPORTED_TYPES.stream().sorted(Comparator.comparing(PortType::name))
                .map(Enum::name).forEach(options::add);
        Map<String, String> labels = new HashMap<>();
        for (PortType type : SUPPORTED_TYPES) labels.put(type.name(), displayTypeName(type));
        if (includeAll) labels.put("*", tr("geometry_node.data_library.all_types"));
        SelectHintRenderer.DropdownSearchMenu menu = new SelectHintRenderer.DropdownSearchMenu(
                getContext(), tr("geometry_node.data_library.choose_type"), options, labels, value -> {
            if (consumer == null) return;
            consumer.accept("*".equals(value) ? null : PortType.valueOf(value));
        });
        menu.showAt(anchor, overlayHost());
    }

    private View createCard(DataLibraryUiRepository.Entry entry) {
        CardBinding cached = cards.get(entry.id());
        if (cached != null) {
            refreshCard(cached, entry);
            return cached.card;
        }
        AtomicReference<DataLibraryUiRepository.Entry> current = new AtomicReference<>(entry);
        DataLibraryDragSurface card = dragSurface(() -> dragPayloadForEntry(current.get()));
        card.setOnClickListener(v -> selectEntry(entry.id(), card.shiftPressed));
        DataLibraryEntryCard presentation = new DataLibraryEntryCard(card, entry.key(), entry.type().getColor(),
                selected.contains(key(entry)), () -> toggleEntry(entry.id()), checked -> {
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            selection.anchor = entry.id();
            updateSelectionViews();
        });
        TextView name = presentation.nameView();
        card.contextMenu = event -> showRenameMenu(event, name, updatedName -> {
            DataLibraryUiRepository.Entry latest = repository.findEntry(entry.id());
            if (latest != null && !Objects.equals(latest.key(), updatedName)) {
                repository.update(latest, new DataLibraryUiRepository.Entry(
                        latest.id(), latest.parentId(), latest.type(), updatedName, latest.value(), latest.tags()));
            }
        });
        CardBinding binding = new CardBinding(presentation, current, entry);
        binding.authoritativeRevision = authoritativeRevision;
        cards.put(entry.id(), binding);
        refreshCard(binding, entry);
        return card;
    }

    private static final class CardBinding {
        final DataLibraryDragSurface card;
        final DataLibraryEntryCard presentation;
        final AtomicReference<DataLibraryUiRepository.Entry> current;
        DataLibraryUiRepository.Entry displayed;
        long authoritativeRevision;

        CardBinding(DataLibraryEntryCard presentation, AtomicReference<DataLibraryUiRepository.Entry> current,
                    DataLibraryUiRepository.Entry displayed) {
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
        DataLibraryUiRepository.Entry latest = repository.findEntry(entryId);
        observeAuthority();
        if (binding != null && latest != null && latest.type() == binding.current.get().type()) {
            refreshCard(binding, latest);
        } else {
            scheduleRefresh();
        }
    }

    private void showRenameMenu(MotionEvent event, TextView name, Consumer<String> rename) {
        ViewGroup host = overlayHost();
        int[] location = new int[2];
        host.getLocationOnScreen(location);
        FileContextMenu menu = new FileContextMenu(getContext());
        menu.addMenuItem(tr("geometry_node.asset_library.action.rename"),
                () -> post(() -> beginRename(name, rename)));
        menu.showAt(event.getRawX() - location[0], event.getRawY() - location[1], host);
    }

    private void beginRename(TextView name, Consumer<String> rename) {
        if (!(name.getParent() instanceof ViewGroup parent)) return;
        EditText input = styledNameEditor(name.getText().toString());
        input.setBackground(solid(INPUT_BG, 2, BORDER));
        int index = parent.indexOfChild(name);
        ViewGroup.LayoutParams params = name.getLayoutParams();
        name.setVisibility(View.GONE);
        parent.addView(input, index, params);
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<Boolean> finish = commit -> {
            if (!finished.compareAndSet(false, true)) return;
            String updatedName = input.getText().toString().trim();
            parent.removeView(input);
            name.setVisibility(View.VISIBLE);
            if (commit && !updatedName.isEmpty() && !disposed) rename.accept(updatedName);
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

    private DataLibraryDragPayload dragPayloadForEntry(DataLibraryUiRepository.Entry entry) {
        Set<UUID> ids = selected.stream().map(DataLibraryUiRepository.EntryKey::id)
                .filter(renderedEntries::contains).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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

    private void selectEntry(UUID id, boolean range) {
        selection.selectEntry(id, range);
        updateSelectionViews();
    }

    private void finishInternalDrag() {
        internalDragActive = false;
        groupsHost.hideIndicator();
        if (!rebuildPending) return;
        rebuildPending = false;
        scheduleRefresh();
    }

    private void updateDropIndicator(float rawX, float rawY) {
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
        if (!(session.payload() instanceof DataLibraryDragPayload payload)) return false;
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
        Map<UUID, DataLibraryUiRepository.Folder> folders = snapshot.folders;
        int lastBottom = rootDropTarget.getBottom();
        for (Map.Entry<View, UUID> candidate : folderDropRows.entrySet()) {
            View row = candidate.getKey();
            lastBottom = Math.max(lastBottom, row.getBottom());
            if (y > row.getBottom()) continue;
            DataLibraryUiRepository.Folder folder = folders.get(candidate.getValue());
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
                DataLibraryUiRepository.Folder ancestor = folders.get(folder.parentId());
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
        return DataLibraryDropPolicy.canMove(new DataLibrarySnapshot(repository), payload, targetParent);
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
        KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
        KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
        if (copy != null && copy.matches(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                clipboardEntry = selected.size() == 1
                        ? repository.findEntry(selected.iterator().next().id()) : null;
            }
            return true;
        }
        if (paste != null && paste.matches(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && clipboardEntry != null && selected.size() == 1) {
                DataLibraryUiRepository.Entry target = repository.findEntry(selected.iterator().next().id());
                if (target != null && target.type() == clipboardEntry.type()) valueEditors.updateValue(
                        new AtomicReference<>(target), clipboardEntry.value());
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateActions() {
        deleteButton.setEnabled(!selected.isEmpty() || !selectedFolders.isEmpty());
        addFolderButton.tooltip(tr(activeFolder == null
                ? "geometry_node.data_library.add_folder" : "geometry_node.data_library.add_subfolder"));
    }

    private static DataLibraryUiRepository.EntryKey key(DataLibraryUiRepository.Entry entry) {
        return new DataLibraryUiRepository.EntryKey(entry.id());
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String displayTypeName(PortType type) {
        if (type == null) return "";
        String translated = tr("geometry_node.port." + type.name().toLowerCase(Locale.ROOT));
        return translated.startsWith("geometry_node.") ? type.name() : translated;
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

    @Override public View getView() { return this; }
    @Override public void onShow() {
        if (disposed) return;
        suspended = false;
        if (!dropTargetRegistered) {
            WorkspaceDragDropRegistry.register(dropTarget);
            dropTargetRegistered = true;
        }
        refresh();
    }

    @Override public void onDispose() {
        if (disposed) return;
        disposed = true;
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
    @Override public void onHide() {
        suspended = true;
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
