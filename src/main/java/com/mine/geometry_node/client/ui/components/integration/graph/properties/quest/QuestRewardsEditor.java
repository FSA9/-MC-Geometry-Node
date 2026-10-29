package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

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
import com.mine.geometry_node.core.engine.system.quest.model.QuestRewardDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
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

public final class QuestRewardsEditor extends LinearLayout {
    private static final int COLOR_INPUT = UiThemes.EDITOR.control().input();
    private static final int COLOR_BORDER = UiThemes.EDITOR.control().inputBorder();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().muted();

    private final LinearLayout rows;
    private final QuestEntryUi.SectionHeader header;
    private final Runnable onChanged;
    private final Supplier<GraphPropertiesTarget> captureTarget;
    private final List<RewardRow> editors = new ArrayList<>();
    private final Set<String> collapsedEntryIds = new HashSet<>();
    private boolean updating;

    public QuestRewardsEditor(Context context, Runnable onChanged, Supplier<GraphPropertiesTarget> captureTarget) {
        super(context);
        this.onChanged = onChanged;
        this.captureTarget = captureTarget;
        setOrientation(VERTICAL);

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        header = new QuestEntryUi.SectionHeader(context,
                "geometry_node.graph_properties.quest.rewards",
                "geometry_node.graph_properties.quest.reward_add", rows, this::addReward);
        header.setCount(0);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setRewards(List<QuestRewardDefinition> rewards) {
        updating = true;
        rememberCollapsedRows();
        rows.removeAllViews();
        editors.clear();
        if (rewards != null) {
            for (QuestRewardDefinition reward : rewards) editors.add(new RewardRow(reward));
        }
        rebuildRows();
        updating = false;
    }

    public void setRewardsPreservingFocusedEdit(List<QuestRewardDefinition> rewards) {
        FocusedEdit focusedEdit = captureFocusedEdit();
        setRewards(rewards);
        restoreFocusedEdit(focusedEdit);
    }

    public List<QuestRewardDefinition> rewards() {
        List<QuestRewardDefinition> result = new ArrayList<>(editors.size());
        for (RewardRow editor : editors) result.add(editor.definition());
        return List.copyOf(result);
    }

    private void addReward() {
        editors.add(new RewardRow(QuestRewardDefinition.empty()));
        rebuildRows();
        changed();
    }

    private void removeRow(RewardRow row) {
        if (!editors.remove(row)) return;
        collapsedEntryIds.remove(row.entryId);
        rebuildRows();
        changed();
    }

    private void moveRow(RewardRow row, int delta) {
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
            RewardRow editor = editors.get(i);
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
        for (RewardRow editor : editors) {
            if (editor.expanded) {
                collapsedEntryIds.remove(editor.entryId);
            } else {
                collapsedEntryIds.add(editor.entryId);
            }
        }
    }

    private FocusedEdit captureFocusedEdit() {
        for (RewardRow editor : editors) {
            FocusedEdit focusedEdit = editor.captureFocusedEdit();
            if (focusedEdit != null) return focusedEdit;
        }
        return null;
    }

    private void restoreFocusedEdit(FocusedEdit focusedEdit) {
        if (focusedEdit == null) return;
        for (RewardRow editor : editors) {
            if (editor.entryId.equals(focusedEdit.entryId())) {
                editor.restoreFocusedEdit(focusedEdit);
                return;
            }
        }
    }

    private enum EditField {
        CONTENT, COUNTER_KEY, AMOUNT
    }

    private record FocusedEdit(
            String entryId,
            EditField field,
            QuestEntryUi.TextEdit textEdit
    ) {}

    private final class RewardRow {
        private final String entryId;
        private RichTextValue content;
        private boolean counterEnabled;
        private QuestHintType hintType;
        private String itemHintValue;
        private boolean expanded;

        private final LinearLayout root;
        private final TextView numberLabel;
        private final LinearLayout details;
        private final View detailsDivider;
        private final UiIconButton expandButton;
        private final SvgIconView expandIcon;
        private final EditText contentInput;
        private final UiCheckBox counterToggle;
        private final EditText counterKeyInput;
        private final EditText amountInput;
        private final QuestHintSelector hintSelector;
        private final QuestHintView hintPreview;

        private RewardRow(QuestRewardDefinition definition) {
            entryId = definition.entryId();
            content = definition.content();
            counterEnabled = definition.counterEnabled();
            hintType = definition.hintType();
            itemHintValue = definition.hintValue();
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
                    QuestEntryUi.disclosureIcon(expanded), COLOR_TEXT);
            expandButton = new UiIconButton(getContext(), expandIcon, QuestEntryUi.disclosureStyle());
            expandButton.setOnClickListener(v -> toggleExpanded());
            contentInput = singleLineInput(getContext(), content.plain(),
                    tr("geometry_node.graph_properties.quest.reward_placeholder"),
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
                    "geometry_node.graph_properties.quest.reward_remove");
            remove.setOnClickListener(v -> removeRow(this));
            contentRow.addView(remove, GraphPropertiesUi.actionParams());
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(44)));
            detailsDivider = new View(getContext());
            detailsDivider.setBackground(solid(UiThemes.EDITOR.border().subtle()));
            root.addView(detailsDivider, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
            root.addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout amountRow = QuestEntryUi.inlineRow(getContext());
            TextView amountLabel = label(getContext(), tr("geometry_node.graph_properties.quest.reward_amount"),
                    10.5f, UiThemes.EDITOR.text().muted());
            amountRow.addView(amountLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(32)));
            amountInput = singleLineInput(getContext(), format(definition.amount()),
                    tr("geometry_node.graph_properties.quest.reward_amount"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            amountInput.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            amountInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            amountRow.addView(amountInput, QuestEntryUi.inlineFieldParams());

            LinearLayout counterRow = QuestEntryUi.inlineRow(getContext());
            counterToggle = new UiCheckBox(getContext());
            counterToggle.setTooltipText(tr("geometry_node.graph_properties.quest.reward_counter_enabled"));
            counterToggle.setOnCheckedChangeListener((v, checked) -> {
                counterEnabled = checked;
                updateCounterUi();
                changed();
            });
            QuestEntryUi.addOptionLabel(getContext(), counterRow, counterToggle,
                    "geometry_node.graph_properties.quest.counter_label");
            counterKeyInput = singleLineInput(getContext(), definition.counterKey(),
                    tr("geometry_node.graph_properties.quest.counter_key"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            counterKeyInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            counterRow.addView(counterKeyInput, QuestEntryUi.inlineFieldParams());
            QuestEntryUi.addQuantityAndCounter(details, amountRow, counterRow);

            LinearLayout hintRow = QuestEntryUi.inlineRow(getContext());
            TextView displayLabel = label(getContext(),
                    tr("geometry_node.graph_properties.quest.objective_display"), 10.5f, UiThemes.EDITOR.text().muted());
            hintRow.addView(displayLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(32)));
            hintSelector = new QuestHintSelector(getContext(), false, selected -> {
                if (hintType == selected) return;
                hintType = selected;
                updateHintUi();
                changed();
            });
            hintRow.addView(hintSelector, QuestEntryUi.inlineFieldParams());
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickItem);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            hintRow.addView(hintPreview, new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32)));
            details.addView(hintRow, QuestEntryUi.detailParams());

