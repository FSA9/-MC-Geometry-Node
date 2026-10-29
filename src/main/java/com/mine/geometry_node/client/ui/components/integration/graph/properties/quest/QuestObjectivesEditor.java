package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.integration.picker.EntityTemplatePickerController;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.QuestHintView;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.engine.system.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.singleLineInput;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertyNumbers.format;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertyNumbers.parseNonNegativeDouble;

public final class QuestObjectivesEditor extends LinearLayout {
    private static final int COLOR_INPUT = UiThemes.EDITOR.control().input();
    private static final int COLOR_BORDER = UiThemes.EDITOR.control().inputBorder();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().muted();

    private final LinearLayout rows;
    private final QuestEntryUi.SectionHeader header;
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

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        header = new QuestEntryUi.SectionHeader(context,
                "geometry_node.graph_properties.quest.objectives",
                "geometry_node.graph_properties.quest.objective_add", rows, this::addObjective);
        header.setCount(0);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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
        for (int i = 0; i < editors.size(); i++) {
            ObjectiveRow editor = editors.get(i);
            int number = i + 1;
            editor.numberLabel.setText(number < 10 ? "0" + number : Integer.toString(number));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UIUtils.dp2pxInt(10);
            rows.addView(editor.root, lp);
        }
        header.setCount(editors.size());
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
            QuestEntryUi.TextEdit textEdit
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
        private final TextView numberLabel;
        private final LinearLayout details;
        private final View detailsDivider;
        private final UiIconButton expandButton;
        private final SvgIconView expandIcon;
        private final EditText contentInput;
        private final UiCheckBox quantityToggle;
        private final UiCheckBox counterToggle;
        private final EditText counterKeyInput;
        private final EditText targetInput;
        private final QuestHintSelector hintSelector;
        private final QuestHintView hintPreview;

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
            LinearLayout contentRow = new LinearLayout(getContext());
            contentRow.setOrientation(HORIZONTAL);
            contentRow.setGravity(Gravity.CENTER_VERTICAL);
            details = new LinearLayout(getContext());
            details.setOrientation(VERTICAL);
            QuestEntryUi.decorateCard(root, contentRow, details);
            contentRow.addView(QuestEntryUi.reorderHandle(getContext(), rows, root,
                    delta -> moveRow(this, delta)), QuestEntryUi.handleParams());
            numberLabel = QuestEntryUi.entryNumber(getContext());
            contentRow.addView(numberLabel, new LayoutParams(UIUtils.dp2pxInt(22), UIUtils.dp2pxInt(28)));
            expandIcon = new SvgIconView(getContext(),
                    SvgIconView.Icon.forExpandedState(expanded), COLOR_TEXT);
            expandButton = new UiIconButton(getContext(), expandIcon, QuestEntryUi.disclosureStyle());
            expandButton.setOnClickListener(v -> toggleExpanded());
            contentInput = singleLineInput(getContext(), content.plain(),
                    tr("geometry_node.graph_properties.quest.objective_placeholder"),
                    COLOR_TEXT, COLOR_MUTED, 0, 0);
            contentInput.setBackground(null);
            UIUtils.setLockedTextSize(contentInput, 12);
            contentInput.setPadding(0, 0, 0, 0);
            contentInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            contentRow.addView(contentInput, new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
            contentRow.addView(expandButton, GraphPropertiesUi.actionParams());
            UiIconButton rich = QuestEntryUi.action(getContext(), SvgIconView.Icon.MORE_HORIZONTAL,
                    "geometry_node.graph_properties.quest.edit_rich_text");
            rich.setOnClickListener(v -> openRichEditor(rich));
            contentRow.addView(rich, GraphPropertiesUi.actionParams());
            UiIconButton remove = QuestEntryUi.action(getContext(), SvgIconView.Icon.CLOSE,
                    "geometry_node.graph_properties.quest.objective_remove");
            remove.setOnClickListener(v -> removeRow(this));
            contentRow.addView(remove, GraphPropertiesUi.actionParams());
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(44)));
            detailsDivider = new View(getContext());
            detailsDivider.setBackground(solid(UiThemes.EDITOR.border().subtle()));
            root.addView(detailsDivider, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
            root.addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout quantityRow = QuestEntryUi.inlineRow(getContext());
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
            targetInput.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            targetInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            quantityRow.addView(targetInput, QuestEntryUi.inlineFieldParams());

            LinearLayout counterRow = QuestEntryUi.inlineRow(getContext());
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
            counterRow.addView(counterKeyInput, QuestEntryUi.inlineFieldParams());
            QuestEntryUi.addQuantityAndCounter(details, quantityRow, counterRow);

            LinearLayout hintRow = QuestEntryUi.inlineRow(getContext());
            TextView displayLabel = label(getContext(),
                    tr("geometry_node.graph_properties.quest.objective_display"), 10.5f, UiThemes.EDITOR.text().muted());
            hintRow.addView(displayLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(32)));
            hintSelector = new QuestHintSelector(getContext(), true, selected -> {
                if (hintType == selected) return;
                hintType = selected;
                hintValue = "";
                updateHintUi();
                changed();
            });
            hintRow.addView(hintSelector, QuestEntryUi.inlineFieldParams());
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickHint);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            hintPreview.setEntityDisplayPasteAction(this::pasteEntity);
            hintRow.addView(hintPreview, new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32)));
            details.addView(hintRow, QuestEntryUi.detailParams());

            updateQuantityUi();
            updateCounterUi();
            updateHintUi();
            updateExpandedUi();
        }

        private QuestObjectiveDefinition definition() {
            RichTextValue updatedContent = QuestEntryUi.afterInlineEdit(content, contentInput.getText());
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
            return new FocusedEdit(entryId, field, QuestEntryUi.TextEdit.capture(input));
        }

        private void restoreFocusedEdit(FocusedEdit focusedEdit) {
            EditText input = switch (focusedEdit.field()) {
                case CONTENT -> contentInput;
                case COUNTER_KEY -> counterKeyInput;
                case TARGET -> targetInput;
            };
            focusedEdit.textEdit().restore(input);
        }

        private void openRichEditor(View anchor) {
            if (!editors.contains(this)) return;
            content = QuestEntryUi.afterInlineEdit(content, contentInput.getText());
            ExpandedTextInputOverlay.showRichText(getContext(), anchor, content, value -> {
                if (!editors.contains(this)) return;
                content = value;
                contentInput.setText(value.plain());
                changed();
            });
        }

        private void pickItem() {
            QuestHintSelection.pickItem(captureTarget.get(), entryId, QuestHintSelection.EntryKind.OBJECTIVE, () -> {
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
            if (!editors.contains(this)) return;
            hintType = QuestHintType.ITEM_STACK;
            hintValue = itemJson != null ? itemJson : "";
            updateHintUi();
            changed();
        }

        private void pasteEntity(EntityTemplateValue template) {
            if (!editors.contains(this)) return;
            if (template == null || template.isEmpty()) return;
            hintType = QuestHintType.ENTITY;
            hintValue = template.toJsonString();
            updateHintUi();
            changed();
        }

        private void updateQuantityUi() {
            quantityToggle.setChecked(quantityEnabled);
            targetInput.setEnabled(quantityEnabled);
            targetInput.setAlpha(quantityEnabled ? 1.0f : UiMetrics.DISABLED_ALPHA);
        }

        private void updateCounterUi() {
            counterToggle.setChecked(counterEnabled);
            counterKeyInput.setVisibility(View.VISIBLE);
            counterKeyInput.setEnabled(counterEnabled);
            counterKeyInput.setAlpha(counterEnabled ? 1.0f : UiMetrics.DISABLED_ALPHA);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintSelector.setSelected(hintType);
            hintPreview.setVisibility(hasHint ? View.VISIBLE : View.GONE);
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
            details.setVisibility(visibility);
            detailsDivider.setVisibility(visibility);
            expandIcon.setIcon(SvgIconView.Icon.forExpandedState(expanded));
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.objective_collapse"
                    : "geometry_node.graph_properties.quest.objective_expand"));
        }
    }
}
