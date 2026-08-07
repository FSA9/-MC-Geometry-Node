package com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.quest;

import com.mine.geometry_node.client.quest.ui.QuestHintView;
import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.EntityTemplatePickerController;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.VanillaInventoryPicker;
import com.mine.geometry_node.core.engine.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.quest.model.QuestHintType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.EntityTemplateValue;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
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

public final class QuestObjectivesEditor extends LinearLayout {
    private static final int COLOR_ROW = 0xFF292929;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_BORDER = 0xFF4A4A4A;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF888888;
    private static final int COLOR_BUTTON = 0xFF454545;
    private static final int COLOR_BUTTON_HOVER = 0xFF565656;
    private static final int COLOR_ACTIVE = 0xFF3F6D52;
    private static final int COLOR_REMOVE = 0xFF704545;

    private final LinearLayout rows;
    private final Runnable onChanged;
    private final List<ObjectiveRow> editors = new ArrayList<>();
    private final Set<String> collapsedEntryIds = new HashSet<>();
    private boolean updating;

    public QuestObjectivesEditor(Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(context, tr("geometry_node.graph_properties.quest.objectives"), 10.5f, 0xFFA8A8A8),
                new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        TextView add = button(context, "+", tr("geometry_node.graph_properties.quest.objective_add"),
                COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
        add.setOnClickListener(v -> addObjective());
        header.addView(add, new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(26)));
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
        private final VectorIconView expandIcon;
        private final EditText contentInput;
        private final TextView quantityToggle;
        private final TextView counterToggle;
        private final EditText counterKeyInput;
        private final EditText targetInput;
        private final TextView hintToggle;
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
            root.setPadding(UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6),
                    UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6));
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
                    tr("geometry_node.graph_properties.quest.objective_placeholder"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            contentInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            contentRow.addView(contentInput, new LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            TextView rich = button(getContext(), "...",
                    tr("geometry_node.graph_properties.quest.edit_rich_text"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            rich.setOnClickListener(v -> openRichEditor(rich));
            LayoutParams smallButtonLp = new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(30));
            smallButtonLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(rich, smallButtonLp);
            LinearLayout order = orderButtons(getContext(),
                    () -> moveRow(this, -1),
                    () -> moveRow(this, 1),
                    tr("geometry_node.graph_properties.quest.objective_move_up"),
                    tr("geometry_node.graph_properties.quest.objective_move_down"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            LayoutParams orderLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            orderLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(order, orderLp);
            FrameLayout remove = iconButton(getContext(), VectorIconView.Kind.CLOSE,
                    tr("geometry_node.graph_properties.quest.objective_remove"),
                    15, COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            styleButton(remove, COLOR_REMOVE, COLOR_BUTTON_HOVER);
            remove.setOnClickListener(v -> removeRow(this));
            LayoutParams removeLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            removeLp.leftMargin = UIUtils.dp2pxInt(3);
            contentRow.addView(remove, removeLp);
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            quantityRow = new LinearLayout(getContext());
            quantityRow.setOrientation(HORIZONTAL);
            quantityRow.setGravity(Gravity.CENTER_VERTICAL);
            quantityToggle = button(getContext(), "",
                    tr("geometry_node.graph_properties.quest.quantity_enabled"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            quantityToggle.setOnClickListener(v -> {
                quantityEnabled = !quantityEnabled;
                if (!quantityEnabled) counterEnabled = false;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            quantityRow.addView(quantityToggle, new LayoutParams(
                    UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28)));
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
            root.addView(quantityRow, compactRowParams());

            counterRow = new LinearLayout(getContext());
            counterRow.setOrientation(HORIZONTAL);
            counterRow.setGravity(Gravity.CENTER_VERTICAL);
            counterToggle = button(getContext(), "",
                    tr("geometry_node.graph_properties.quest.counter_enabled"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            counterToggle.setOnClickListener(v -> {
                counterEnabled = !counterEnabled;
                if (counterEnabled) quantityEnabled = true;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            counterRow.addView(counterToggle, new LayoutParams(
                    UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28)));
            counterKeyInput = singleLineInput(getContext(), definition.counterKey(),
                    tr("geometry_node.graph_properties.quest.counter_key"),
                    COLOR_TEXT, COLOR_MUTED, COLOR_INPUT, COLOR_BORDER);
            counterKeyInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams keyLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            keyLp.leftMargin = UIUtils.dp2pxInt(4);
            counterRow.addView(counterKeyInput, keyLp);
            root.addView(counterRow, compactRowParams());

            hintRow = new LinearLayout(getContext());
            hintRow.setOrientation(HORIZONTAL);
            hintRow.setGravity(Gravity.CENTER_VERTICAL);
            hintToggle = button(getContext(), "",
                    tr("geometry_node.graph_properties.quest.hint_enabled"),
                    COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
            hintToggle.setOnClickListener(v -> {
                hintType = nextHintType(hintType);
                hintValue = "";
                updateHintUi();
                changed();
            });
            LayoutParams hintToggleLp = new LayoutParams(UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28));
            hintRow.addView(hintToggle, hintToggleLp);
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickHint);
            hintPreview.setDisplayPasteAction(this::pasteItem);
            hintPreview.setEntityDisplayPasteAction(this::pasteEntity);
            LayoutParams previewLp = new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32));
            previewLp.leftMargin = UIUtils.dp2pxInt(4);
            hintRow.addView(hintPreview, previewLp);
            root.addView(hintRow, compactRowParams());

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
                hintValue = ItemCodecUtils.toJson(stack, minecraft.level.registryAccess());
                updateHintPreview();
                changed();
            }, () -> {
                updateHintPreview();
                hintPreview.requestHintFocus();
            });
        }

        private void pickEntity() {
            EntityTemplatePickerController.open(template -> {
                hintType = QuestHintType.ENTITY;
                hintValue = template.toJsonString();
                updateHintPreview();
                changed();
            }, () -> {
                updateHintPreview();
                hintPreview.requestHintFocus();
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
            updateHintPreview();
            changed();
        }

        private void pasteEntity(EntityTemplateValue template) {
            if (template == null || template.isEmpty()) return;
            hintType = QuestHintType.ENTITY;
            hintValue = template.toJsonString();
            updateHintPreview();
            changed();
        }

        private void updateQuantityUi() {
            quantityToggle.setText(tr("geometry_node.graph_properties.quest.quantity_value"));
            styleButton(quantityToggle,
                    quantityEnabled ? COLOR_ACTIVE : COLOR_BUTTON,
                    COLOR_BUTTON_HOVER);
            targetInput.setVisibility(quantityEnabled ? View.VISIBLE : View.GONE);
        }

        private void updateCounterUi() {
            counterToggle.setText(tr("geometry_node.graph_properties.quest.counter_label"));
            styleButton(counterToggle,
                    counterEnabled ? COLOR_ACTIVE : COLOR_BUTTON,
                    COLOR_BUTTON_HOVER);
            counterKeyInput.setVisibility(counterEnabled ? View.VISIBLE : View.GONE);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintToggle.setText(tr(switch (hintType) {
                case ITEM_STACK, BLOCK -> "geometry_node.graph_properties.quest.hint_item";
                case ENTITY -> "geometry_node.graph_properties.quest.hint_entity";
                case NONE -> "geometry_node.graph_properties.quest.hint_none";
            }));
            styleButton(hintToggle,
                    hasHint ? COLOR_ACTIVE : COLOR_BUTTON,
                    COLOR_BUTTON_HOVER);
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
            quantityRow.setVisibility(visibility);
            counterRow.setVisibility(visibility);
            hintRow.setVisibility(visibility);
            expandIcon.setKind(expanded ? VectorIconView.Kind.CHEVRON_UP : VectorIconView.Kind.CHEVRON_DOWN);
            expandButton.setTooltipText(tr(expanded
                    ? "geometry_node.graph_properties.quest.objective_collapse"
                    : "geometry_node.graph_properties.quest.objective_expand"));
        }
    }

    private LayoutParams compactRowParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32));
        lp.topMargin = UIUtils.dp2pxInt(4);
        return lp;
    }

    private static QuestHintType nextHintType(QuestHintType current) {
        if (current == QuestHintType.NONE) return QuestHintType.ITEM_STACK;
        if (current == QuestHintType.ITEM_STACK) return QuestHintType.ENTITY;
        return QuestHintType.NONE;
    }

}
