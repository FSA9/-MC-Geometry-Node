package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryEntryValues;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.integration.picker.DataLibraryEntityPickerController;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.composite.layout.UiPropertyRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.components.integration.asset.dialog.FilePickerDialog;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchableMenu;
import com.mine.geometry_node.client.integration.picker.EntityTemplatePickerController;
import com.mine.geometry_node.client.integration.picker.VanillaInventoryPicker;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryValueCodec;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.LinearLayout.LayoutParams;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static icyllis.modernui.widget.LinearLayout.VERTICAL;

/** Value-specific controls, native previews and commits; the window owns card/draft lifetimes. */
final class DataLibraryValueEditors {
    private static final int INPUT_BG = UiThemes.EDITOR.control().input();
    private static final int BORDER = UiThemes.EDITOR.control().inputBorder();
    private static final int TEXT = UiThemes.EDITOR.text().normal();
    private static final int MUTED = UiThemes.EDITOR.text().muted();
    private static final int CHECKBOX_SIZE_DP = 16;
    private static final int CARD_PREVIEW_SIZE_DP = 64;
    private static final int EDIT_ACTION_SIZE_DP = 26;
    private static final float EDITOR_TEXT_SIZE_DP = 12.0f;

    private final Context context;
    private final DataLibraryRepository repository;
    private final Supplier<ViewGroup> overlayHost;
    private final Runnable refresh;
    private final Runnable changed;
    private final BooleanSupplier active;
    private final Map<UUID, Integer> externalEditors = new HashMap<>();
    private static UUID entityReferenceClipboard;
    private static boolean hasEntityReferenceClipboard;

    DataLibraryValueEditors(Context context, DataLibraryRepository repository,
                            Supplier<ViewGroup> overlayHost, Runnable refresh, Runnable changed,
                            BooleanSupplier active) {
        this.context = context;
        this.repository = repository;
        this.overlayHost = overlayHost;
        this.refresh = refresh;
        this.changed = changed;
        this.active = active;
    }

    private Context getContext() { return context; }
    private ViewGroup overlayHost() { return overlayHost.get(); }
    private void refresh() { if (active.getAsBoolean()) refresh.run(); }
    private void changed() { if (active.getAsBoolean()) changed.run(); }

    synchronized boolean isEditing(UUID entryId) {
        return externalEditors.containsKey(entryId);
    }

    private Runnable beginExternalEdit(AtomicReference<DataLibraryRepository.Entry> current) {
        UUID id = current.get().id();
        synchronized (this) { externalEditors.merge(id, 1, Integer::sum); }
        AtomicBoolean finished = new AtomicBoolean();
        return () -> {
            if (!finished.compareAndSet(false, true)) return;
            synchronized (this) {
                externalEditors.computeIfPresent(id, (ignored, count) -> count > 1 ? count - 1 : null);
            }
            changed();
        };
    }

