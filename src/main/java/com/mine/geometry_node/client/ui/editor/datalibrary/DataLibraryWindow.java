package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.common.VectorIconView;
import com.mine.geometry_node.client.ui.components.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.editor.asset.dialog.FilePickerDialog;
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
    private static final int DRAG_HANDLE_COLOR = 0xFF46515D;
    private static final int DROP_INDICATOR_COLOR = 0xFF00AAFF;
    private static final int DRAG_HANDLE_WIDTH_DP = 12;
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final int PREVIEW_SIZE_DP = 44;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;
    private static final EnumSet<PortType> SUPPORTED_TYPES =
            EnumSet.copyOf(DataLibraryTypes.supported());

    private final DataLibraryUiRepository repository;
    private final DataLibraryTreeLayout groupsHost;
    private final ScrollView scrollHost;
    private final UiSearchInput searchInput;
    private final UiCheckBox selectAll;
    private final View deleteButton;
    private final Set<DataLibraryUiRepository.EntryKey> selected = new LinkedHashSet<>();
    private final Set<UUID> selectedFolders = new LinkedHashSet<>();
    private final Set<UUID> expandedFolders = new HashSet<>();
    private final Set<UUID> expandedEntries = new HashSet<>();
    private final Set<UUID> renderedEntries = new LinkedHashSet<>();
    private final Map<View, UUID> folderDropRows = new java.util.LinkedHashMap<>();
    private final WorkspaceDragDropRegistry.DropTarget dropTarget = this::acceptInternalDrop;
    private final Runnable repositoryChanged = () -> post(this::requestRebuild);
    private View rootDropTarget;
    private boolean dropTargetRegistered;
    private String search = "";
    private PortType typeFilter;
    private String tagFilter;
    private boolean syncingSelectAll;
    private boolean internalDragActive;
    private boolean rebuildPending;
    private static UUID entityReferenceClipboard;
    private static boolean hasEntityReferenceClipboard;

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
        toolbar.setOrientation(VERTICAL);
        toolbar.setPadding(px(4), px(3), px(4), px(3));
        toolbar.setBackground(solid(TOOLBAR_BG, 0, DIVIDER));

        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(searchRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(28)));
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams actionsRowLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(26));
        actionsRowLp.topMargin = px(2);
        toolbar.addView(actionsRow, actionsRowLp);

        addView(toolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(62)));

        searchInput = new UiSearchInput(context);
        searchInput.setHint(tr("geometry_node.data_library.search"));
        searchInput.setOnQueryChanged(value -> {
            search = value.trim().toLowerCase(Locale.ROOT);
            rebuild();
        });
        LayoutParams searchLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        searchRow.addView(searchInput, searchLp);

        TextView clear = toolbarIconButton(context, "×", tr("geometry_node.data_library.clear_search"));
        clear.setOnClickListener(v -> searchInput.setText(""));
        LayoutParams clearLp = new LayoutParams(px(26), ViewGroup.LayoutParams.MATCH_PARENT);
        clearLp.leftMargin = px(2);
        searchRow.addView(clear, clearLp);

        UiActionButton typeButton = button(context, tr("geometry_node.data_library.all_types"),
                UiActionButton.Role.INLINE);
        typeButton.setOnClickListener(v -> showTypeMenu(typeButton, selectedType -> {
            typeFilter = selectedType;
            typeButton.setText(selectedType == null ? tr("geometry_node.data_library.all_types") : selectedType.name());
            rebuild();
        }, true));
        LayoutParams typeLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        actionsRow.addView(typeButton, typeLp);

        UiActionButton tagButton = button(context, tr("geometry_node.data_library.all_tags"),
                UiActionButton.Role.INLINE);
        tagButton.setOnClickListener(v -> showTagMenu(tagButton, selectedTag -> {
            tagFilter = selectedTag;
            tagButton.setText(selectedTag == null ? tr("geometry_node.data_library.all_tags") : selectedTag);
            rebuild();
        }));
        LayoutParams tagFilterLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        tagFilterLp.leftMargin = px(3);
        actionsRow.addView(tagButton, tagFilterLp);

        UiIconButton addFolder = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SQUARE_PLUS, TEXT),
                tr("geometry_node.data_library.add_folder"));
        addFolder.setOnClickListener(v -> repository.createFolder(null));
        LayoutParams addFolderLp = fixed(28, 26);
        addFolderLp.leftMargin = px(3);
        actionsRow.addView(addFolder, addFolderLp);

        UiIconButton addEntry = iconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SQUARE_PLUS, ACCENT),
                tr("geometry_node.data_library.add"));
        addEntry.setOnClickListener(v -> showTypeMenu(addEntry,
                selectedType -> repository.create(null, selectedType), false));
        LayoutParams addEntryLp = fixed(28, 26);
        addEntryLp.leftMargin = px(2);
        actionsRow.addView(addEntry, addEntryLp);

        selectAll = new UiCheckBox(context);
        selectAll.setContentDescription(tr("geometry_node.data_library.all"));
        selectAll.setOnCheckedChangeListener((button, checked) -> {
            if (!syncingSelectAll) selectAllVisible(checked);
        });
        LayoutParams selectLp = new LayoutParams(px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP));
        selectLp.leftMargin = px(3);
        actionsRow.addView(selectAll, selectLp);

        deleteButton = iconButton(context,
                new VectorIconView(context, VectorIconView.Kind.TRASH, 0xFFFFB0B0),
                tr("geometry_node.common.delete"));
        deleteButton.setOnClickListener(v -> {
            repository.delete(Set.copyOf(selected), Set.copyOf(selectedFolders));
            selected.clear();
            selectedFolders.clear();
        });
        LayoutParams deleteLp = fixed(28, 26);
        deleteLp.leftMargin = px(3);
        actionsRow.addView(deleteButton, deleteLp);

        scrollHost = new ScrollView(context);
        scrollHost.setFillViewport(true);
        groupsHost = new DataLibraryTreeLayout(context);
        groupsHost.setOrientation(VERTICAL);
        groupsHost.setPadding(px(4), px(4), px(4), px(8));
        scrollHost.addView(groupsHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scrollHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        rebuild();
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
        expandedFolders.retainAll(availableFolders);
        expandedEntries.retainAll(available.stream().map(DataLibraryUiRepository.EntryKey::id)
                .collect(java.util.stream.Collectors.toSet()));
        groupsHost.removeAllViews();
        groupsHost.hideIndicator();
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
        addTreeLevel(null, 0, childFolders, childEntries, matchedEntries, visibleFolders, new HashSet<>());
        if (groupsHost.getChildCount() == 1) {
            TextView empty = label(getContext(), tr(!isFiltering()
                    ? "geometry_node.data_library.empty" : "geometry_node.data_library.no_results"), 12, MUTED);
            empty.setGravity(Gravity.CENTER);
            groupsHost.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(90)));
        }
        updateActions();
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
        for (DataLibraryUiRepository.Entry entry : childEntries.getOrDefault(parentId, List.of())) {
            if (isFiltering() && !matchedEntries.contains(entry.id())) continue;
            renderedEntries.add(entry.id());
            View row = createCard(entry);
            row.setPadding(px(4 + depth * 14), px(3), px(4), px(3));
            groupsHost.addView(row, rowParams(entry.type()));
        }
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

    private View createFolderRow(DataLibraryUiRepository.Folder folder, int depth, boolean expanded) {
        AtomicReference<DataLibraryUiRepository.Folder> current = new AtomicReference<>(folder);
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(4 + depth * 14), 0, px(4), 0);
        row.setBackground(solid(selectedFolders.contains(folder.id()) ? ROW_SELECTED_BG : HEADER_BG, 0, BORDER));
        row.addView(createDragHandle(DataLibraryDragPayload.folder(folder.id())),
                fixed(DRAG_HANDLE_WIDTH_DP, 28));
        SvgIconView disclosure = new SvgIconView(getContext(), SvgIconView.Icon.forExpandedState(expanded), TEXT);
        disclosure.setClickable(true);
        disclosure.setOnClickListener(v -> {
            if (!expandedFolders.remove(folder.id())) expandedFolders.add(folder.id());
            rebuild();
        });
        row.addView(disclosure, fixed(18, 28));

        UiCheckBox check = new UiCheckBox(getContext());
        check.setChecked(selectedFolders.contains(folder.id()));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selectedFolders.add(folder.id()); else selectedFolders.remove(folder.id());
            row.setBackground(solid(checked ? ROW_SELECTED_BG : HEADER_BG, 0,
                    checked ? ACCENT : BORDER));
            updateActions();
        });
        LayoutParams checkLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        checkLp.leftMargin = px(2);
        row.addView(check, checkLp);

        EditText name = styledNameEditor(folder.name());
        name.setOnFocusChangeListener((view, focused) -> {
            DataLibraryUiRepository.Folder expected = current.get();
            if (!focused && !Objects.equals(expected.name(), name.getText().toString())) {
                DataLibraryUiRepository.Folder replacement = new DataLibraryUiRepository.Folder(
                        expected.id(), expected.parentId(), name.getText().toString());
                current.set(replacement);
                repository.updateFolder(expected, replacement);
            }
        });
        LayoutParams nameLp = new LayoutParams(0, px(28), 1);
        nameLp.leftMargin = px(5);
        row.addView(name, nameLp);

        UiIconButton addFolder = iconButton(getContext(),
                new SvgIconView(getContext(), SvgIconView.Icon.SQUARE_PLUS, TEXT),
                tr("geometry_node.data_library.add_subfolder"));
        addFolder.setOnClickListener(v -> {
            expandedFolders.add(folder.id());
            repository.createFolder(folder.id());
        });
        row.addView(addFolder, fixed(26, 24));

        UiIconButton addEntry = iconButton(getContext(),
                new SvgIconView(getContext(), SvgIconView.Icon.SQUARE_PLUS, ACCENT),
                tr("geometry_node.data_library.add"));
        addEntry.setOnClickListener(v -> showTypeMenu(addEntry, selectedType -> {
            expandedFolders.add(folder.id());
            repository.create(folder.id(), selectedType);
        }, false));
        LayoutParams addLp = fixed(26, 24);
        addLp.leftMargin = px(2);
        row.addView(addEntry, addLp);
        folderDropRows.put(row, folder.id());
        return row;
    }

    private View createRootRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(4), 0, px(4), 0);
        row.setBackground(solid(HEADER_BG, 0, BORDER));
        VectorIconView folder = new VectorIconView(getContext(), VectorIconView.Kind.FOLDER, MUTED);
        row.addView(folder, fixed(18, 28));
        TextView name = label(getContext(), tr("geometry_node.data_library.root"), 11.5f, TEXT);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LayoutParams nameLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        nameLp.leftMargin = px(5);
        row.addView(name, nameLp);
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
        if (tagFilter != null && entry.tags().stream().noneMatch(tag -> tag.equalsIgnoreCase(tagFilter))) return false;
        return search.isEmpty() || entry.key().toLowerCase(Locale.ROOT).contains(search)
                || entry.type().name().toLowerCase(Locale.ROOT).contains(search)
                || entry.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(search));
    }

    private boolean isFiltering() {
        return typeFilter != null || tagFilter != null || !search.isEmpty();
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
        if (includeAll) labels.put("*", tr("geometry_node.data_library.all_types"));
        SelectHintRenderer.DropdownSearchMenu menu = new SelectHintRenderer.DropdownSearchMenu(
                getContext(), tr("geometry_node.data_library.choose_type"), options, labels, value -> {
            if (consumer == null) return;
            consumer.accept("*".equals(value) ? null : PortType.valueOf(value));
        });
        menu.showAt(anchor, overlayHost());
    }

    private void showTagMenu(View anchor, Consumer<String> consumer) {
        List<String> options = repository.entries().stream()
                .flatMap(entry -> entry.tags().stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        options.add(0, "*");
        Map<String, String> labels = new HashMap<>();
        labels.put("*", tr("geometry_node.data_library.all_tags"));
        SelectHintRenderer.DropdownSearchMenu menu = new SelectHintRenderer.DropdownSearchMenu(
                getContext(), tr("geometry_node.data_library.choose_tag"), options, labels,
                value -> consumer.accept("*".equals(value) ? null : value));
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
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(solid(selected.contains(key(entry)) ? ROW_SELECTED_BG : ROW_BG, 0,
                selected.contains(key(entry)) ? ACCENT : BORDER));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(createDragHandle(DataLibraryDragPayload.entry(entry.id(), entry.type(), entry.key())),
                fixed(DRAG_HANDLE_WIDTH_DP, 32));

        boolean expanded = expandedEntries.contains(entry.id());
        SvgIconView disclosure = new SvgIconView(getContext(), SvgIconView.Icon.forExpandedState(expanded), TEXT);
        disclosure.setClickable(true);
        disclosure.setContentDescription(tr(expanded
                ? "geometry_node.data_library.collapse_entry"
                : "geometry_node.data_library.expand_entry"));
        disclosure.setOnClickListener(v -> toggleEntry(entry.id()));
        header.addView(disclosure, fixed(18, 32));

        TextView type = label(getContext(), entry.type().name(), 9.0f, MUTED);
        type.setSingleLine(true);
        type.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(type, fixed(66, 32));

        TextView name = label(getContext(), entry.key(), 11.5f, TEXT);
        name.setSingleLine(true);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(name, new LayoutParams(0, px(32), 0.38f));

        TextView summary = label(getContext(), compactValueSummary(entry), 10.0f, MUTED);
        summary.setSingleLine(true);
        summary.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LayoutParams summaryLp = new LayoutParams(0, px(32), 0.34f);
        summaryLp.leftMargin = px(4);
        header.addView(summary, summaryLp);

        if (!entry.tags().isEmpty()) {
            TagFlowLayout tags = createCompactTags(entry.tags());
            LayoutParams tagsLp = new LayoutParams(0, px(22), 0.28f);
            tagsLp.leftMargin = px(4);
            header.addView(tags, tagsLp);
        }

        UiCheckBox check = new UiCheckBox(getContext());
        check.setChecked(selected.contains(key(entry)));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            updateActions();
            card.setBackground(solid(checked ? ROW_SELECTED_BG : ROW_BG, 0,
                    checked ? ACCENT : BORDER));
        });
        LayoutParams checkLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        checkLp.leftMargin = px(5);
        checkLp.rightMargin = px(5);
        header.addView(check, checkLp);
        header.setClickable(true);
        header.setOnClickListener(v -> toggleEntry(entry.id()));
        card.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(32)));
        if (expanded) {
            View divider = new View(getContext());
            divider.setBackground(solid(DIVIDER, 0));
            LayoutParams dividerLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1));
            dividerLp.leftMargin = px(30);
            card.addView(divider, dividerLp);
            card.addView(createExpandedEditor(entry, current), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
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
        content.setPadding(px(34), px(5), px(6), px(6));

        LinearLayout metadata = new LinearLayout(getContext());
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        EditText name = styledNameEditor(entry.key());
        name.setHint(tr("geometry_node.data_library.name"));
        name.setBackground(solid(INPUT_BG, 2, BORDER));
        name.setOnFocusChangeListener((view, focused) -> {
            if (focused) return;
            DataLibraryUiRepository.Entry latest = current.get();
            String updatedName = name.getText().toString();
            if (Objects.equals(latest.key(), updatedName)) return;
            DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                    latest.id(), latest.parentId(), latest.type(), updatedName, latest.value(), latest.tags());
            current.set(updated);
            repository.update(latest, updated);
        });
        metadata.addView(name, new LayoutParams(0, px(26), 1));

        UiActionButton copyId = button(getContext(), "ID", UiActionButton.Role.INLINE);
        copyId.setContentDescription(tr("geometry_node.data_library.copy_uuid"));
        copyId.setOnClickListener(v -> Minecraft.getInstance().keyboardHandler
                .setClipboard(current.get().id().toString()));
        LayoutParams copyLp = fixed(32, 22);
        copyLp.leftMargin = px(4);
        metadata.addView(copyId, copyLp);
        content.addView(metadata, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(28)));

        EditText tagEditor = styledNameEditor(String.join(", ", entry.tags()));
        tagEditor.setHint(tr("geometry_node.data_library.tags_hint"));
        tagEditor.setBackground(solid(INPUT_BG, 2, BORDER));
        tagEditor.setOnFocusChangeListener((view, focused) -> {
            if (focused) return;
            DataLibraryUiRepository.Entry latest = current.get();
            List<String> updatedTags = parseTags(tagEditor.getText().toString());
            if (latest.tags().equals(updatedTags)) return;
            DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                    latest.id(), latest.parentId(), latest.type(), latest.key(), latest.value(), updatedTags);
            current.set(updated);
            repository.update(latest, updated);
        });
        LayoutParams tagsLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(26));
        tagsLp.topMargin = px(3);
        content.addView(tagEditor, tagsLp);

        View valueEditor = createExpandedValueEditor(entry, current);
        LayoutParams valueLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                px(expandedValueHeightDp(entry.type())));
        valueLp.topMargin = px(4);
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
            content.addView(centeredPreview(preview), fixed(58, 58));
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
            content.addView(centeredPreview(preview), fixed(58, 58));
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
        for (String part : raw.split("[,，;；]+")) {
            String tag = part.trim();
            if (!tag.isEmpty()) tags.add(tag);
        }
        return List.copyOf(tags);
    }

    private static int expandedValueHeightDp(PortType type) {
        if (type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE || type == PortType.ITEM_STACK) return 62;
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

    private View createDragHandle(DataLibraryDragPayload payload) {
        View handle = new View(getContext());
        handle.setBackground(solid(DRAG_HANDLE_COLOR, 0));
        handle.setClickable(true);
        handle.setContentDescription(tr("geometry_node.data_library.drag"));
        handle.setOnTouchListener(new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
            @Override public void onPressed(MotionEvent event) {
                internalDragActive = true;
            }

            @Override public void onDragStarted(MotionEvent event) {
                handle.setAlpha(0.45f);
                WorkspaceDragService.INSTANCE.begin(
                        payload, WorkspaceDragOperation.CONTEXTUAL, DataLibraryWindow.this);
            }

            @Override public void onDragged(MotionEvent event) {
                updateDropIndicator(event.getRawX(), event.getRawY());
            }

            @Override public void onReleased(MotionEvent event, boolean moved) {
                handle.setAlpha(1.0f);
                groupsHost.hideIndicator();
                if (moved && !WorkspaceDragService.INSTANCE.drop(event.getRawX(), event.getRawY())) {
                    WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
                }
                finishInternalDrag();
            }

            @Override public void onCancelled(MotionEvent event) {
                handle.setAlpha(1.0f);
                groupsHost.hideIndicator();
                WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
                finishInternalDrag();
            }
        }));
        return handle;
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
        groupsHost.updateIndicator(destination.top(), destination.bottom());
    }

    private boolean acceptInternalDrop(WorkspaceDragState.Session session, float rawX, float rawY) {
        if (!(session.payload() instanceof DataLibraryDragPayload payload)) return false;
        DropDestination destination = dropDestinationAt(rawX, rawY);
        if (destination == null || !canMove(payload, destination.parentId())) return false;
        return movePayload(payload, destination.parentId());
    }

    private DropDestination dropDestinationAt(float rawX, float rawY) {
        if (isRawPointInside(rootDropTarget, rawX, rawY)) {
            return new DropDestination(null, rootDropTarget.getTop(), rootDropTarget.getBottom());
        }
        if (!isRawPointInside(scrollHost, rawX, rawY)) return null;
        for (Map.Entry<View, UUID> candidate : folderDropRows.entrySet()) {
            View row = candidate.getKey();
            if (isRawPointInside(row, rawX, rawY)) {
                return new DropDestination(candidate.getValue(), row.getTop(), row.getBottom());
            }
        }
        return null;
    }

    private boolean movePayload(DataLibraryDragPayload payload, UUID targetParent) {
        if (payload.isEntry()) {
            DataLibraryUiRepository.Entry expected = repository.findEntry(payload.id());
            if (expected == null) return true;
            if (targetParent != null) expandedFolders.add(targetParent);
            repository.moveEntry(expected.id(), targetParent);
            return true;
        }

        DataLibraryUiRepository.Folder expected = repository.folders().stream()
                .filter(folder -> folder.id().equals(payload.id())).findFirst().orElse(null);
        if (expected == null) return true;
        if (targetParent != null) expandedFolders.add(targetParent);
        repository.moveFolder(expected.id(), targetParent);
        return true;
    }

    private boolean canMove(DataLibraryDragPayload payload, UUID targetParent) {
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

    private record DropDestination(UUID parentId, int top, int bottom) {}

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
        FrameLayout host = new FrameLayout(getContext());
        UiCheckBox valueCheckBox = new UiCheckBox(getContext());
        valueCheckBox.setChecked(Boolean.TRUE.equals(entry.value()));
        valueCheckBox.setOnCheckedChangeListener((button, checked) -> {
            DataLibraryUiRepository.Entry latest = current.get();
            DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                    latest.id(), latest.parentId(), latest.type(), latest.key(), checked, latest.tags());
            current.set(updated);
            repository.update(latest, updated);
        });
        host.addView(valueCheckBox, new FrameLayout.LayoutParams(
                px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP), Gravity.CENTER));
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            try {
                var registries = minecraft.level.registryAccess();
                if (DataLibraryValueCodec.encode(latest.type(), latest.value(), registries)
                        .equals(DataLibraryValueCodec.encode(latest.type(), value, registries))) return;
            } catch (RuntimeException ignored) {
                // Let the server-side codec report unsupported values through the transfer result.
            }
        } else if (Objects.equals(latest.value(), value)) {
            return;
        }
        DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                latest.id(), latest.parentId(), latest.type(), latest.key(), value, latest.tags());
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

    private void selectAllVisible(boolean checked) {
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null || !renderedEntries.contains(entry.id())) continue;
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
        }
        rebuild();
    }

    private void updateActions() {
        boolean hasSelection = !selected.isEmpty() || !selectedFolders.isEmpty();
        deleteButton.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
        boolean hasVisible = false;
        boolean allVisibleSelected = true;
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null || !renderedEntries.contains(entry.id())) continue;
            hasVisible = true;
            if (!selected.contains(key(entry))) allVisibleSelected = false;
        }
        boolean checked = hasVisible && allVisibleSelected;
        if (selectAll.isChecked() != checked) {
            syncingSelectAll = true;
            selectAll.setChecked(checked);
            syncingSelectAll = false;
        }
    }

    private String serializedValue(DataLibraryUiRepository.Entry entry) {
        try {
            if (Minecraft.getInstance().level == null) return String.valueOf(entry.value());
            return DataLibraryValueCodec.encode(entry.type(), entry.value(),
                    Minecraft.getInstance().level.registryAccess()).toString();
        } catch (RuntimeException ignored) {
            return String.valueOf(entry.value());
        }
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

    private static UiActionButton button(Context context, String text, UiActionButton.Role role) {
        return new UiActionButton(context, text, role, UiActionButton.Density.COMPACT);
    }

    private static UiIconButton iconButton(Context context, View icon, String description) {
        UiIconButton button = new UiIconButton(context, icon);
        return button.tooltip(description);
    }

    private static TextView toolbarIconButton(Context context, String text, String description) {
        TextView button = UIUtils.createLockedTextView(context, text, 14, TEXT);
        button.setSingleLine(true);
        button.setPadding(0, 0, 0, 0);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(solid(0xFF353535, 1, BORDER));
        button.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                button.setBackground(solid(0xFF444444, 1, BORDER));
                button.setTextColor(ACCENT);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                button.setBackground(solid(0xFF353535, 1, BORDER));
                button.setTextColor(TEXT);
            }
            return false;
        });
        return button;
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
        private int indicatorTop = -1;
        private int indicatorBottom = -1;

        private DataLibraryTreeLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            indicatorPaint.setColor(DROP_INDICATOR_COLOR);
        }

        private void updateIndicator(int top, int bottom) {
            if (indicatorTop == top && indicatorBottom == bottom) return;
            indicatorTop = top;
            indicatorBottom = bottom;
            invalidate();
        }

        private void hideIndicator() {
            if (indicatorTop < 0) return;
            indicatorTop = -1;
            indicatorBottom = -1;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (indicatorTop < 0 || indicatorBottom <= indicatorTop) return;
            float top = Math.max(0, indicatorTop);
            float bottom = Math.min(getHeight(), indicatorBottom);
            indicatorPaint.setStyle(Paint.Style.FILL);
            indicatorPaint.setColor(0x335C8CCB);
            canvas.drawRect(0, top, getWidth(), bottom, indicatorPaint);
            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setStrokeWidth(Math.max(1, px(1)));
            indicatorPaint.setColor(DROP_INDICATOR_COLOR);
            float inset = Math.max(1, px(1));
            canvas.drawRect(inset, top + inset, getWidth() - inset, bottom - inset, indicatorPaint);
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
