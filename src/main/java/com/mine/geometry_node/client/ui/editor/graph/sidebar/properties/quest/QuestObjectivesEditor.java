package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.client.runtime.quest.ui.QuestHintView;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.integration.picker.EntityTemplatePickerController;
import com.mine.geometry_node.client.integration.picker.VanillaInventoryPicker;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphPropertiesTarget;
import java.util.function.Supplier;
import com.mine.geometry_node.core.engine.system.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.singleLineInput;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertyNumbers.format;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertyNumbers.parseNonNegativeDouble;

public final class QuestObjectivesEditor extends LinearLayout {
    private static final int COLOR_ROW = UiTheme.Surface.PANEL;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_BORDER = UiTheme.Control.INPUT_BORDER;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF888888;

    private final LinearLayout rows;
    private final Runnable onChanged;
    private final Supplier<GraphPropertiesTarget> captureTarget;
    private final List<ObjectiveRow> editors = new ArrayList<>();
    private final Set<String> collapsedEntryIds = new HashSet<>();
    private boolean updating;

    public QuestObjectivesEditor(Context context, Runnable onChanged, Supplier<GraphPropertiesTarget> captureTarget) {
        super(context);
        this.onChanged = onChanged;
        this.captureTarget = captureTarget;
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(context, tr("geometry_node.graph_properties.quest.objectives"), 10.5f, 0xFFA8A8A8),
                new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        UiIconButton add = GraphPropertiesUi.actionIcon(context, SvgIconView.Icon.PLUS,
                "geometry_node.graph_properties.quest.objective_add");
        add.setOnClickListener(v -> addObjective());
        header.addView(add, GraphPropertiesUi.actionParams());
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setObjectives(List<QuestObjectiveDefinition> objectives) {
        updating = true;
        rememberCollapsedRows();
        rows.removeAllViews();
        editors.clear();
        if (objectives != null) {
            for (QuestObjectiveDefinition objective : objectives) addRow(objective);
        }
        rebuildRows();
        updating = false;
    }

    public void setObjectivesPreservingFocusedEdit(List<QuestObjectiveDefinition> objectives) {
        FocusedEdit focusedEdit = captureFocusedEdit();
        setObjectives(objectives);
        restoreFocusedEdit(focusedEdit);
    }

    public List<QuestObjectiveDefinition> objectives() {
        List<QuestObjectiveDefinition> result = new ArrayList<>(editors.size());
        for (ObjectiveRow editor : editors) result.add(editor.definition());
        return List.copyOf(result);
    }

    private void addObjective() {
        addRow(QuestObjectiveDefinition.empty());
        rebuildRows();
        changed();
    }

    private void addRow(QuestObjectiveDefinition definition) {
        editors.add(new ObjectiveRow(definition));
    }

    private void removeRow(ObjectiveRow row) {
        if (!editors.remove(row)) return;
        collapsedEntryIds.remove(row.entryId);
        rebuildRows();
        changed();
    }

    private void moveRow(ObjectiveRow row, int delta) {
        int oldIndex = editors.indexOf(row);
        int newIndex = oldIndex + delta;
        if (oldIndex < 0 || newIndex < 0 || newIndex >= editors.size()) return;
        editors.remove(oldIndex);
        editors.add(newIndex, row);
        rebuildRows();
        changed();
    }

    private void rebuildRows() {
        rows.removeAllViews();
        for (ObjectiveRow editor : editors) {
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UIUtils.dp2pxInt(6);
            rows.addView(editor.root, lp);
        }
    }

    private void changed() {
        if (!updating && onChanged != null) onChanged.run();
    }

    private void rememberCollapsedRows() {
        for (ObjectiveRow editor : editors) {
            if (editor.expanded) {
                collapsedEntryIds.remove(editor.entryId);
            } else {
                collapsedEntryIds.add(editor.entryId);
            }
        }
    }

    private FocusedEdit captureFocusedEdit() {
        for (ObjectiveRow editor : editors) {
            FocusedEdit focusedEdit = editor.captureFocusedEdit();
            if (focusedEdit != null) return focusedEdit;
        }
        return null;
    }

    private void restoreFocusedEdit(FocusedEdit focusedEdit) {
        if (focusedEdit == null) return;
        for (ObjectiveRow editor : editors) {
            if (editor.entryId.equals(focusedEdit.entryId())) {
                editor.restoreFocusedEdit(focusedEdit);
                return;
            }
        }
    }

    private enum EditField {
        CONTENT, COUNTER_KEY, TARGET
    }

    private record FocusedEdit(
            String entryId,
            EditField field,
            String text,
            int selectionStart,
            int selectionEnd
    ) {}

    private final class ObjectiveRow {
        private final String entryId;
        private RichTextValue content;
        private boolean quantityEnabled;
        private boolean counterEnabled;
        private QuestHintType hintType;
        private String hintValue;
        private boolean expanded;

        private final LinearLayout root;
        private final FrameLayout expandButton;
        private final SvgIconView expandIcon;
        private final EditText contentInput;
        private final UiCheckBox quantityToggle;
        private final UiCheckBox counterToggle;
        private final EditText counterKeyInput;
        private final EditText targetInput;
        private final QuestHintSelector hintSelector;
        private final QuestHintView hintPreview;
        private final LinearLayout quantityRow;
        private final LinearLayout counterRow;
        private final LinearLayout hintRow;

        private ObjectiveRow(QuestObjectiveDefinition definition) {
            entryId = definition.entryId();
            content = definition.content();
            quantityEnabled = definition.quantityEnabled();
            counterEnabled = definition.counterEnabled();
            hintType = definition.hintType();
            hintValue = definition.hintValue();
            expanded = !collapsedEntryIds.contains(entryId);

            root = new LinearLayout(getContext());
            root.setOrientation(VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(6),
                    UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));
            root.setBackground(rect(COLOR_ROW, 5.0f, 1, 0xFF3B3B3B));

            LinearLayout contentRow = new LinearLayout(getContext());
            contentRow.setOrientation(HORIZONTAL);
            contentRow.setGravity(Gravity.CENTER_VERTICAL);
            expandIcon = new SvgIconView(getContext(),
                    SvgIconView.Icon.forExpandedState(expanded), COLOR_TEXT);
            expandButton = new UiIconButton(getContext(), expandIcon, GraphPropertiesUi.actionStyle(14));
            expandButton.setOnClickListener(v -> toggleExpanded());
            LayoutParams expandLp = new LayoutParams(UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP),
                    UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP));
            expandLp.rightMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(expandButton, expandLp);
            contentInput = singleLineInput(getContext(), content.plain(),
                    tr("geometry_node.graph_properties.quest.objective_placeholder"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            contentInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            contentRow.addView(contentInput, new LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            UiIconButton rich = GraphPropertiesUi.actionIcon(getContext(), SvgIconView.Icon.MORE_HORIZONTAL,
                    "geometry_node.graph_properties.quest.edit_rich_text");
            rich.setOnClickListener(v -> openRichEditor(rich));
            contentRow.addView(rich, GraphPropertiesUi.actionParams());
            LinearLayout order = GraphPropertiesUi.orderButtons(getContext(),
                    () -> moveRow(this, -1), () -> moveRow(this, 1),
                    "geometry_node.graph_properties.quest.objective_move_up",
                    "geometry_node.graph_properties.quest.objective_move_down");
            contentRow.addView(order, GraphPropertiesUi.actionParams());
            UiIconButton remove = GraphPropertiesUi.actionIcon(getContext(), SvgIconView.Icon.CLOSE,
                    "geometry_node.graph_properties.quest.objective_remove");
            remove.setOnClickListener(v -> removeRow(this));
            contentRow.addView(remove, GraphPropertiesUi.actionParams());
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            quantityRow = new LinearLayout(getContext());
            quantityRow.setOrientation(HORIZONTAL);
            quantityRow.setGravity(Gravity.CENTER_VERTICAL);
            quantityToggle = new UiCheckBox(getContext());
            quantityToggle.setTooltipText(tr("geometry_node.graph_properties.quest.quantity_enabled"));
            quantityToggle.setOnCheckedChangeListener((v, checked) -> {
                quantityEnabled = checked;
                if (!quantityEnabled) counterEnabled = false;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            QuestEntryUi.addOptionLabel(getContext(), quantityRow, quantityToggle, "geometry_node.graph_properties.quest.quantity_value");
            targetInput = singleLineInput(getContext(), format(definition.targetValue()),
                    tr("geometry_node.graph_properties.quest.quantity_value"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            targetInput.setGravity(Gravity.CENTER);
            targetInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams targetLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            targetLp.leftMargin = UIUtils.dp2pxInt(4);
            quantityRow.addView(targetInput, targetLp);
            root.addView(quantityRow, QuestEntryUi.detailParams());

            counterRow = new LinearLayout(getContext());
            counterRow.setOrientation(HORIZONTAL);
            counterRow.setGravity(Gravity.CENTER_VERTICAL);
            counterToggle = new UiCheckBox(getContext());
            counterToggle.setTooltipText(tr("geometry_node.graph_properties.quest.counter_enabled"));
            counterToggle.setOnCheckedChangeListener((v, checked) -> {
                counterEnabled = checked;
                if (counterEnabled) quantityEnabled = true;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            QuestEntryUi.addOptionLabel(getContext(), counterRow, counterToggle, "geometry_node.graph_properties.quest.counter_label");
            counterKeyInput = singleLineInput(getContext(), definition.counterKey(),
                    tr("geometry_node.graph_properties.quest.counter_key"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            counterKeyInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams keyLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            keyLp.leftMargin = UIUtils.dp2pxInt(4);
            counterRow.addView(counterKeyInput, keyLp);
            root.addView(counterRow, QuestEntryUi.detailParams());

            hintRow = new LinearLayout(getContext());
            hintRow.setOrientation(HORIZONTAL);
            hintRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView displayLabel = label(getContext(),
                    tr("geometry_node.graph_properties.quest.objective_display"), 10.5f, UiTheme.Text.MUTED);
            hintRow.addView(displayLabel, new LayoutParams(UIUtils.dp2pxInt(78), UIUtils.dp2pxInt(28)));
            hintSelector = new QuestHintSelector(getContext(), true, selected -> {
                if (hintType == selected) return;
                hintType = selected;
                hintValue = "";
                updateHintUi();
                changed();
            });
            hintRow.addView(hintSelector, new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickHint);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            hintPreview.setEntityDisplayPasteAction(this::pasteEntity);
            LayoutParams previewLp = new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32));
            previewLp.leftMargin = UIUtils.dp2pxInt(4);
            hintRow.addView(hintPreview, previewLp);
            root.addView(hintRow, QuestEntryUi.detailParams());

            updateQuantityUi();
            updateCounterUi();
            updateHintUi();
            updateExpandedUi();
        }

        private QuestObjectiveDefinition definition() {
            RichTextValue updatedContent = contentInput.getText().toString().equals(content.plain())
                    ? content
                    : RichTextValue.plain(contentInput.getText().toString());
            return new QuestObjectiveDefinition(
                    entryId,
                    updatedContent,
                    counterEnabled,
                    counterKeyInput.getText().toString(),
                    quantityEnabled,
                    parseNonNegativeDouble(targetInput.getText().toString(), 1.0),
                    hintType,
                    hintType == QuestHintType.NONE ? "" : hintValue);
        }

        private FocusedEdit captureFocusedEdit() {
            if (contentInput.hasFocus()) return focusedEdit(EditField.CONTENT, contentInput);
            if (counterKeyInput.hasFocus()) return focusedEdit(EditField.COUNTER_KEY, counterKeyInput);
            if (targetInput.hasFocus()) return focusedEdit(EditField.TARGET, targetInput);
            return null;
        }

        private FocusedEdit focusedEdit(EditField field, EditText input) {
            return new FocusedEdit(
                    entryId,
                    field,
                    input.getText().toString(),
                    input.getSelectionStart(),
                    input.getSelectionEnd());
        }

        private void restoreFocusedEdit(FocusedEdit focusedEdit) {
            EditText input = switch (focusedEdit.field()) {
                case CONTENT -> contentInput;
                case COUNTER_KEY -> counterKeyInput;
                case TARGET -> targetInput;
            };
            input.setText(focusedEdit.text());
            int length = focusedEdit.text().length();
            int start = Math.clamp(focusedEdit.selectionStart(), 0, length);
            int end = Math.clamp(focusedEdit.selectionEnd(), start, length);
            input.requestFocus();
            input.setSelection(start, end);
        }

        private void openRichEditor(View anchor) {
            content = contentInput.getText().toString().equals(content.plain())
                    ? content
                    : RichTextValue.plain(contentInput.getText().toString());
            ExpandedTextInputOverlay.showRichText(getContext(), anchor, content, value -> {
                content = value;
                contentInput.setText(value.plain());
                changed();
            });
        }

        private void pickItem() {
            var selection = QuestHintSelection.capture(captureTarget.get(), entryId, QuestHintSelection.EntryKind.OBJECTIVE);
            VanillaInventoryPicker.openItem(stack -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) return;
                selection.accept(QuestHintType.ITEM_STACK,
                        ItemCodecUtils.toJson(stack, minecraft.level.registryAccess()));
            }, () -> {
                if (hintPreview.isAttachedToWindow()) hintPreview.requestHintFocus();
            });
        }

        private void pickEntity() {
            var selection = QuestHintSelection.capture(captureTarget.get(), entryId, QuestHintSelection.EntryKind.OBJECTIVE);
            EntityTemplatePickerController.open(template ->
                    selection.accept(QuestHintType.ENTITY, template.toJsonString()), () -> {
                if (hintPreview.isAttachedToWindow()) hintPreview.requestHintFocus();
            });
        }

        private void pickHint() {
            if (hintType == QuestHintType.ENTITY) {
                pickEntity();
            } else if (hintType == QuestHintType.ITEM_STACK || hintType == QuestHintType.BLOCK) {
                pickItem();
            }
        }

        private void pasteItem(String itemJson) {
            hintType = QuestHintType.ITEM_STACK;
            hintValue = itemJson != null ? itemJson : "";
            updateHintUi();
            changed();
        }

        private void pasteEntity(EntityTemplateValue template) {
            if (template == null || template.isEmpty()) return;
            hintType = QuestHintType.ENTITY;
            hintValue = template.toJsonString();
            updateHintUi();
            changed();
        }

        private void updateQuantityUi() {
            quantityToggle.setChecked(quantityEnabled);
            targetInput.setEnabled(quantityEnabled);
            targetInput.setAlpha(quantityEnabled ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
        }

        private void updateCounterUi() {
            counterToggle.setChecked(counterEnabled);
            counterKeyInput.setEnabled(counterEnabled);
            counterKeyInput.setAlpha(counterEnabled ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintSelector.setSelected(hintType);
            hintPreview.setVisibility(hasHint ? View.VISIBLE : View.INVISIBLE);
            updateHintPreview();
        }

        private void updateHintPreview() {
            hintPreview.setHint(hintType, hintValue);
        }

        private void toggleExpanded() {
            expanded = !expanded;
            if (expanded) {
                collapsedEntryIds.remove(entryId);
            } else {
                collapsedEntryIds.add(entryId);
            }
            updateExpandedUi();
        }

        private void updateExpandedUi() {
            int visibility = expanded ? View.VISIBLE : View.GONE;
            quantityRow.setVisibility(visibility);
            counterRow.setVisibility(visibility);
            hintRow.setVisibility(visibility);
            expandIcon.setIcon(SvgIconView.Icon.forExpandedState(expanded));
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.objective_collapse"
                    : "geometry_node.graph_properties.quest.objective_expand"));
        }
    }


}
