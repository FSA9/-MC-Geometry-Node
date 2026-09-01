package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
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
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragOperation;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryValueCodec;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntityReference;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryTypes;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Compact editor for the server-authoritative Data Library. */
public final class DataLibraryWindow extends LinearLayout implements AreaEditorWindow {
    private static final int BG = 0xFF1E1E1E;
    private static final int TOOLBAR_BG = 0xFF272727;
    private static final int PANEL_BG = 0xFF252525;
    private static final int HEADER_BG = 0xFF303030;
    private static final int CARD_BG = 0xFF2B2B2B;
    private static final int CARD_SELECTED_BG = 0xFF303840;
    private static final int INPUT_BG = 0xFF202020;
    private static final int BORDER = 0xFF444444;
    private static final int DIVIDER = 0xFF383838;
    private static final int TEXT = 0xFFD2D2D2;
    private static final int MUTED = 0xFF969696;
    private static final int ACCENT = 0xFF5C8CCB;
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final int PREVIEW_SIZE_DP = 44;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;
    private static final EnumSet<PortType> SUPPORTED_TYPES =
            EnumSet.copyOf(DataLibraryTypes.supported());

    private final DataLibraryUiRepository repository;
    private final LinearLayout groupsHost;
    private final UiSearchInput searchInput;
    private final UiCheckBox selectAll;
    private final View deleteButton;
    private final Set<DataLibraryUiRepository.EntryKey> selected = new LinkedHashSet<>();
    private final EnumMap<PortType, Boolean> expanded = new EnumMap<>(PortType.class);
    private final Runnable repositoryChanged = () -> post(this::rebuild);
    private String search = "";
    private boolean syncingSelectAll;
    private static DataLibraryEntityReference entityReferenceClipboard;
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

