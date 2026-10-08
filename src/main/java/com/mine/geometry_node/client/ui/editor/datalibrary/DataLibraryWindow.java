package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import com.mine.geometry_node.client.ui.components.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.editor.asset.dialog.FilePickerDialog;
import com.mine.geometry_node.client.ui.editor.asset.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers.SelectHintRenderer;
import com.mine.geometry_node.client.ui.editor.graph.picker.EntityTemplatePickerController;
import com.mine.geometry_node.client.ui.editor.graph.picker.VanillaInventoryPicker;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragGesture;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragDropRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragOperation;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragState;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryValueCodec;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryTypes;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.method.DigitsInputFilter;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
    private static final int TOOLBAR_BG = 0xFF272727;
    private static final int HEADER_BG = 0xFF303030;
    private static final int ROW_BG = 0xFF292929;
    private static final int ROW_SELECTED_BG = 0xFF303840;
    private static final int INPUT_BG = 0xFF202020;
    private static final int BORDER = 0xFF444444;
    private static final int DIVIDER = 0xFF383838;
    private static final int TEXT = 0xFFD2D2D2;
    private static final int MUTED = 0xFF969696;
    private static final int ACCENT = 0xFF5C8CCB;
    private static final int DROP_INDICATOR_COLOR = 0xFF00AAFF;
    private static final int TREE_INDENT_DP = 20;
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final int PREVIEW_SIZE_DP = 44;
    private static final int CARD_PREVIEW_SIZE_DP = 64;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;
    private static final EnumSet<PortType> SUPPORTED_TYPES =
            EnumSet.copyOf(DataLibraryTypes.supported());

    private final DataLibraryUiRepository repository;
    private final DataLibraryTreeLayout groupsHost;
    private final DataLibraryTreeLayout rightHost;
    private final ScrollView scrollHost;
    private final TextView breadcrumbView;
    private final TextView statusView;
    private final UiSearchInput searchInput;
    private final UiActionButton typeButton;
    private final UiIconButton addFolderButton;
    private final View deleteButton;
    private final Set<DataLibraryUiRepository.EntryKey> selected = new LinkedHashSet<>();
    private final Set<UUID> selectedFolders = new LinkedHashSet<>();
    private final Set<UUID> expandedFolders = new HashSet<>();
    private final Set<UUID> expandedEntries = new HashSet<>();
    /** Expanded state for the type blocks shown inside each folder. */
    private final Set<String> expandedTypeGroups = new HashSet<>();
    private final Set<UUID> renderedEntries = new LinkedHashSet<>();
    /** Last clicked entry used as the anchor for shift range selection. */
    private UUID selectionAnchor;
    private UUID activeFolder;
    private final Map<View, UUID> folderDropRows = new java.util.LinkedHashMap<>();
    private final WorkspaceDragDropRegistry.DropTarget dropTarget = this::acceptInternalDrop;
    private final Runnable repositoryChanged = () -> post(this::requestRebuild);
    private View rootDropTarget;
    private boolean dropTargetRegistered;
    private String search = "";
    private PortType typeFilter;
    private boolean rootExpanded = true;
    private boolean internalDragActive;
    private boolean rebuildPending;
    private static UUID entityReferenceClipboard;
    private static boolean hasEntityReferenceClipboard;
    private static DataLibraryUiRepository.Entry clipboardEntry;

    public DataLibraryWindow(Context context) {
        this(context, DataLibraryUiRepository.EMPTY);
    }

    public DataLibraryWindow(Context context, DataLibraryUiRepository repository) {
        super(context);
        this.repository = repository != null ? repository : DataLibraryUiRepository.EMPTY;
        this.repository.addChangeListener(repositoryChanged);
        setOrientation(VERTICAL);
        setBackground(solid(BG, 0));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(px(8), px(4), px(8), px(4));
        toolbar.setBackground(solid(TOOLBAR_BG, 0, DIVIDER));
        addView(toolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(76)));

        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(actionsRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(32)));

        addFolderButton = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SQUARE_PLUS, TEXT),
                tr("geometry_node.data_library.add_folder"));
        addFolderButton.setOnClickListener(v -> {
            prepareCreation();
            repository.createFolder(activeFolder);
        });
        actionsRow.addView(addFolderButton, fixed(30, 32));

        UiIconButton addButton = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.PLUS, TEXT),
                tr("geometry_node.data_library.add"));
        addButton.setOnClickListener(v -> showTypeMenu(addButton, type -> {
            prepareCreation();
            String groupKey = (activeFolder == null ? "root" : activeFolder.toString()) + ":" + type.name();
            expandedTypeGroups.remove("!" + groupKey);
            repository.create(activeFolder, type);
        }, false));
        LayoutParams addLp = fixed(30, 32);
        addLp.leftMargin = px(4);
        actionsRow.addView(addButton, addLp);

        deleteButton = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CLEAR, TEXT),
                tr("geometry_node.common.delete"));
        deleteButton.setOnClickListener(v -> {
            if (selected.isEmpty() && selectedFolders.isEmpty()) return;
            Set<DataLibraryUiRepository.EntryKey> entries = Set.copyOf(selected);
            Set<UUID> folders = Set.copyOf(selectedFolders);
            clearSelection();
            repository.delete(entries, folders);
        });
        LayoutParams deleteLp = fixed(30, 32);
        deleteLp.leftMargin = px(4);
        actionsRow.addView(deleteButton, deleteLp);
        actionsRow.addView(new View(context), new LayoutParams(0, 1, 1));

        typeButton = button(context, tr("geometry_node.data_library.all_types"),
                UiActionButton.Role.INLINE);
        typeButton.setOnClickListener(v -> showTypeMenu(typeButton, selectedType -> {
            typeFilter = selectedType;
            typeButton.setText(selectedType == null ? tr("geometry_node.data_library.all_types") : displayTypeName(selectedType));
            rebuild();
        }, true));
        LayoutParams typeLp = fixed(92, 32);
        typeLp.leftMargin = px(8);
        actionsRow.addView(typeButton, typeLp);

        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams searchRowLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(32));
        searchRowLp.topMargin = px(4);
        toolbar.addView(searchRow, searchRowLp);
        searchInput = new UiSearchInput(context);
        searchInput.setHint(tr("geometry_node.data_library.search"));
        // The input is a draft; only the search button commits it to the filter.
        searchRow.addView(searchInput, new LayoutParams(0, px(32), 1));

        UiIconButton searchButton = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SEARCH, TEXT),
                tr("geometry_node.asset_library.toolbar.search"));
        searchButton.setOnClickListener(v -> applySearch());
        LayoutParams searchButtonLp = fixed(30, 32);
        searchButtonLp.leftMargin = px(4);
        searchRow.addView(searchButton, searchButtonLp);

        UiIconButton clearSearchButton = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CLOSE, TEXT),
                tr("geometry_node.data_library.clear_search"));
        clearSearchButton.setOnClickListener(v -> clearSearch());
        LayoutParams clearSearchLp = fixed(30, 32);
        clearSearchLp.leftMargin = px(4);
        searchRow.addView(clearSearchButton, clearSearchLp);

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
        ScrollView rightScroll = new ScrollView(context);
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
        repository.refresh(() -> post(this::requestRebuild));
    }

    private void requestRebuild() {
        if (internalDragActive) {
            rebuildPending = true;
            return;
        }
        rebuild();
    }

    private void rebuild() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        Set<DataLibraryUiRepository.EntryKey> available = new LinkedHashSet<>();
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry != null && entry.type() != null && entry.id() != null) available.add(key(entry));
        }
        selected.retainAll(available);
        Set<UUID> availableFolders = new HashSet<>();
        for (DataLibraryUiRepository.Folder folder : repository.folders()) {
            if (folder != null && folder.id() != null) availableFolders.add(folder.id());
        }
        selectedFolders.retainAll(availableFolders);
        if (activeFolder != null && !availableFolders.contains(activeFolder)) activeFolder = null;
        expandedFolders.retainAll(availableFolders);
        expandedEntries.retainAll(available.stream().map(DataLibraryUiRepository.EntryKey::id)
                .collect(java.util.stream.Collectors.toSet()));
        groupsHost.removeAllViews();
        groupsHost.hideIndicator();
        rightHost.removeAllViews();
        rightHost.hideIndicator();
        folderDropRows.clear();
        renderedEntries.clear();
        rootDropTarget = createRootRow();
        groupsHost.addView(rootDropTarget, rowParams(null));

        Map<UUID, DataLibraryUiRepository.Folder> foldersById = new HashMap<>();
        Map<UUID, List<DataLibraryUiRepository.Folder>> childFolders = new HashMap<>();
        for (DataLibraryUiRepository.Folder folder : repository.folders()) {
            if (folder == null || folder.id() == null) continue;
            foldersById.put(folder.id(), folder);
            childFolders.computeIfAbsent(folder.parentId(), ignored -> new ArrayList<>()).add(folder);
        }
        childFolders.values().forEach(children -> children.sort(
                Comparator.comparing(DataLibraryUiRepository.Folder::name, String.CASE_INSENSITIVE_ORDER)));

        Map<UUID, List<DataLibraryUiRepository.Entry>> childEntries = new HashMap<>();
        Set<UUID> matchedEntries = new HashSet<>();
        Set<UUID> visibleFolders = new HashSet<>();
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null || entry.type() == null || !SUPPORTED_TYPES.contains(entry.type())) continue;
            childEntries.computeIfAbsent(entry.parentId(), ignored -> new ArrayList<>()).add(entry);
            if (matches(entry)) {
                matchedEntries.add(entry.id());
                collectAncestors(entry.parentId(), foldersById, visibleFolders);
            }
        }
        childEntries.values().forEach(entries -> entries.sort(
                Comparator.comparing(DataLibraryUiRepository.Entry::key, String.CASE_INSENSITIVE_ORDER)));
        if (!search.isEmpty()) {
            for (DataLibraryUiRepository.Folder folder : repository.folders()) {
                if (folder != null && folder.name().toLowerCase(Locale.ROOT).contains(search)) {
                    visibleFolders.add(folder.id());
                    collectAncestors(folder.parentId(), foldersById, visibleFolders);
                }
            }
        }
        if (rootExpanded) {
            addTreeLevel(null, 1, childFolders, childEntries, matchedEntries, visibleFolders, new HashSet<>());
        }
        addEntriesGrouped(activeFolder, 0, childEntries, matchedEntries);
        updateNavigationLabels();
        if (rightHost.getChildCount() == 0) {
            TextView empty = label(getContext(), tr(!isFiltering()
                    ? "geometry_node.data_library.empty" : "geometry_node.data_library.no_results"), 12, MUTED);
            empty.setGravity(Gravity.CENTER);
            rightHost.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(90)));
        }
        updateActions();
    }

    private void updateNavigationLabels() {
        // Render the complete folder path so the right pane mirrors the asset
        // browser breadcrumb instead of showing only the immediate parent.
        List<String> path = new ArrayList<>();
        Map<UUID, DataLibraryUiRepository.Folder> folders = new HashMap<>();
        for (DataLibraryUiRepository.Folder folder : repository.folders()) {
            if (folder != null && folder.id() != null) folders.put(folder.id(), folder);
        }
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
            if (isFiltering() && !matchedEntries.contains(entry.id())) continue;
            byType.computeIfAbsent(entry.type(), ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<PortType, List<DataLibraryUiRepository.Entry>> group : byType.entrySet()) {
            PortType type = group.getKey();
            String groupKey = (parentId == null ? "root" : parentId.toString()) + ":" + type.name();
            boolean expanded = !expandedTypeGroups.contains("!" + groupKey);
            DataLibraryDragSurface header = new DataLibraryDragSurface(() -> {
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
                rebuild();
            });
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
                grid.addView(createCard(entry), new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private View createFolderRow(DataLibraryUiRepository.Folder folder, int depth, boolean expanded) {
        DataLibraryDragSurface row = new DataLibraryDragSurface(() -> dragPayloadForFolder(folder.id()));
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
        folderDropRows.put(row, folder.id());
        return row;
    }

    /** Selects a folder and all descendants, including their data entries. */
    private void selectFolderTree(UUID root, boolean checked) {
        if (root == null) return;
        Map<UUID, Boolean> descendants = new HashMap<>();
        descendants.put(root, true);
        boolean changed;
        do {
            changed = false;
            for (DataLibraryUiRepository.Folder folder : repository.folders()) {
                if (folder != null && folder.parentId() != null && descendants.containsKey(folder.parentId())
                        && !descendants.containsKey(folder.id())) {
                    descendants.put(folder.id(), true);
                    changed = true;
                }
            }
        } while (changed);
        for (UUID id : descendants.keySet()) {
            if (checked) selectedFolders.add(id); else selectedFolders.remove(id);
        }
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null || entry.parentId() == null || !descendants.containsKey(entry.parentId())) continue;
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
        }
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
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null) continue;
            hasSelectable = true;
            if (!selected.contains(key(entry))) { allSelected = false; break; }
        }
        if (allSelected) {
            for (DataLibraryUiRepository.Folder folder : repository.folders()) {
                if (folder == null) continue;
                hasSelectable = true;
                if (!selectedFolders.contains(folder.id())) { allSelected = false; break; }
            }
        }
        check.setChecked(hasSelectable && allSelected);
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

    private static boolean isInFolderTree(UUID folderId, UUID treeRoot,
                                          Map<UUID, DataLibraryUiRepository.Folder> folders) {
        if (treeRoot == null) return false;
        Set<UUID> visited = new HashSet<>();
        UUID current = folderId;
        while (current != null && visited.add(current)) {
            if (current.equals(treeRoot)) return true;
            DataLibraryUiRepository.Folder folder = folders.get(current);
            current = folder != null ? folder.parentId() : null;
        }
        return false;
    }

    private View createCard(DataLibraryUiRepository.Entry entry) {
        AtomicReference<DataLibraryUiRepository.Entry> current = new AtomicReference<>(entry);
        DataLibraryDragSurface card = new DataLibraryDragSurface(() -> dragPayloadForEntry(current.get()));
        card.setOrientation(VERTICAL);
        card.setOnClickListener(v -> selectEntry(entry.id(), card.shiftPressed));
        boolean initiallySelected = selected.contains(key(entry));
        card.setBackground(solid(initiallySelected ? ROW_SELECTED_BG : ROW_BG, 3,
                initiallySelected ? entry.type().getColor() : BORDER));
        card.setPadding(px(2), px(2), px(2), px(2));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(solid(withAlpha(entry.type().getColor(), 0x28), 2, entry.type().getColor()));
        boolean expanded = expandedEntries.contains(entry.id());
        SvgIconView disclosure = new SvgIconView(getContext(), SvgIconView.Icon.forExpandedState(expanded), TEXT);
        disclosure.setClickable(true);
        disclosure.setContentDescription(tr(expanded
                ? "geometry_node.data_library.collapse_entry"
                : "geometry_node.data_library.expand_entry"));
        disclosure.setOnClickListener(v -> toggleEntry(entry.id()));
        header.addView(disclosure, fixed(18, 32));

        TextView name = label(getContext(), entry.key(), EDITOR_TEXT_SIZE_DP, entry.type().getColor());
        name.setSingleLine(true);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        card.contextMenu = event -> showRenameMenu(event, name, updatedName -> {
            DataLibraryUiRepository.Entry latest = repository.findEntry(entry.id());
            if (latest != null && !Objects.equals(latest.key(), updatedName)) {
                repository.update(latest, new DataLibraryUiRepository.Entry(
                        latest.id(), latest.parentId(), latest.type(), updatedName, latest.value(), latest.tags()));
            }
        });
        header.addView(name, new LayoutParams(0, px(32), 1));

        UiCheckBox check = new UiCheckBox(getContext());
        check.setChecked(selected.contains(key(entry)));
        check.setContentDescription(tr("geometry_node.data_library.select_entry"));
        check.setTooltipText(tr("geometry_node.data_library.select_entry"));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            selectionAnchor = entry.id();
            rebuild();
        });
        LayoutParams checkLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        checkLp.leftMargin = px(5);
        checkLp.rightMargin = px(5);
        header.addView(check, checkLp);
        card.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(32)));
        if (expanded) {
            View divider = new View(getContext());
            divider.setBackground(solid(DIVIDER, 0));
            LayoutParams dividerLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1));
            card.addView(divider, dividerLp);
            card.addView(createExpandedEditor(entry, current), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    private UUID selectedFolderParent() {
        return activeFolder;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void toggleEntry(UUID entryId) {
        View focused = findFocus();
        if (focused != null) focused.clearFocus();
        if (!expandedEntries.remove(entryId)) expandedEntries.add(entryId);
        rebuild();
    }

    private TagFlowLayout createCompactTags(List<String> values) {
        TagFlowLayout flow = new TagFlowLayout(getContext(), 3, 0);
        int visible = Math.min(values.size(), 2);
        for (int index = 0; index < visible; index++) flow.addView(tagChip(values.get(index), false));
        if (values.size() > visible) flow.addView(tagChip("+" + (values.size() - visible), false));
        return flow;
    }

    private TextView tagChip(String value, boolean removable) {
        TextView chip = label(getContext(), removable ? value + "  x" : value, 9.0f, 0xFFC5CEDA);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(px(5), 0, px(5), 0);
        chip.setBackground(solid(0xFF343E49, 2, 0xFF4B5B6C));
        int width = Math.min(px(92), Math.max(px(24), px(11 + chip.getText().length() * 6)));
        chip.setLayoutParams(new ViewGroup.LayoutParams(width, px(18)));
        return chip;
    }

    private View createExpandedEditor(DataLibraryUiRepository.Entry entry,
                                      AtomicReference<DataLibraryUiRepository.Entry> current) {
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(0, px(5), px(6), px(6));

        View valueEditor = createExpandedValueEditor(entry, current);
        LayoutParams valueLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                px(expandedValueHeightDp(entry.type())));
        content.addView(valueEditor, valueLp);
        return content;
    }

    private View createExpandedValueEditor(DataLibraryUiRepository.Entry entry,
                                           AtomicReference<DataLibraryUiRepository.Entry> current) {
        if (entry.type() == PortType.ENTITY || entry.type() == PortType.ENTITY_TEMPLATE) {
            LinearLayout content = new LinearLayout(getContext());
            content.setGravity(Gravity.CENTER_VERTICAL);
            EntityTemplatePreviewView preview = createEntityPreview(entry, current);
            if (entry.value() instanceof EntityTemplateValue template) preview.setDisplayTemplate(template);
            else if (entry.value() instanceof UUID entityId) {
                Entity entity = resolveClientEntity(entityId);
                if (entity != null) preview.setDisplayTemplate(EntityTemplateValue.capture(entity));
            }
            content.addView(centeredPreview(preview), fixed(CARD_PREVIEW_SIZE_DP, CARD_PREVIEW_SIZE_DP));
            UiActionButton edit = button(getContext(), tr(entry.type() == PortType.ENTITY
                            ? "geometry_node.data_library.choose_entity" : "geometry_node.ui.edit_template"),
                    UiActionButton.Role.INLINE);
            edit.setContentDescription(tr("geometry_node.data_library.edit_value"));
            edit.setOnClickListener(v -> editEntityValue(entry, current));
            LayoutParams editLp = new LayoutParams(0, px(26), 1);
            editLp.leftMargin = px(6);
            content.addView(edit, editLp);
            return content;
        } else if (entry.type() == PortType.ITEM_STACK) {
            LinearLayout content = new LinearLayout(getContext());
            content.setGravity(Gravity.CENTER_VERTICAL);
            ItemSlotView preview = new ItemSlotView(getContext());
            preview.setDisplayStack(entry.value() instanceof ItemStack stack ? stack : ItemStack.EMPTY);
            preview.setOpenEditorOnClick(false);
            preview.setDisplayPasteAction(json -> pasteItemStack(current, json));
            content.addView(centeredPreview(preview), fixed(CARD_PREVIEW_SIZE_DP, CARD_PREVIEW_SIZE_DP));
            UiActionButton edit = button(getContext(), tr("geometry_node.node.pick_item_stack"),
                    UiActionButton.Role.INLINE);
            edit.setContentDescription(tr("geometry_node.data_library.edit_value"));
            edit.setOnClickListener(v -> editItemStackValue(current));
            LayoutParams editLp = new LayoutParams(0, px(26), 1);
            editLp.leftMargin = px(6);
            content.addView(edit, editLp);
            return content;
        } else if (entry.type() == PortType.XYZ) {
            return createXyzValueEditor(current);
        } else if (entry.type() == PortType.AREA) {
            TextView summary = label(getContext(), areaSummary(entry.value()), 10.5f, TEXT);
            summary.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            return summary;
        } else if (isSummaryType(entry.type())) {
            TextView summary = label(getContext(), valueSummary(entry), 10.5f, TEXT);
            summary.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            return summary;
        } else if (isInlineValueType(entry.type())) {
            return switch (entry.type()) {
                case BOOLEAN -> createBooleanValueEditor(entry, current);
                case STRING -> createPlainTextValueEditor(current);
                case PATH -> createPathValueEditor(current);
                case RICH_TEXT -> createRichTextValueEditor(current);
                case ITEM, BLOCK_STATE -> createRegistryValueEditor(entry.type(), current);
                default -> createValueEditor(entry, current);
            };
        }
        return createValueEditor(entry, current);
    }

    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String tag = part.trim();
            if (!tag.isEmpty()) tags.add(tag);
        }
        return List.copyOf(tags);
    }

    private static int expandedValueHeightDp(PortType type) {
        if (type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE || type == PortType.ITEM_STACK) return 70;
        if (type == PortType.XYZ) return 56;
        if (isInlineValueType(type) || isSummaryType(type) || type == PortType.AREA) return 28;
        return 54;
    }

    private String compactValueSummary(DataLibraryUiRepository.Entry entry) {
        if (entry.type() == PortType.AREA) return areaSummary(entry.value());
        if (isSummaryType(entry.type())) return valueSummary(entry);
        if (entry.type() == PortType.ITEM_STACK && entry.value() instanceof ItemStack stack) {
            return stack.isEmpty() ? "" : stack.getHoverName().getString() + " x" + stack.getCount();
        }
        if (entry.type() == PortType.ENTITY || entry.type() == PortType.ENTITY_TEMPLATE) {
            return entry.value() == null ? tr("geometry_node.data_library.reference_unavailable")
                    : tr("geometry_node.data_library.preview_available");
        }
        String value = String.valueOf(entry.value());
        return value.length() <= 42 ? value : value.substring(0, 39) + "...";
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
            if (commit && !updatedName.isEmpty()) rename.accept(updatedName);
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

    /** Observe the entire surface, cancelling child clicks only after the drag threshold. */
    private final class DataLibraryDragSurface extends LinearLayout {
        private final WorkspaceDragGesture gesture;
        private Consumer<MotionEvent> contextMenu;
        private boolean pressed;
        private boolean dragging;
        private boolean secondaryPressed;
        private boolean shiftPressed;

        private DataLibraryDragSurface(Supplier<DataLibraryDragPayload> payload) {
            super(DataLibraryWindow.this.getContext());
            setClickable(true);
            gesture = new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
                @Override public void onPressed(MotionEvent event) {
                    internalDragActive = true;
                }

                @Override public void onDragStarted(MotionEvent event) {
                    dragging = true;
                    MotionEvent cancel = event.copy();
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    try {
                        dispatchToChildren(cancel);
                    } finally {
                        cancel.recycle();
                    }
                    if (getParent() instanceof ViewGroup parent) parent.requestDisallowInterceptTouchEvent(true);
                    setAlpha(0.6f);
                    WorkspaceDragService.INSTANCE.begin(payload.get(), WorkspaceDragOperation.CONTEXTUAL,
                            DataLibraryWindow.this);
                }

                @Override public void onDragged(MotionEvent event) {
                    updateDropIndicator(event.getRawX(), event.getRawY());
                }

                @Override public void onReleased(MotionEvent event, boolean moved) {
                    setAlpha(1.0f);
                    groupsHost.hideIndicator();
                    if (moved && !WorkspaceDragService.INSTANCE.drop(event.getRawX(), event.getRawY())) {
                        WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
                    }
                    finishInternalDrag();
                }

                @Override public void onCancelled(MotionEvent event) {
                    setAlpha(1.0f);
                    groupsHost.hideIndicator();
                    WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
                    finishInternalDrag();
                }
            });
        }

        private boolean dispatchToChildren(MotionEvent event) {
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            shiftPressed = event.isShiftPressed();
            if (action == MotionEvent.ACTION_DOWN) {
                secondaryPressed = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                        || event.getActionButton() == MotionEvent.BUTTON_SECONDARY;
                if (secondaryPressed) return true;
                if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) return false;
                pressed = true;
                dragging = false;
                gesture.onTouch(this, event);
                dispatchToChildren(event);
                return true;
            }
            if (secondaryPressed) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    secondaryPressed = false;
                    if (action == MotionEvent.ACTION_UP && contextMenu != null) contextMenu.accept(event);
                }
                return true;
            }
            if (!pressed) return dispatchToChildren(event);
            gesture.onTouch(this, event);
            if (!dragging) dispatchToChildren(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                dragging = false;
            }
            return true;
        }
    }

    private DataLibraryDragPayload dragPayloadForEntry(DataLibraryUiRepository.Entry entry) {
        Set<UUID> ids = selected.stream().map(DataLibraryUiRepository.EntryKey::id)
                .filter(renderedEntries::contains).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!ids.contains(entry.id())) ids = Set.of(entry.id());
        return ids.size() == 1 ? DataLibraryDragPayload.entry(entry.id(), entry.type(), entry.key())
                : DataLibraryDragPayload.entries(entry.id(), entry.type(), entry.key(), ids);
    }

    private DataLibraryDragPayload dragPayloadForFolder(UUID id) {
        Set<UUID> ids = new LinkedHashSet<>(selectedFolders);
        if (!ids.contains(id)) ids = Set.of(id);
        Map<UUID, DataLibraryUiRepository.Folder> folders = new HashMap<>();
        repository.folders().forEach(folder -> folders.put(folder.id(), folder));
        // A recursively checked ancestor must not replace the folder actually grabbed.
        for (UUID candidate : ids) {
            if (!candidate.equals(id) && isInFolderTree(id, candidate, folders)) {
                return DataLibraryDragPayload.folder(id);
            }
        }
        Set<UUID> roots = new LinkedHashSet<>(ids);
        for (UUID candidate : ids) {
            for (UUID ancestor : ids) {
                if (!candidate.equals(ancestor) && isInFolderTree(candidate, ancestor, folders)) {
                    roots.remove(candidate);
                    break;
                }
            }
        }
        UUID anchor = roots.contains(id) ? id : roots.iterator().next();
        return roots.size() == 1 ? DataLibraryDragPayload.folder(anchor)
                : DataLibraryDragPayload.folders(anchor, roots);
    }

    private void selectEntry(UUID id, boolean range) {
        if (id == null) return;
        List<UUID> ordered = new ArrayList<>(renderedEntries);
        int target = ordered.indexOf(id);
        if (target < 0) return;
        if (!range || selectionAnchor == null || !ordered.contains(selectionAnchor)) {
            selected.clear();
            selected.add(new DataLibraryUiRepository.EntryKey(id));
        } else {
            selected.clear();
            int anchor = ordered.indexOf(selectionAnchor);
            int from = Math.min(anchor, target), to = Math.max(anchor, target);
            for (int i = from; i <= to; i++) selected.add(new DataLibraryUiRepository.EntryKey(ordered.get(i)));
        }
        selectionAnchor = id;
        rebuild();
    }

    private void finishInternalDrag() {
        internalDragActive = false;
        if (!rebuildPending) return;
        rebuildPending = false;
        post(this::requestRebuild);
    }

    private void updateDropIndicator(float rawX, float rawY) {
        WorkspaceDragState.Session session = WorkspaceDragService.INSTANCE.current();
        DataLibraryDragPayload payload = session != null
                && session.payload() instanceof DataLibraryDragPayload dataLibraryPayload
                ? dataLibraryPayload : null;
        DropDestination destination = dropDestinationAt(rawX, rawY);
        if (destination == null || payload == null || !canMove(payload, destination.parentId())) {
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
        Map<UUID, DataLibraryUiRepository.Folder> folders = new HashMap<>();
        repository.folders().forEach(folder -> folders.put(folder.id(), folder));
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
        rootExpanded = true;
        rebuildPending = true;
        if (payload.isEntry()) {
            if (targetParent != null) expandedFolders.add(targetParent);
            for (UUID id : payload.entryIds()) {
                DataLibraryUiRepository.Entry expected = repository.findEntry(id);
                if (expected != null && canMove(DataLibraryDragPayload.entry(expected.id(), expected.type(), expected.key()), targetParent)) {
                    repository.moveEntry(expected.id(), targetParent);
                }
            }
            return true;
        }
        if (targetParent != null) expandedFolders.add(targetParent);
        for (UUID id : payload.folderIds()) {
            DataLibraryUiRepository.Folder expected = repository.folders().stream()
                    .filter(folder -> folder.id().equals(id)).findFirst().orElse(null);
            if (expected != null && canMove(DataLibraryDragPayload.folder(id), targetParent)) {
                repository.moveFolder(expected.id(), targetParent);
            }
        }
        return true;
    }

    private boolean canMove(DataLibraryDragPayload payload, UUID targetParent) {
        // Validate the complete selection before sending any move requests.
        if (payload.isEntry()) {
            return payload.entryIds().stream().allMatch(id -> {
                DataLibraryUiRepository.Entry entry = repository.findEntry(id);
                return entry != null && canMoveSingle(
                        DataLibraryDragPayload.entry(id, entry.type(), entry.key()), targetParent);
            });
        }
        return payload.folderIds().stream().allMatch(id -> canMoveSingle(DataLibraryDragPayload.folder(id), targetParent));
    }

    private boolean canMoveSingle(DataLibraryDragPayload payload, UUID targetParent) {
        if (payload.isEntry()) {
            DataLibraryUiRepository.Entry source = repository.findEntry(payload.id());
            if (source == null || Objects.equals(source.parentId(), targetParent)) return false;
            return repository.entries().stream().noneMatch(entry -> !entry.id().equals(source.id())
                    && Objects.equals(entry.parentId(), targetParent)
                    && entry.type() == source.type()
                    && entry.key().trim().equalsIgnoreCase(source.key().trim()));
        }

        DataLibraryUiRepository.Folder source = repository.folders().stream()
                .filter(folder -> folder.id().equals(payload.id())).findFirst().orElse(null);
        if (source == null || Objects.equals(source.parentId(), targetParent)) return false;
        Map<UUID, DataLibraryUiRepository.Folder> folders = new HashMap<>();
        repository.folders().forEach(folder -> folders.put(folder.id(), folder));
        if (targetParent != null && isInFolderTree(targetParent, source.id(), folders)) return false;
        return repository.folders().stream().noneMatch(folder -> !folder.id().equals(source.id())
                && Objects.equals(folder.parentId(), targetParent)
                && folder.name().trim().equalsIgnoreCase(source.name().trim()));
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

    private EditText createValueEditor(DataLibraryUiRepository.Entry entry,
                                       AtomicReference<DataLibraryUiRepository.Entry> current) {
        EditText valueEditor = new EditText(getContext());
        valueEditor.setText(serializedValue(entry));
        valueEditor.setTextColor(MUTED);
        valueEditor.setSingleLine(true);
        valueEditor.setGravity(Gravity.CENTER_VERTICAL);
        valueEditor.setPadding(px(5), 0, px(5), 0);
        valueEditor.setBackground(solid(INPUT_BG, 1, BORDER));
        UIUtils.setLockedTextSize(valueEditor, EDITOR_TEXT_SIZE_DP);
        valueEditor.setOnFocusChangeListener((view, focused) -> {
            if (!focused) commitValue(current, valueEditor.getText().toString());
        });
        return valueEditor;
    }

    private EditText createPlainTextValueEditor(AtomicReference<DataLibraryUiRepository.Entry> current) {
        EditText editor = styledValueEditor();
        Object value = current.get().value();
        editor.setText(value == null ? "" : String.valueOf(value));
        editor.setOnFocusChangeListener((view, focused) -> {
            if (!focused) updateValue(current, editor.getText().toString());
        });
        return editor;
    }

    private View createPathValueEditor(AtomicReference<DataLibraryUiRepository.Entry> current) {
        String path = current.get().value() instanceof String value ? value : "";
        UiActionButton button = button(getContext(), pathLabel(path), UiActionButton.Role.INLINE);
        button.setContentDescription(path.isBlank() ? tr("geometry_node.data_library.choose_path") : path);
        button.setOnClickListener(v -> FilePickerDialog.showPath(button,
                current.get().value() instanceof String value ? value : "", selectedPath -> {
            updateValue(current, selectedPath);
            button.setText(pathLabel(selectedPath));
            button.setContentDescription(selectedPath);
        }));
        return button;
    }

    private View createRichTextValueEditor(AtomicReference<DataLibraryUiRepository.Entry> current) {
        UiActionButton button = button(getContext(), tr("geometry_node.data_library.edit"),
                UiActionButton.Role.INLINE);
        button.setContentDescription(tr("geometry_node.graph_properties.quest.edit_rich_text"));
        button.setOnClickListener(v -> ExpandedTextInputOverlay.showRichText(
                getContext(), button, RichTextValue.from(current.get().value()),
                value -> updateValue(current, value)));
        return button;
    }

    private View createRegistryValueEditor(PortType type,
                                           AtomicReference<DataLibraryUiRepository.Entry> current) {
        UiActionButton button = button(getContext(), registryValueLabel(type, current.get().value()),
                UiActionButton.Role.INLINE);
        button.setContentDescription(tr(type == PortType.ITEM
                ? "geometry_node.data_library.choose_item"
                : "geometry_node.data_library.choose_block"));
        button.setOnClickListener(v -> showRegistryValueMenu(button, type, current));
        return button;
    }

    private void showRegistryValueMenu(UiActionButton anchor, PortType type,
                                       AtomicReference<DataLibraryUiRepository.Entry> current) {
        ViewGroup host = overlayHost();
        if (host == null) return;
        List<String> options = type == PortType.ITEM
                ? RegistryDataManager.getAllItems()
                : RegistryDataManager.getAllBlocks();
        SelectHintRenderer.DropdownSearchMenu menu = new SelectHintRenderer.DropdownSearchMenu(
                getContext(), tr(type == PortType.ITEM
                        ? "geometry_node.data_library.choose_item"
                        : "geometry_node.data_library.choose_block"),
                options, Map.of(), selectedId -> {
                    Object selectedValue = registryValue(type, selectedId);
                    if (selectedValue == null) return;
                    updateValue(current, selectedValue);
                    anchor.setText(selectedId);
                });
        menu.showAt(anchor, host);
    }

    private static Object registryValue(PortType type, String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;
        if (type == PortType.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        }
        if (type == PortType.BLOCK_STATE) {
            return BuiltInRegistries.BLOCK.getOptional(identifier)
                    .map(block -> block.defaultBlockState())
                    .orElse(null);
        }
        return null;
    }

    private static String registryValueLabel(PortType type, Object value) {
        if (type == PortType.ITEM && value instanceof Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }
        if (type == PortType.BLOCK_STATE && value instanceof BlockState state) {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }
        return "-";
    }

    private static String pathLabel(String path) {
        if (path == null || path.isBlank()) {
            return tr("geometry_node.data_library.choose_path");
        }
        String normalized = path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 && separator + 1 < normalized.length()
                ? normalized.substring(separator + 1)
                : normalized;
    }

    private LinearLayout createXyzValueEditor(AtomicReference<DataLibraryUiRepository.Entry> current) {
        LinearLayout inputs = new LinearLayout(getContext());
        inputs.setOrientation(VERTICAL);
        List<Double> values = xyzComponents(current.get().value());
        String[] labels = {"X", "Y", "Z"};
        for (int index = 0; index < 3; index++) {
            EditText editor = styledValueEditor();
            editor.setHint(labels[index]);
            editor.setHintTextColor(MUTED);
            editor.setFilters(DigitsInputFilter.getInstance(Locale.ROOT, true, true));
            editor.setText(formatNumber(values.get(index)));
            int component = index;
            editor.setOnFocusChangeListener((view, focused) -> {
                if (!focused) commitXyzComponent(current, component, editor.getText().toString());
            });
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            if (index > 0) params.topMargin = px(2);
            inputs.addView(editor, params);
        }
        return inputs;
    }

    private EditText styledValueEditor() {
        EditText editor = new EditText(getContext());
        editor.setSingleLine(true);
        editor.setTextColor(MUTED);
        editor.setGravity(Gravity.CENTER_VERTICAL);
        editor.setPadding(px(5), 0, px(5), 0);
        editor.setBackground(solid(INPUT_BG, 1, BORDER));
        UIUtils.setLockedTextSize(editor, EDITOR_TEXT_SIZE_DP);
        return editor;
    }

    private void commitXyzComponent(AtomicReference<DataLibraryUiRepository.Entry> current,
                                    int component, String text) {
        try {
            float value = Float.parseFloat(text.trim());
            List<Double> values = new ArrayList<>(xyzComponents(current.get().value()));
            values.set(component, (double) value);
            updateValue(current, List.copyOf(values));
        } catch (NumberFormatException ignored) {
            // Keep the previous component when the temporary editor text is incomplete.
        }
    }

    private static List<Double> xyzComponents(Object value) {
        if (value instanceof Vec3 vec) return List.of(vec.x, vec.y, vec.z);
        if (value instanceof List<?> values && values.size() >= 3) {
            return List.of(numberAt(values, 0), numberAt(values, 1), numberAt(values, 2));
        }
        return List.of(0.0, 0.0, 0.0);
    }

    private static double numberAt(List<?> values, int index) {
        Object value = values.get(index);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static String valueSummary(DataLibraryUiRepository.Entry entry) {
        int size = 0;
        String key;
        if (entry.type() == PortType.LIST) {
            if (entry.value() instanceof List<?> list) size = list.size();
            key = "geometry_node.data_library.summary.items";
        } else if (entry.type() == PortType.SHOP) {
            if (entry.value() instanceof Map<?, ?> map && map.get("offers") instanceof List<?> offers) {
                size = offers.size();
            }
            key = "geometry_node.data_library.summary.offers";
        } else {
            if (entry.value() instanceof Map<?, ?> map) size = map.size();
            key = "geometry_node.data_library.summary.entries";
        }
        return Component.translatable(key, size).getString();
    }

    private static String areaSummary(Object value) {
        if (!(value instanceof AreaRef area)) {
            return tr("geometry_node.data_library.reference_unavailable");
        }
        return area.address().dimension().identifier() + " / " + area.address().id();
    }

    private View createBooleanValueEditor(DataLibraryUiRepository.Entry entry,
                                          AtomicReference<DataLibraryUiRepository.Entry> current) {
        LinearLayout host = new LinearLayout(getContext());
        host.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = label(getContext(), tr("geometry_node.port.data_library_value"), EDITOR_TEXT_SIZE_DP, TEXT);
        host.addView(label, new LayoutParams(0, px(28), 1));
        UiCheckBox valueCheckBox = new UiCheckBox(getContext());
        valueCheckBox.setContentDescription(tr("geometry_node.data_library.edit_value"));
        valueCheckBox.setTooltipText(tr("geometry_node.data_library.edit_value"));
        valueCheckBox.setChecked(Boolean.TRUE.equals(entry.value()));
        valueCheckBox.setOnCheckedChangeListener((button, checked) -> {
            DataLibraryUiRepository.Entry latest = current.get();
            DataLibraryUiRepository.Entry updated = DataLibraryValueChanges.replace(latest, checked);
            current.set(updated);
            repository.update(latest, updated);
        });
        host.addView(valueCheckBox, fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP));
        return host;
    }

    private static boolean isInlineValueType(PortType type) {
        return type == PortType.INTEGER || type == PortType.LONG || type == PortType.FLOAT
                || type == PortType.BOOLEAN || type == PortType.STRING || type == PortType.PATH
                || type == PortType.RICH_TEXT || type == PortType.ITEM || type == PortType.BLOCK_STATE
                || type == PortType.COLOR;
    }

    private static boolean isSummaryType(PortType type) {
        return type == PortType.LIST || type == PortType.DICT || type == PortType.SHOP;
    }

    private EntityTemplatePreviewView createEntityPreview(
            DataLibraryUiRepository.Entry entry,
            AtomicReference<DataLibraryUiRepository.Entry> current
    ) {
        EntityTemplatePreviewView preview;
        if (entry.type() == PortType.ENTITY) {
            preview = new EntityTemplatePreviewView(getContext()) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (isFocused() && handleEntityReferenceClipboard(event, current)) return true;
                    return super.dispatchKeyEvent(event);
                }
            };
        } else {
            preview = new EntityTemplatePreviewView(getContext());
            preview.setDisplayPasteAction(template -> updateValue(current, template));
        }
        preview.setDisplayClickAction(() -> editEntityValue(entry, current));
        return preview;
    }

    private boolean handleEntityReferenceClipboard(
            KeyEvent event,
            AtomicReference<DataLibraryUiRepository.Entry> current
    ) {
        KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
        if (copy != null && copy.matches(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Object value = current.get().value();
                entityReferenceClipboard = value instanceof UUID entityId ? entityId : null;
                hasEntityReferenceClipboard = true;
            }
            return true;
        }
        KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
        if (paste != null && paste.matches(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && hasEntityReferenceClipboard) {
                updateValue(current, entityReferenceClipboard);
            }
            return true;
        }
        return false;
    }

    private FrameLayout centeredPreview(View preview) {
        FrameLayout host = new FrameLayout(getContext());
        host.addView(preview, new FrameLayout.LayoutParams(
                px(PREVIEW_SIZE_DP), px(PREVIEW_SIZE_DP), Gravity.CENTER));
        return host;
    }

    private void editItemStackValue(AtomicReference<DataLibraryUiRepository.Entry> current) {
        AtomicBoolean picked = new AtomicBoolean();
        VanillaInventoryPicker.openItem(stack -> {
            picked.set(true);
            updateValue(current, stack == null || stack.isEmpty() ? null : stack.copy());
        }, () -> {
            if (!picked.get()) refresh();
        });
    }

    private void pasteItemStack(AtomicReference<DataLibraryUiRepository.Entry> current, String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        try {
            Object value = json == null || json.isBlank()
                    ? null
                    : DataLibraryValueCodec.decode(PortType.ITEM_STACK, JsonParser.parseString(json),
                    minecraft.level.registryAccess());
            updateValue(current, value instanceof ItemStack stack && stack.isEmpty() ? null : value);
        } catch (RuntimeException ignored) {
            // Ignore clipboard data that is not a valid item stack.
        }
    }

    private void updateValue(AtomicReference<DataLibraryUiRepository.Entry> current, Object value) {
        DataLibraryUiRepository.Entry latest = current.get();
        if (DataLibraryValueChanges.equivalent(latest, value)) return;
        DataLibraryUiRepository.Entry updated = DataLibraryValueChanges.replace(latest, value);
        current.set(updated);
        repository.update(latest, updated);
    }

    private void editEntityValue(DataLibraryUiRepository.Entry entry,
                                 AtomicReference<DataLibraryUiRepository.Entry> current) {
        AtomicBoolean picked = new AtomicBoolean();
        Runnable refreshIfCancelled = () -> {
            if (!picked.get()) refresh();
        };
        if (entry.type() == PortType.ENTITY) {
            DataLibraryEntityPickerController.open((reference, preview) -> {
                picked.set(true);
                updateValue(current, reference);
            }, refreshIfCancelled);
        } else if (entry.type() == PortType.ENTITY_TEMPLATE) {
            EntityTemplatePickerController.open(template -> {
                picked.set(true);
                updateValue(current, template);
            }, refreshIfCancelled);
        }
    }

    private void clearSelection() {
        if (selected.isEmpty() && selectedFolders.isEmpty()) return;
        selected.clear();
        selectedFolders.clear();
        selectionAnchor = null;
        rebuild();
    }

    private void selectAllVisible(boolean checked) {
        if (checked) {
            for (DataLibraryUiRepository.Folder folder : repository.folders()) {
                if (folder != null && folder.id() != null) selectedFolders.add(folder.id());
            }
        } else {
            selectedFolders.clear();
        }
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null) continue;
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
        }
        rebuild();
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
                if (target != null && target.type() == clipboardEntry.type()) updateValue(
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

    private String serializedValue(DataLibraryUiRepository.Entry entry) {
        return DataLibraryValueChanges.serialize(entry);
    }

    private void commitValue(AtomicReference<DataLibraryUiRepository.Entry> current, String json) {
        if (Minecraft.getInstance().level == null) return;
        try {
            DataLibraryUiRepository.Entry entry = current.get();
            var registries = Minecraft.getInstance().level.registryAccess();
            Object value = DataLibraryValueCodec.decode(entry.type(), JsonParser.parseString(json),
                    registries);
            if (DataLibraryValueCodec.encode(entry.type(), entry.value(), registries)
                    .equals(DataLibraryValueCodec.encode(entry.type(), value, registries))) return;
            DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                    entry.id(), entry.parentId(), entry.type(), entry.key(), value, entry.tags());
            current.set(updated);
            repository.update(entry, updated);
        } catch (RuntimeException ignored) {
            // Keep the previous valid value; malformed text never corrupts the library file.
        }
    }

    private static Entity resolveClientEntity(UUID entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        return minecraft.level.getEntity(entityId);
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

    private static UiActionButton button(Context context, String text, UiActionButton.Role role) {
        return new UiActionButton(context, text, role, UiActionButton.Density.COMPACT);
    }

    private static UiIconButton iconButton(Context context, View icon, String description) {
        UiIconButton button = new UiIconButton(context, icon,
                new UiIconButton.Style(3.0f, 1.0f, 0xFF3A3A3A, 0xFF4A4A4A, 0xFF484848));
        return button.tooltip(description);
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

    private static final class DataLibraryTreeLayout extends LinearLayout {
        private final Paint indicatorPaint = new Paint();
        private int indicatorY = -1;
        private int indicatorLeft = -1;

        private DataLibraryTreeLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            indicatorPaint.setColor(DROP_INDICATOR_COLOR);
        }

        private void updateIndicator(int y, int left) {
            if (indicatorY == y && indicatorLeft == left) return;
            indicatorY = y;
            indicatorLeft = left;
            invalidate();
        }

        private void hideIndicator() {
            if (indicatorY < 0) return;
            indicatorY = -1;
            indicatorLeft = -1;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (indicatorY < 0) return;
            float inset = Math.max(1, px(1));
            float y = Math.max(inset, Math.min(getHeight() - inset, indicatorY));
            float right = Math.max(inset, getWidth() - getPaddingRight());
            float left = Math.min(indicatorLeft, right);
            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setStrokeWidth(Math.max(1, px(2)));
            indicatorPaint.setColor(DROP_INDICATOR_COLOR);
            canvas.drawLine(left, y, right, y, indicatorPaint);
            canvas.drawLine(left, Math.max(inset, y - px(3)), left,
                    Math.min(getHeight() - inset, y + px(3)), indicatorPaint);
        }
    }

    @Override public View getView() { return this; }
    @Override public void onShow() {
        if (!dropTargetRegistered) {
            WorkspaceDragDropRegistry.register(dropTarget);
            dropTargetRegistered = true;
        }
        refresh();
    }

    @Override public void onDispose() {
        cancelInternalDrag();
        unregisterDropTarget();
        repository.removeChangeListener(repositoryChanged);
    }
    @Override public void onHide() {
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