    private static void finishOnDetach(View overlay, Runnable finished) {
        if (overlay == null) {
            finished.run();
            return;
        }
        overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                finished.run();
            }
        });
    }

    private void openNativePicker(AtomicReference<DataLibraryRepository.Entry> current,
                                  BiConsumer<Consumer<Object>, Runnable> open) {
        if (!canUpdate(current) || isEditing(current.get().id())) return;
        NativeValueEdit edit = beginNativeValueEdit(current);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            edit.finish().run();
            return;
        }
        minecraft.execute(() -> {
            if (!canUpdate(current)) {
                edit.finish().run();
                return;
            }
            try {
                // Check the actual open result on the game thread, not its optimistic queued result.
                open.accept(edit.commit(), edit.finish());
            } catch (RuntimeException failure) {
                edit.finish().run();
                throw failure;
            }
        });
    }

    /** A native screen destroys the MUI host; its one-shot edit belongs to the connection and entry. */
    private NativeValueEdit beginNativeValueEdit(AtomicReference<DataLibraryRepository.Entry> current) {
        DataLibraryRepository.Entry expected = current.get();
        String scope = UiStateScopes.current();
        AtomicBoolean retired = new AtomicBoolean();
        AtomicBoolean committed = new AtomicBoolean();
        Runnable scopeChanged = () -> retired.set(true);
        UiStateScopes.addChangeListener(scopeChanged);
        Runnable externalFinished = beginExternalEdit(current);
        Runnable finish = () -> {
            retired.set(true);
            UiStateScopes.removeChangeListener(scopeChanged);
            externalFinished.run();
        };
        Consumer<Object> commit = value -> {
            if (retired.get() || !Objects.equals(scope, UiStateScopes.current())
                    || !committed.compareAndSet(false, true) || !repository.hasLoadedSnapshot()) return;
            DataLibraryRepository.Entry existing = repository.findEntry(expected.id());
            if (existing == null || existing.type() != expected.type() || DataLibraryEntryValues.equivalent(expected, value)) return;
            DataLibraryRepository.Entry updated = DataLibraryEntryValues.replace(expected, value);
            // Never retarget a newer editor. The repository validates this captured snapshot by CAS.
            if (current.get() == expected) current.set(updated);
            repository.update(expected, updated);
            changed();
        };
        return new NativeValueEdit(commit, finish);
    }

    private record NativeValueEdit(Consumer<Object> commit, Runnable finish) {}

    private boolean canUpdate(AtomicReference<DataLibraryRepository.Entry> current) {
        if (!active.getAsBoolean() || current == null || current.get() == null) return false;
        DataLibraryRepository.Entry existing = repository.findEntry(current.get().id());
        return existing != null && existing.type() == current.get().type();
    }

    View createExpandedEditor(DataLibraryRepository.Entry entry,
                                      AtomicReference<DataLibraryRepository.Entry> current) {
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(px(UiTableStyle.ROW_NUMBER_WIDTH + UiTableStyle.SELECTION_COLUMN_WIDTH + 6),
                px(12), px(16), px(16));

        ExpandedValueEditor editor = createExpandedValueEditor(entry, current);
        int height = expandedValueHeightDp(entry.type());
        TextView key = label(getContext(), tr("geometry_node.port.data_library_value"), EDITOR_TEXT_SIZE_DP, MUTED);
        key.setSingleLine(true);
        key.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
        key.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LayoutParams valueParams = entry.type() == PortType.BOOLEAN
                ? fixed(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP)
                : hasPreview(entry.type()) ? fixed(CARD_PREVIEW_SIZE_DP, CARD_PREVIEW_SIZE_DP)
                : new LayoutParams(0, px(height), 1);
        content.addView(new UiPropertyRow(getContext(), key, editor.value(), editor.action(),
                        new UiPropertyRow.Layout(66, height, 6, EDIT_ACTION_SIZE_DP, 16), valueParams),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return content;
    }

    private record ExpandedValueEditor(View value, View action) {
        private ExpandedValueEditor(View value) { this(value, null); }
    }

    private ExpandedValueEditor createExpandedValueEditor(DataLibraryRepository.Entry entry,
                                           AtomicReference<DataLibraryRepository.Entry> current) {
        if (entry.type() == PortType.ENTITY || entry.type() == PortType.ENTITY_TEMPLATE) {
            EntityTemplatePreviewView preview = createEntityPreview(entry, current);
            if (entry.value() instanceof EntityTemplateValue template) preview.setDisplayTemplate(template);
            else if (entry.value() instanceof UUID entityId) preview.setDisplayReference(entityId, "");
            UiIconButton edit = editButton(tr(entry.type() == PortType.ENTITY
                    ? "geometry_node.data_library.choose_entity" : "geometry_node.ui.edit_template"));
            edit.setOnClickListener(v -> editEntityValue(entry, current));
            return new ExpandedValueEditor(centeredPreview(preview), edit);
        } else if (entry.type() == PortType.ITEM_STACK) {
            ItemSlotView preview = new ItemSlotView(getContext());
            preview.setDisplayStack(entry.value() instanceof ItemStack stack ? stack : ItemStack.EMPTY);
            preview.setOpenEditorOnClick(false);
            preview.setDisplayPasteAction(json -> pasteItemStack(current, json));
            UiIconButton edit = editButton(tr("geometry_node.node.pick_item_stack"));
            edit.setOnClickListener(v -> editItemStackValue(current));
            return new ExpandedValueEditor(centeredPreview(preview), edit);
        } else if (entry.type() == PortType.XYZ) {
            return new ExpandedValueEditor(createXyzValueEditor(current));
        } else if (entry.type() == PortType.AREA) {
            return new ExpandedValueEditor(valueLabel(areaSummary(entry.value())));
        } else if (isSummaryType(entry.type())) {
            return new ExpandedValueEditor(valueLabel(valueSummary(entry)));
        } else if (isInlineValueType(entry.type())) {
            return switch (entry.type()) {
                case BOOLEAN -> new ExpandedValueEditor(createBooleanValueEditor(entry, current));
                case STRING -> new ExpandedValueEditor(createPlainTextValueEditor(current));
                case PATH -> createPathValueEditor(current);
                case RICH_TEXT -> createRichTextValueEditor(current);
                case ITEM, BLOCK_STATE -> createRegistryValueEditor(entry.type(), current);
                default -> new ExpandedValueEditor(createValueEditor(entry, current));
            };
        }
        return new ExpandedValueEditor(createValueEditor(entry, current));
    }

    static int expandedValueHeightDp(PortType type) {
        if (hasPreview(type)) return CARD_PREVIEW_SIZE_DP;
        if (type == PortType.XYZ) return 56;
        if (isInlineValueType(type) || isSummaryType(type) || type == PortType.AREA) return 28;
        return 54;
    }

    private static boolean hasPreview(PortType type) {
        return type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE || type == PortType.ITEM_STACK;
    }

    private TextView valueLabel(String text) {
        TextView summary = label(getContext(), text, EDITOR_TEXT_SIZE_DP, TEXT);
        summary.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        summary.setSingleLine(true);
        summary.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
        return summary;
    }

    static String compactValueSummary(DataLibraryRepository.Entry entry) {
        if (entry.value() == null) return "";
        if (entry.type() == PortType.AREA) return areaSummary(entry.value());
        if (isSummaryType(entry.type())) return valueSummary(entry);
        if (entry.type() == PortType.ITEM_STACK && entry.value() instanceof ItemStack stack) {
            return stack.isEmpty() ? "" : stack.getHoverName().getString() + " x" + stack.getCount();
        }
        if (entry.type() == PortType.ENTITY || entry.type() == PortType.ENTITY_TEMPLATE) {
            return tr("geometry_node.data_library.preview_available");
        }
        String value = String.valueOf(entry.value());
        return value.length() <= 42 ? value : value.substring(0, 39) + "...";
    }

    private EditText createValueEditor(DataLibraryRepository.Entry entry,
                                       AtomicReference<DataLibraryRepository.Entry> current) {
        EditText valueEditor = styledValueEditor();
        valueEditor.setText(serializedValue(entry));
        commitEditedTextOnBlur(valueEditor, json -> commitValue(current, json));
        return valueEditor;
    }

    private EditText createPlainTextValueEditor(AtomicReference<DataLibraryRepository.Entry> current) {
        EditText editor = styledValueEditor();
        Object value = current.get().value();
        editor.setText(value == null ? "" : String.valueOf(value));
        commitEditedTextOnBlur(editor, text -> updateValue(current, text));
        return editor;
    }

    private ExpandedValueEditor createPathValueEditor(AtomicReference<DataLibraryRepository.Entry> current) {
        String path = current.get().value() instanceof String value ? value : "";
        TextView summary = valueLabel(path);
        UiIconButton button = editButton(tr("geometry_node.data_library.choose_path"));
        button.setOnClickListener(v -> {
            if (!canUpdate(current) || isEditing(current.get().id())) return;
            Runnable finished = beginExternalEdit(current);
            try {
                FilePickerDialog picker = FilePickerDialog.showPath(button,
                        current.get().value() instanceof String value ? value : "", selectedPath -> {
                    updateValue(current, selectedPath);
                    summary.setText(selectedPath);
                });
                finishOnDetach(picker, finished);
            } catch (RuntimeException failure) {
                finished.run();
                throw failure;
            }
        });
        return new ExpandedValueEditor(summary, button);
    }

    private ExpandedValueEditor createRichTextValueEditor(AtomicReference<DataLibraryRepository.Entry> current) {
        TextView summary = valueLabel(RichTextValue.from(current.get().value()).plain());
        UiIconButton button = editButton(tr("geometry_node.graph_properties.quest.edit_rich_text"));
        button.setOnClickListener(v -> {
            if (!canUpdate(current) || isEditing(current.get().id())) return;
            Runnable finished = beginExternalEdit(current);
            try {
                ExpandedTextInputOverlay.showRichText(getContext(), button,
                        RichTextValue.from(current.get().value()), value -> {
                            updateValue(current, value);
                            summary.setText(value.plain());
                        }, finished);
            } catch (RuntimeException failure) {
                finished.run();
                throw failure;
            }
        });
        return new ExpandedValueEditor(summary, button);
    }

    private ExpandedValueEditor createRegistryValueEditor(PortType type,
                                           AtomicReference<DataLibraryRepository.Entry> current) {
        TextView summary = valueLabel(registryValueLabel(type, current.get().value()));
        UiIconButton button = editButton(tr(type == PortType.ITEM
                ? "geometry_node.data_library.choose_item"
                : "geometry_node.data_library.choose_block"));
        button.setOnClickListener(v -> {
            if (canUpdate(current) && !isEditing(current.get().id()))
                showRegistryValueMenu(button, summary, type, current);
        });
        return new ExpandedValueEditor(summary, button);
    }

    private void showRegistryValueMenu(View anchor, TextView summary, PortType type,
                                       AtomicReference<DataLibraryRepository.Entry> current) {
        ViewGroup host = overlayHost();
        if (host == null) return;
        List<String> options = type == PortType.ITEM
                ? RegistryDataManager.getAllItems()
                : RegistryDataManager.getAllBlocks();
        UiSearchableMenu menu = new UiSearchableMenu(
                getContext(), tr(type == PortType.ITEM
                        ? "geometry_node.data_library.choose_item"
                        : "geometry_node.data_library.choose_block"),
                options, Map.of(), selectedId -> {
                    Object selectedValue = registryValue(type, selectedId);
                    if (selectedValue == null) return;
                    updateValue(current, selectedValue);
                    summary.setText(selectedId);
                });
        Runnable finished = beginExternalEdit(current);
        finishOnDetach(menu, finished);
        try {
            menu.showAt(anchor, host);
        } catch (RuntimeException failure) {
            finished.run();
            throw failure;
        }
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
        return "";
    }

    private LinearLayout createXyzValueEditor(AtomicReference<DataLibraryRepository.Entry> current) {
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
            commitEditedTextOnBlur(editor, text -> commitXyzComponent(current, component, text));
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            if (index > 0) params.topMargin = px(2);
            inputs.addView(editor, params);
        }
        return inputs;
    }

    private EditText styledValueEditor() {
        EditText editor = new UiTextInput(getContext(), new UiTextInput.Style(
                EDITOR_TEXT_SIZE_DP, 5, 2, 1, MUTED, MUTED, INPUT_BG, BORDER));
        editor.setSingleLine(true);
        editor.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return editor;
    }

    private void commitEditedTextOnBlur(EditText editor, Consumer<String> commit) {
        String[] baseline = {editor.getText().toString()};
        if (editor instanceof UiTextInput input) {
            input.setEditActions(editor::clearFocus, () -> {
                editor.setText(baseline[0]);
                editor.clearFocus();
            });
        }
        editor.setOnFocusChangeListener((view, focused) -> {
            if (focused) return;
            String draft = editor.getText().toString();
            try {
                // A repository refresh must not turn an untouched field into a stale value write.
                if (!Objects.equals(baseline[0], draft)) {
                    commit.accept(draft);
                    baseline[0] = draft;
                }
            } finally {
                changed();
            }
        });
    }

    private void commitXyzComponent(AtomicReference<DataLibraryRepository.Entry> current,
                                    int component, String text) {
        if (!canUpdate(current)) return;
        try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) return;
            List<Double> values = new ArrayList<>(xyzComponents(current.get().value()));
            values.set(component, value);
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
        if (value == Math.rint(value) && value >= -0x1p63 && value < 0x1p63) return Long.toString((long) value);
        // The numeric input filter rejects exponent markers, so keep large/small values in plain decimal.
        return Double.isFinite(value) ? java.math.BigDecimal.valueOf(value).toPlainString() : Double.toString(value);
    }

    private static String valueSummary(DataLibraryRepository.Entry entry) {
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

    private View createBooleanValueEditor(DataLibraryRepository.Entry entry,
                                          AtomicReference<DataLibraryRepository.Entry> current) {
        UiCheckBox valueCheckBox = new UiCheckBox(getContext());
        valueCheckBox.setContentDescription(tr("geometry_node.data_library.edit_value"));
        valueCheckBox.setChecked(Boolean.TRUE.equals(entry.value()));
        valueCheckBox.setOnCheckedChangeListener((button, checked) -> updateValue(current, checked));
        return valueCheckBox;
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
            DataLibraryRepository.Entry entry,
            AtomicReference<DataLibraryRepository.Entry> current
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
            AtomicReference<DataLibraryRepository.Entry> current
    ) {
        KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
        if (copy != null && UiInputBindings.matches(copy, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Object value = current.get().value();
                entityReferenceClipboard = value instanceof UUID entityId ? entityId : null;
                hasEntityReferenceClipboard = true;
            }
            return true;
        }
        KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
        if (paste != null && UiInputBindings.matches(paste, event)) {
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
                -1, -1, Gravity.CENTER));
        if (preview instanceof com.mine.geometry_node.client.ui.components.integration.nativepreview.ViewportNativePreviewView nativePreview)
            nativePreview.setFullVisibilityAnchor(host);
        return host;
    }

    private void editItemStackValue(AtomicReference<DataLibraryRepository.Entry> current) {
        AtomicBoolean picked = new AtomicBoolean();
        openNativePicker(current, (commit, finished) -> {
            boolean opened = VanillaInventoryPicker.openItem(stack -> {
                picked.set(true);
                commit.accept(stack == null || stack.isEmpty() ? null : stack.copy());
            }, () -> {
                try { if (!picked.get()) refresh(); } finally { finished.run(); }
            });
            if (!opened) finished.run();
        });
    }

    private void pasteItemStack(AtomicReference<DataLibraryRepository.Entry> current, String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
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

    void updateValue(AtomicReference<DataLibraryRepository.Entry> current, Object value) {
        if (!canUpdate(current)) return;
        DataLibraryRepository.Entry latest = current.get();
        if (DataLibraryEntryValues.equivalent(latest, value)) return;
        DataLibraryRepository.Entry updated = DataLibraryEntryValues.replace(latest, value);
        current.set(updated);
        repository.update(latest, updated);
        changed();
    }

    private void editEntityValue(DataLibraryRepository.Entry entry,
                                 AtomicReference<DataLibraryRepository.Entry> current) {
        AtomicBoolean picked = new AtomicBoolean();
        openNativePicker(current, (commit, finished) -> {
            Runnable refreshIfCancelled = () -> {
                try { if (!picked.get()) refresh(); } finally { finished.run(); }
            };
            boolean opened = false;
            if (entry.type() == PortType.ENTITY) {
                opened = DataLibraryEntityPickerController.open((reference, preview) -> {
                    picked.set(true);
                    commit.accept(reference);
                }, refreshIfCancelled);
            } else if (entry.type() == PortType.ENTITY_TEMPLATE) {
                opened = EntityTemplatePickerController.open(template -> {
                    picked.set(true);
                    commit.accept(template);
                }, refreshIfCancelled);
            }
            if (!opened) finished.run();
        });
    }

    private String serializedValue(DataLibraryRepository.Entry entry) {
        return DataLibraryEntryValues.serialize(entry);
    }

    private void commitValue(AtomicReference<DataLibraryRepository.Entry> current, String json) {
        if (!canUpdate(current)) return;
        if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) return;
        try {
            DataLibraryRepository.Entry entry = current.get();
            var registries = Minecraft.getInstance().level.registryAccess();
            Object value = DataLibraryValueCodec.decode(entry.type(), JsonParser.parseString(json),
                    registries);
            if (DataLibraryValueCodec.encode(entry.type(), entry.value(), registries)
                    .equals(DataLibraryValueCodec.encode(entry.type(), value, registries))) return;
            DataLibraryRepository.Entry updated = new DataLibraryRepository.Entry(
                    entry.id(), entry.parentId(), entry.type(), entry.key(), value, entry.tags(), entry.data());
            current.set(updated);
            repository.update(entry, updated);
        } catch (RuntimeException ignored) {
            // Keep the previous valid value; malformed text never corrupts the library file.
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private UiIconButton editButton(String description) {
        UiIconButton button = new UiIconButton(getContext(),
                new SvgIconView(getContext(), SvgIconView.Icon.MORE_HORIZONTAL, TEXT));
        button.setContentDescription(description);
        return button;
    }

    private static TextView label(Context context, String text, float size, int color) {
        return UIUtils.createLockedTextView(context, text, size, color);
    }

    private static LayoutParams fixed(int widthDp, int heightDp) {
        return new LayoutParams(px(widthDp), px(heightDp));
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }

}