            updateCounterUi();
            updateHintUi();
            updateExpandedUi();
        }

        private QuestRewardDefinition definition() {
            RichTextValue updatedContent = QuestEntryUi.afterInlineEdit(content, contentInput.getText());
            return new QuestRewardDefinition(
                    entryId,
                    updatedContent,
                    counterEnabled,
                    counterKeyInput.getText().toString(),
                    parseNonNegativeDouble(amountInput.getText().toString(), 1.0),
                    hintType,
                    hintType == QuestHintType.NONE ? "" : itemHintValue);
        }

        private FocusedEdit captureFocusedEdit() {
            if (contentInput.hasFocus()) return focusedEdit(EditField.CONTENT, contentInput);
            if (counterKeyInput.hasFocus()) return focusedEdit(EditField.COUNTER_KEY, counterKeyInput);
            if (amountInput.hasFocus()) return focusedEdit(EditField.AMOUNT, amountInput);
            return null;
        }

        private FocusedEdit focusedEdit(EditField field, EditText input) {
            return new FocusedEdit(entryId, field, QuestEntryUi.TextEdit.capture(input));
        }

        private void restoreFocusedEdit(FocusedEdit focusedEdit) {
            EditText input = switch (focusedEdit.field()) {
                case CONTENT -> contentInput;
                case COUNTER_KEY -> counterKeyInput;
                case AMOUNT -> amountInput;
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
            QuestHintSelection.pickItem(captureTarget.get(), entryId, QuestHintSelection.EntryKind.REWARD, () -> {
                if (hintPreview.isAttachedToWindow()) hintPreview.requestHintFocus();
            });
        }

        private void pasteItem(String itemJson) {
            if (!editors.contains(this)) return;
            hintType = QuestHintType.ITEM_STACK;
            itemHintValue = itemJson != null ? itemJson : "";
            updateHintUi();
            changed();
        }

        private void updateCounterUi() {
            counterToggle.setChecked(counterEnabled);
            counterKeyInput.setVisibility(View.VISIBLE);
            counterKeyInput.setEnabled(counterEnabled);
            counterKeyInput.setAlpha(counterEnabled ? 1.0f : UiMetrics.DISABLED_ALPHA);
            amountInput.setEnabled(!counterEnabled);
            amountInput.setAlpha(counterEnabled ? UiMetrics.DISABLED_ALPHA : 1.0f);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintSelector.setSelected(hintType);
            hintPreview.setVisibility(hasHint ? View.VISIBLE : View.GONE);
            updateHintPreview();
        }

        private void updateHintPreview() {
            hintPreview.setHint(hintType, itemHintValue);
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
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            detailsDivider.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandIcon.setIcon(QuestEntryUi.disclosureIcon(expanded));
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.reward_collapse"
                    : "geometry_node.graph_properties.quest.reward_expand"));
        }
    }
}
