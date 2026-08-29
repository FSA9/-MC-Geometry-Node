package com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.quest;

import com.mine.geometry_node.client.runtime.quest.ui.QuestHintView;
import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.VanillaInventoryPicker;
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

import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.button;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.iconButton;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.orderButtons;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.singleLineInput;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.styleButton;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.tr;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertyNumbers.format;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertyNumbers.parseNonNegativeDouble;

public final class QuestRewardsEditor extends LinearLayout {
    private static final int COLOR_ROW = 0xFF292929;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_BORDER = 0xFF4A4A4A;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF888888;
    private static final int COLOR_BUTTON = 0xFF454545;
    private static final int COLOR_BUTTON_HOVER = 0xFF565656;
    private static final int COLOR_ACTIVE = 0xFF6D573F;
    private static final int COLOR_REMOVE = 0xFF704545;

    private final LinearLayout rows;
    private final Runnable onChanged;
    private final List<RewardRow> editors = new ArrayList<>();
    private final Set<String> collapsedEntryIds = new HashSet<>();
    private boolean updating;

    public QuestRewardsEditor(Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(context, tr("geometry_node.graph_properties.quest.rewards"), 10.5f, 0xFFA8A8A8),
                new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        TextView add = button(context, "+", tr("geometry_node.graph_properties.quest.reward_add"),
                COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
        add.setOnClickListener(v -> addReward());
        header.addView(add, new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(26)));
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

    private final class RewardRow {
        private final String entryId;
        private RichTextValue content;
        private boolean counterEnabled;
        private QuestHintType hintType;
        private String itemHintValue;
        private boolean expanded;

        private final LinearLayout root;
        private final FrameLayout expandButton;
        private final VectorIconView expandIcon;
        private final EditText contentInput;
        private final TextView counterToggle;
        private final EditText counterKeyInput;
        private final EditText amountInput;
        private final TextView hintToggle;
        private final QuestHintView hintPreview;
        private final LinearLayout amountRow;

        private RewardRow(QuestRewardDefinition definition) {
            entryId = definition.entryId();
            content = definition.content();
            counterEnabled = definition.counterEnabled();
            hintType = definition.hintType();
            itemHintValue = definition.hintValue();
            expanded = !collapsedEntryIds.contains(entryId);

            root = new LinearLayout(getContext());
            root.setOrientation(VERTICAL);
            int padding = UIUtils.dp2pxInt(6);
            root.setPadding(padding, padding, padding, padding);
            root.setBackground(rect(COLOR_ROW, 3.0f, 1, COLOR_BORDER));

            LinearLayout contentRow = new LinearLayout(getContext());
            contentRow.setOrientation(HORIZONTAL);
            contentRow.setGravity(Gravity.CENTER_VERTICAL);
            expandButton = new FrameLayout(getContext());
            styleButton(expandButton, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            expandIcon = new VectorIconView(getContext(), VectorIconView.Kind.CHEVRON_UP, COLOR_TEXT);
            expandIcon.setClickable(false);
            FrameLayout.LayoutParams expandIconLp = new FrameLayout.LayoutParams(
                    UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12));
            expandIconLp.gravity = Gravity.CENTER;
            expandButton.addView(expandIcon, expandIconLp);
            expandButton.setOnClickListener(v -> toggleExpanded());
            LayoutParams expandLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            expandLp.rightMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(expandButton, expandLp);
            contentInput = singleLineInput(getContext(), content.plain(),
                    tr("geometry_node.graph_properties.quest.reward_placeholder"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            contentInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            contentRow.addView(contentInput, new LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            TextView rich = button(getContext(), "...",
                    tr("geometry_node.graph_properties.quest.edit_rich_text"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            rich.setOnClickListener(v -> openRichEditor(rich));
            LayoutParams richLp = new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(30));
            richLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(rich, richLp);
            LinearLayout order = orderButtons(getContext(),
                    () -> moveRow(this, -1),
                    () -> moveRow(this, 1),
                    tr("geometry_node.graph_properties.quest.reward_move_up"),
                    tr("geometry_node.graph_properties.quest.reward_move_down"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            LayoutParams orderLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            orderLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(order, orderLp);
            FrameLayout remove = iconButton(getContext(), VectorIconView.Kind.CLOSE,
                    tr("geometry_node.graph_properties.quest.reward_remove"),
                    15, COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            styleButton(remove, COLOR_REMOVE, COLOR_BUTTON_HOVER);
            remove.setOnClickListener(v -> removeRow(this));
            LayoutParams removeLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            removeLp.leftMargin = UIUtils.dp2pxInt(3);
            contentRow.addView(remove, removeLp);
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            amountRow = new LinearLayout(getContext());
            amountRow.setOrientation(HORIZONTAL);
            amountRow.setGravity(Gravity.CENTER_VERTICAL);
            counterToggle = button(getContext(), "",
                    tr("geometry_node.graph_properties.quest.reward_counter_enabled"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            counterToggle.setOnClickListener(v -> {
                counterEnabled = !counterEnabled;
                updateCounterUi();
                changed();
            });
            amountRow.addView(counterToggle, new LayoutParams(
                    UIUtils.dp2pxInt(68), UIUtils.dp2pxInt(28)));
            counterKeyInput = singleLineInput(getContext(), definition.counterKey(),
                    tr("geometry_node.graph_properties.quest.counter_key"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            counterKeyInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams counterKeyLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            counterKeyLp.leftMargin = UIUtils.dp2pxInt(4);
            amountRow.addView(counterKeyInput, counterKeyLp);
            amountInput = singleLineInput(getContext(), format(definition.amount()),
                    tr("geometry_node.graph_properties.quest.reward_amount"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            amountInput.setGravity(Gravity.CENTER);
            amountInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams amountLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            amountLp.leftMargin = UIUtils.dp2pxInt(4);
            amountRow.addView(amountInput, amountLp);
            hintToggle = button(getContext(), "",
                    tr("geometry_node.graph_properties.quest.hint_enabled"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            hintToggle.setOnClickListener(v -> {
                hintType = hintType == QuestHintType.NONE
                        ? QuestHintType.ITEM_STACK
                        : QuestHintType.NONE;
                updateHintUi();
                changed();
            });
            LayoutParams hintToggleLp = new LayoutParams(UIUtils.dp2pxInt(62), UIUtils.dp2pxInt(28));
            hintToggleLp.leftMargin = UIUtils.dp2pxInt(6);
            amountRow.addView(hintToggle, hintToggleLp);
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickItem);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            LayoutParams previewLp = new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32));
            previewLp.leftMargin = UIUtils.dp2pxInt(4);
            amountRow.addView(hintPreview, previewLp);
            LayoutParams amountRowLp = new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32));
            amountRowLp.topMargin = UIUtils.dp2pxInt(4);
            root.addView(amountRow, amountRowLp);

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
            VanillaInventoryPicker.openItem(stack -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) return;
                hintType = QuestHintType.ITEM_STACK;
                itemHintValue = ItemCodecUtils.toJson(stack, minecraft.level.registryAccess());
                updateHintPreview();
                changed();
            }, () -> {
                updateHintPreview();
                hintPreview.requestHintFocus();
            });
        }

        private void pasteItem(String itemJson) {
            hintType = QuestHintType.ITEM_STACK;
            itemHintValue = itemJson != null ? itemJson : "";
            updateHintPreview();
            changed();
        }

        private void updateCounterUi() {
            counterToggle.setText(tr("geometry_node.graph_properties.quest.counter_label"));
            styleButton(counterToggle,
                    counterEnabled ? COLOR_ACTIVE : COLOR_BUTTON,
                    COLOR_BUTTON_HOVER);
            counterKeyInput.setVisibility(counterEnabled ? View.VISIBLE : View.GONE);
            amountInput.setVisibility(counterEnabled ? View.GONE : View.VISIBLE);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintToggle.setText(tr("geometry_node.graph_properties.quest.hint_label"));
            styleButton(hintToggle,
                    hasHint ? COLOR_ACTIVE : COLOR_BUTTON,
                    COLOR_BUTTON_HOVER);
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
            amountRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandIcon.setKind(expanded ? VectorIconView.Kind.CHEVRON_UP : VectorIconView.Kind.CHEVRON_DOWN);
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.reward_collapse"
                    : "geometry_node.graph_properties.quest.reward_expand"));
        }
    }

}