        addView(toolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(34)));

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

        selectAll = new UiCheckBox(context);
        selectAll.setContentDescription(tr("geometry_node.data_library.all"));
        selectAll.setOnCheckedChangeListener((button, checked) -> {
            if (!syncingSelectAll) selectAllVisible(checked);
        });
        LayoutParams selectLp = new LayoutParams(px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP));
        selectLp.leftMargin = px(3);
        searchRow.addView(selectAll, selectLp);

        deleteButton = iconButton(context,
                new VectorIconView(context, VectorIconView.Kind.TRASH, 0xFFFFB0B0),
                tr("geometry_node.common.delete"));
        deleteButton.setOnClickListener(v -> {
            repository.delete(Set.copyOf(selected));
            selected.clear();
        });
        LayoutParams deleteLp = fixed(28, 26);
        deleteLp.leftMargin = px(3);
        searchRow.addView(deleteButton, deleteLp);

        ScrollView scroll = new ScrollView(context);
        groupsHost = new LinearLayout(context);
        groupsHost.setOrientation(VERTICAL);
        groupsHost.setPadding(px(4), px(4), px(4), px(8));
        scroll.addView(groupsHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        rebuild();
    }

    private void refresh() {
        repository.refresh(() -> post(this::rebuild));
    }

    private void rebuild() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        Set<DataLibraryUiRepository.EntryKey> available = new LinkedHashSet<>();
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry != null && entry.type() != null && entry.id() != null) available.add(key(entry));
        }
        selected.retainAll(available);
        groupsHost.removeAllViews();
        EnumMap<PortType, List<DataLibraryUiRepository.Entry>> grouped = new EnumMap<>(PortType.class);
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (entry == null || entry.type() == null || !SUPPORTED_TYPES.contains(entry.type())) continue;
            if (!search.isEmpty() && (entry.name() == null
                    || !entry.name().toLowerCase(Locale.ROOT).contains(search))) continue;
            grouped.computeIfAbsent(entry.type(), ignored -> new ArrayList<>()).add(entry);
        }
        for (PortType type : SUPPORTED_TYPES) {
            List<DataLibraryUiRepository.Entry> entries = grouped.getOrDefault(type, List.of());
            groupsHost.addView(createTypeGroup(type, entries));
        }
        if (groupsHost.getChildCount() == 0) {
            TextView empty = label(getContext(), tr(search.isEmpty()
                    ? "geometry_node.data_library.empty" : "geometry_node.data_library.no_results"), 12, MUTED);
            empty.setGravity(Gravity.CENTER);
            groupsHost.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(90)));
        }
        updateActions();
    }

    private View createTypeGroup(PortType type, List<DataLibraryUiRepository.Entry> entries) {
        LinearLayout group = new LinearLayout(getContext());
        group.setOrientation(VERTICAL);
        group.setBackground(solid(PANEL_BG, 1, BORDER));
        group.setPadding(px(1), px(1), px(1), px(1));
        LayoutParams groupLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupLp.bottomMargin = px(3);
        group.setLayoutParams(groupLp);

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(5), 0, px(5), 0);
        header.setBackground(solid(HEADER_BG, 0));
        // Keep the library compact on first open; users can expand only the types they need.
        boolean isExpanded = expanded.getOrDefault(type, false);
        SvgIconView fold = new SvgIconView(getContext(),
                SvgIconView.Icon.forExpandedState(isExpanded), TEXT);
        header.addView(fold, fixed(16, 26));
        UiCheckBox typeSelect = new UiCheckBox(getContext());
        typeSelect.setChecked(!entries.isEmpty()
                && entries.stream().allMatch(entry -> selected.contains(key(entry))));
        typeSelect.setOnCheckedChangeListener((button, checked) -> {
            for (DataLibraryUiRepository.Entry entry : entries) {
                if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            }
            rebuild();
        });
        LayoutParams typeSelectLp = fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP);
        typeSelectLp.leftMargin = px(2);
        header.addView(typeSelect, typeSelectLp);
        TextView title = label(getContext(), type.name() + "  " + entries.size(), 10.5f, TEXT);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LayoutParams titleLp = new LayoutParams(0, px(26), 1);
        titleLp.leftMargin = px(5);
        header.addView(title, titleLp);
        SvgIconView add = new SvgIconView(getContext(), SvgIconView.Icon.SQUARE_PLUS, TEXT);
        add.setClickable(true);
        add.setContentDescription(tr("geometry_node.data_library.add"));
        add.setOnClickListener(v -> repository.create(type));
        header.addView(add, fixed(CHECKBOX_SIZE_DP + 4, CHECKBOX_SIZE_DP + 4));
        View.OnClickListener toggle = v -> {
            expanded.put(type, !isExpanded);
            rebuild();
        };
        header.setOnClickListener(toggle);
        group.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(26)));

        if (isExpanded) {
            DataLibraryGridLayout grid = new DataLibraryGridLayout(getContext(), cardHeightDp(type));
            grid.setPadding(px(3), px(3), px(3), px(3));
            for (DataLibraryUiRepository.Entry entry : entries) grid.addView(createCard(entry));
            group.addView(grid, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return group;
    }

    private View createCard(DataLibraryUiRepository.Entry entry) {
        AtomicReference<DataLibraryUiRepository.Entry> current = new AtomicReference<>(entry);
        FrameLayout card = new FrameLayout(getContext());
        card.setPadding(px(4), px(3), px(4), px(3));
        card.setBackground(solid(selected.contains(key(entry)) ? CARD_SELECTED_BG : CARD_BG, 1,
                selected.contains(key(entry)) ? ACCENT : BORDER));
        UiCheckBox check = new UiCheckBox(getContext());
        check.setChecked(selected.contains(key(entry)));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
            updateActions();
            card.setBackground(solid(checked ? CARD_SELECTED_BG : CARD_BG, 1,
                    checked ? ACCENT : BORDER));
        });
        FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(
                px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP),
                Gravity.RIGHT | Gravity.TOP);

        EditText name = new EditText(getContext());
        name.setSingleLine(true);
        name.setText(entry.name() == null ? "" : entry.name());
        name.setTextColor(TEXT);
        name.setBackground(null);
        name.setPadding(px(2), 0, px(2), 0);
        UIUtils.setLockedTextSize(name, EDITOR_TEXT_SIZE_DP);
        name.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                DataLibraryUiRepository.Entry latest = current.get();
                String updatedName = name.getText().toString();
                if (Objects.equals(latest.name(), updatedName)) return;
                DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                        latest.type(), latest.id(), updatedName, latest.value());
                current.set(updated);
                repository.update(updated);
            }
        });
        if (entry.type() == PortType.ENTITY || entry.type() == PortType.ENTITY_TEMPLATE) {
            LinearLayout content = new LinearLayout(getContext());
            content.setOrientation(VERTICAL);
            content.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(18)));

            EntityTemplatePreviewView preview = createEntityPreview(entry, current);
            if (entry.value() instanceof EntityTemplateValue template) preview.setDisplayTemplate(template);
            else if (entry.value() instanceof DataLibraryEntityReference reference) {
                Entity entity = resolveClientEntity(reference);
                if (entity != null) preview.setDisplayTemplate(EntityTemplateValue.capture(entity));
            }
            content.addView(centeredPreview(preview), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            UiActionButton edit = button(getContext(), tr(entry.type() == PortType.ENTITY
                            ? "geometry_node.data_library.choose_entity" : "geometry_node.ui.edit_template"),
                    UiActionButton.Role.INLINE);
            edit.setContentDescription(tr("geometry_node.data_library.edit_value"));
            edit.setOnClickListener(v -> editEntityValue(entry, current));
            content.addView(previewActions(edit, check), previewActionsLayoutParams());
            card.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (entry.type() == PortType.ITEM_STACK) {
            LinearLayout content = new LinearLayout(getContext());
            content.setOrientation(VERTICAL);
            content.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(18)));

            ItemSlotView preview = new ItemSlotView(getContext());
            preview.setDisplayStack(entry.value() instanceof ItemStack stack ? stack : ItemStack.EMPTY);
            preview.setOpenEditorOnClick(false);
            preview.setDisplayPasteAction(json -> pasteItemStack(current, json));
            content.addView(centeredPreview(preview), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            UiActionButton edit = button(getContext(), tr("geometry_node.node.pick_item_stack"),
                    UiActionButton.Role.INLINE);
            edit.setContentDescription(tr("geometry_node.data_library.edit_value"));
            edit.setOnClickListener(v -> editItemStackValue(current));
            content.addView(previewActions(edit, check), previewActionsLayoutParams());
            card.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (entry.type() == PortType.XYZ) {
            LinearLayout content = new LinearLayout(getContext());
            content.setOrientation(VERTICAL);
            content.setPadding(0, 0, px(CHECKBOX_SIZE_DP + 3), 0);
            content.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(18)));
            content.addView(createXyzValueEditor(current), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            card.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(check, checkLp);
        } else if (isSummaryType(entry.type())) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LayoutParams(0, px(26), 0.42f));
            TextView summary = label(getContext(), valueSummary(entry), 10.5f, MUTED);
            summary.setSingleLine(true);
            summary.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            LayoutParams summaryLp = new LayoutParams(0, px(26), 0.58f);
            summaryLp.leftMargin = px(3);
            row.addView(summary, summaryLp);
            LayoutParams summaryCheckLp = new LayoutParams(px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP));
            summaryCheckLp.leftMargin = px(3);
            row.addView(check, summaryCheckLp);
            card.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (isInlineValueType(entry.type())) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LayoutParams(0, px(26), 0.34f));

            View valueEditor = switch (entry.type()) {
                case BOOLEAN -> createBooleanValueEditor(entry, current);
                case STRING -> createPlainTextValueEditor(current);
                case PATH -> createPathValueEditor(current);
                case RICH_TEXT -> createRichTextValueEditor(current);
                case ITEM, BLOCK -> createRegistryValueEditor(entry.type(), current);
                default -> createValueEditor(entry, current);
            };
            LayoutParams valueLp = new LayoutParams(0, px(26), 0.66f);
            valueLp.leftMargin = px(2);
            row.addView(valueEditor, valueLp);
            LayoutParams inlineCheckLp = new LayoutParams(
                    px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP));
            inlineCheckLp.leftMargin = px(3);
            row.addView(check, inlineCheckLp);
            card.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout content = new LinearLayout(getContext());
            content.setOrientation(VERTICAL);
            content.setPadding(0, 0, px(CHECKBOX_SIZE_DP + 3), 0);
            content.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(20)));
            content.addView(createValueEditor(entry, current),
                    new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            card.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(check, checkLp);
        }
        installDragZone(card, entry);
        return card;
    }

    /** Dedicated title-bar drag strip, kept separate from editors and action controls. */
    private void installDragZone(FrameLayout card, DataLibraryUiRepository.Entry entry) {
        boolean vertical = isVerticalCard(entry.type());
        int reserved = vertical ? px(12) : px(30);
        for (int index = 0; index < card.getChildCount(); index++) {
            View child = card.getChildAt(index);
            if (!(child.getLayoutParams() instanceof FrameLayout.LayoutParams params)) continue;
            if (vertical) params.topMargin += reserved;
            else params.leftMargin += reserved;
            child.setLayoutParams(params);
        }
        View dragZone = new View(getContext());
        // ModernUI only dispatches touch listeners reliably for an interactive view.
        dragZone.setClickable(true);
        dragZone.setContentDescription(tr("geometry_node.data_library.drag"));
        dragZone.setBackground(solid(HEADER_BG, 1, ACCENT));
        dragZone.setOnTouchListener(new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
            @Override public void onPressed(MotionEvent event) {}

            @Override public void onDragStarted(MotionEvent event) {
                WorkspaceDragService.INSTANCE.begin(
                        new DataLibraryDragPayload(entry.type(), entry.id(), entry.name()),
                        WorkspaceDragOperation.LINK, DataLibraryWindow.this);
            }

            @Override public void onDragged(MotionEvent event) {}

            @Override public void onReleased(MotionEvent event, boolean moved) {
                if (!moved) return;
                if (!WorkspaceDragService.INSTANCE.drop(event.getRawX(), event.getRawY())) {
                    WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
                }
            }

            @Override public void onCancelled(MotionEvent event) {
                WorkspaceDragService.INSTANCE.cancelIfSource(DataLibraryWindow.this);
            }
        }));
        FrameLayout.LayoutParams dragLp;
        if (vertical) {
            dragLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, px(10), Gravity.LEFT | Gravity.TOP);
            dragLp.leftMargin = px(1);
            dragLp.rightMargin = px(1);
            dragLp.topMargin = px(1);
        } else {
            dragLp = new FrameLayout.LayoutParams(
                    px(14), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP);
            dragLp.leftMargin = px(1);
            dragLp.topMargin = px(1);
            dragLp.bottomMargin = px(1);
        }
        card.addView(dragZone, dragLp);
    }

    private static boolean isVerticalCard(PortType type) {
        return type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE
                || type == PortType.ITEM_STACK || type == PortType.XYZ;
    }

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
        if (type == PortType.BLOCK) {
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
        if (type == PortType.BLOCK && value instanceof BlockState state) {
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

    private View createBooleanValueEditor(DataLibraryUiRepository.Entry entry,
                                          AtomicReference<DataLibraryUiRepository.Entry> current) {
        FrameLayout host = new FrameLayout(getContext());
        UiCheckBox valueCheckBox = new UiCheckBox(getContext());
        valueCheckBox.setChecked(Boolean.TRUE.equals(entry.value()));
        valueCheckBox.setOnCheckedChangeListener((button, checked) -> {
            DataLibraryUiRepository.Entry latest = current.get();
            DataLibraryUiRepository.Entry updated = new DataLibraryUiRepository.Entry(
                    latest.type(), latest.id(), latest.name(), checked);
            current.set(updated);
            repository.update(updated);
        });
        host.addView(valueCheckBox, new FrameLayout.LayoutParams(
                px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP), Gravity.CENTER));
        return host;
    }

    private static boolean isInlineValueType(PortType type) {
        return type == PortType.INTEGER || type == PortType.LONG || type == PortType.FLOAT
                || type == PortType.BOOLEAN || type == PortType.STRING || type == PortType.PATH
                || type == PortType.RICH_TEXT || type == PortType.ITEM || type == PortType.BLOCK
                || type == PortType.COLOR;
    }

    private static boolean isSummaryType(PortType type) {
        return type == PortType.LIST || type == PortType.DICT || type == PortType.SHOP;
    }

    private static boolean isPreviewType(PortType type) {
        return type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE || type == PortType.ITEM_STACK;
    }

    private static int cardHeightDp(PortType type) {
        if (isInlineValueType(type) || isSummaryType(type)) return 36;
        if (type == PortType.XYZ) return 94;
        return isPreviewType(type) ? 94 : 70;
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
                entityReferenceClipboard = value instanceof DataLibraryEntityReference reference ? reference : null;
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

    private LinearLayout previewActions(View edit, View check) {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(edit, new LayoutParams(0, px(18), 1));
        LayoutParams checkLp = new LayoutParams(px(CHECKBOX_SIZE_DP), px(CHECKBOX_SIZE_DP));
        checkLp.leftMargin = px(3);
        actions.addView(check, checkLp);
        return actions;
    }

    private static LayoutParams previewActionsLayoutParams() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(18));
        params.topMargin = px(2);
        return params;
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
                latest.type(), latest.id(), latest.name(), value);
        current.set(updated);
        repository.update(updated);
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
            if (entry == null || !SUPPORTED_TYPES.contains(entry.type())) continue;
            if (!search.isEmpty() && (entry.name() == null
                    || !entry.name().toLowerCase(Locale.ROOT).contains(search))) continue;
            if (checked) selected.add(key(entry)); else selected.remove(key(entry));
        }
        rebuild();
    }

    private void updateActions() {
        boolean hasSelection = !selected.isEmpty();
        deleteButton.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
        boolean hasVisible = false;
        boolean allVisibleSelected = true;
        for (DataLibraryUiRepository.Entry entry : repository.entries()) {
            if (!isVisible(entry)) continue;
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

    private boolean isVisible(DataLibraryUiRepository.Entry entry) {
        if (entry == null || entry.type() == null || !SUPPORTED_TYPES.contains(entry.type())) return false;
        return search.isEmpty() || (entry.name() != null
                && entry.name().toLowerCase(Locale.ROOT).contains(search));
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
                    entry.type(), entry.id(), entry.name(), value);
            current.set(updated);
            repository.update(updated);
        } catch (RuntimeException ignored) {
            // Keep the previous valid value; malformed text never corrupts the library file.
        }
    }

    private static Entity resolveClientEntity(DataLibraryEntityReference reference) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().identifier().equals(reference.dimension())) return null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getUUID().equals(reference.entityId())) return entity;
        }
        return null;
    }

    private static DataLibraryUiRepository.EntryKey key(DataLibraryUiRepository.Entry entry) {
        return new DataLibraryUiRepository.EntryKey(entry.type(), entry.id());
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static UiActionButton button(Context context, String text, UiActionButton.Role role) {
        return new UiActionButton(context, text, role, UiActionButton.Density.COMPACT);
    }

    private static View iconButton(Context context, View icon, String description) {
        UiIconButton button = new UiIconButton(context, icon);
        button.setContentDescription(description);
        return button;
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

    @Override public View getView() { return this; }
    @Override public void onShow() { refresh(); }

    @Override public void onDispose() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        repository.removeChangeListener(repositoryChanged);
    }
    @Override public void onHide() {}
}
