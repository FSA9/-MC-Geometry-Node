package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.client.runtime.quest.ui.QuestHintView;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.editor.graph.picker.VanillaInventoryPicker;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphPropertiesTarget;
import java.util.function.Supplier;
import com.mine.geometry_node.core.engine.system.quest.model.QuestRewardDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.node.value.RichTextValue;
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

public final class QuestRewardsEditor extends LinearLayout {
    private static final int COLOR_ROW = UiTheme.Surface.PANEL;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_BORDER = UiTheme.Control.INPUT_BORDER;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF888888;

    private final LinearLayout rows;
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

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(context, tr("geometry_node.graph_properties.quest.rewards"), 10.5f, 0xFFA8A8A8),
                new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        UiIconButton add = GraphPropertiesUi.actionIcon(context, SvgIconView.Icon.PLUS,
                "geometry_node.graph_properties.quest.reward_add");
        add.setOnClickListener(v -> addReward());
        header.addView(add, GraphPropertiesUi.actionParams());
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
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
        for (RewardRow editor : editors) {
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UIUtils.dp2pxInt(6);
            rows.addView(editor.root, lp);
        }
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
            String text,
            int selectionStart,
            int selectionEnd
    ) {}

    private final class RewardRow {
        private final String entryId;
        private RichTextValue content;
        private boolean counterEnabled;
        private QuestHintType hintType;
        private String itemHintValue;
        private boolean expanded;

        private final LinearLayout root;
        private final FrameLayout expandButton;
        private final SvgIconView expandIcon;
        private final EditText contentInput;
        private final UiCheckBox counterToggle;
        private final EditText counterKeyInput;
        private final EditText amountInput;
        private final QuestHintSelector hintSelector;
        private final QuestHintView hintPreview;
        private final LinearLayout amountRow;
        private final LinearLayout counterRow;
        private final LinearLayout hintRow;

        private RewardRow(QuestRewardDefinition definition) {
            entryId = definition.entryId();
            content = definition.content();
            counterEnabled = definition.counterEnabled();
            hintType = definition.hintType();
            itemHintValue = definition.hintValue();
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
                    tr("geometry_node.graph_properties.quest.reward_placeholder"),
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
                    "geometry_node.graph_properties.quest.reward_move_up",
                    "geometry_node.graph_properties.quest.reward_move_down");
            contentRow.addView(order, GraphPropertiesUi.actionParams());
            UiIconButton remove = GraphPropertiesUi.actionIcon(getContext(), SvgIconView.Icon.CLOSE,
                    "geometry_node.graph_properties.quest.reward_remove");
            remove.setOnClickListener(v -> removeRow(this));
            contentRow.addView(remove, GraphPropertiesUi.actionParams());
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            amountRow = detailRow();
            TextView amountLabel = label(getContext(), tr("geometry_node.graph_properties.quest.reward_amount"),
                    10.5f, UiTheme.Text.MUTED);
            amountRow.addView(amountLabel, new LayoutParams(UIUtils.dp2pxInt(78), UIUtils.dp2pxInt(28)));
            amountInput = singleLineInput(getContext(), format(definition.amount()),
                    tr("geometry_node.graph_properties.quest.reward_amount"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            amountInput.setGravity(Gravity.CENTER);
            amountInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            amountRow.addView(amountInput, new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
            root.addView(amountRow, QuestEntryUi.detailParams());

            counterRow = detailRow();
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
            LayoutParams keyLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            keyLp.leftMargin = UIUtils.dp2pxInt(4);
            counterRow.addView(counterKeyInput, keyLp);
            root.addView(counterRow, QuestEntryUi.detailParams());

            hintRow = detailRow();
            TextView displayLabel = label(getContext(),
                    tr("geometry_node.graph_properties.quest.objective_display"), 10.5f, UiTheme.Text.MUTED);
            hintRow.addView(displayLabel, new LayoutParams(UIUtils.dp2pxInt(78), UIUtils.dp2pxInt(28)));
            hintSelector = new QuestHintSelector(getContext(), false, selected -> {
                if (hintType == selected) return;
                hintType = selected;
                updateHintUi();
                changed();
            });
            hintRow.addView(hintSelector, new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickItem);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            LayoutParams previewLp = new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32));
            previewLp.leftMargin = UIUtils.dp2pxInt(4);
            hintRow.addView(hintPreview, previewLp);
            root.addView(hintRow, QuestEntryUi.detailParams());

            updateCounterUi();
            updateHintUi();
            updateExpandedUi();
        }

        private QuestRewardDefinition definition() {
            RichTextValue updatedContent = contentInput.getText().toString().equals(content.plain())
                    ? content
                    : RichTextValue.plain(contentInput.getText().toString());
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
                case AMOUNT -> amountInput;
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
            var selection = QuestHintSelection.capture(captureTarget.get(), entryId, QuestHintSelection.EntryKind.REWARD);
            VanillaInventoryPicker.openItem(stack -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) return;
                selection.accept(QuestHintType.ITEM_STACK,
                        ItemCodecUtils.toJson(stack, minecraft.level.registryAccess()));
            }, () -> {
                if (hintPreview.isAttachedToWindow()) hintPreview.requestHintFocus();
            });
        }

        private void pasteItem(String itemJson) {
            hintType = QuestHintType.ITEM_STACK;
            itemHintValue = itemJson != null ? itemJson : "";
            updateHintUi();
            changed();
        }

        private void updateCounterUi() {
            counterToggle.setChecked(counterEnabled);
            counterKeyInput.setEnabled(counterEnabled);
            counterKeyInput.setAlpha(counterEnabled ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
            amountInput.setEnabled(!counterEnabled);
            amountInput.setAlpha(counterEnabled ? UiTheme.Metrics.DISABLED_ALPHA : 1.0f);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintSelector.setSelected(hintType);
            hintPreview.setVisibility(hasHint ? View.VISIBLE : View.INVISIBLE);
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
            amountRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
            counterRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
            hintRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandIcon.setIcon(SvgIconView.Icon.forExpandedState(expanded));
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.reward_collapse"
                    : "geometry_node.graph_properties.quest.reward_expand"));
        }
    }

    private LinearLayout detailRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
}
