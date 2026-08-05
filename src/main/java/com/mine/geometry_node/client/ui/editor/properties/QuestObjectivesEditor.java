package com.mine.geometry_node.client.ui.editor.properties;

import com.mine.geometry_node.client.quest.ui.QuestHintView;
import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.VanillaInventoryPicker;
import com.mine.geometry_node.core.engine.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.quest.model.QuestCounterKey;
import com.mine.geometry_node.core.engine.quest.model.QuestHintType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class QuestObjectivesEditor extends LinearLayout {
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
    private boolean updating;

    QuestObjectivesEditor(Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(tr("geometry_node.graph_properties.quest.objectives"), 10.5f, 0xFFA8A8A8),
                new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        TextView add = button("+", tr("geometry_node.graph_properties.quest.objective_add"));
        add.setOnClickListener(v -> addObjective());
        header.addView(add, new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(26)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void setObjectives(List<QuestObjectiveDefinition> objectives) {
        updating = true;
        rows.removeAllViews();
        editors.clear();
        if (objectives != null) {
            for (QuestObjectiveDefinition objective : objectives) addRow(objective);
        }
        rebuildRows();
        updating = false;
    }

    List<QuestObjectiveDefinition> objectives() {
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

    private final class ObjectiveRow {
        private final String entryId;
        private RichTextValue content;
        private boolean quantityEnabled;
        private boolean counterEnabled;
        private QuestHintType hintType;
        private String itemHintValue;

        private final LinearLayout root;
        private final EditText contentInput;
        private final TextView quantityToggle;
        private final TextView counterToggle;
        private final EditText counterKeyInput;
        private final EditText targetInput;
        private final TextView hintToggle;
        private final QuestHintView hintPreview;

        private ObjectiveRow(QuestObjectiveDefinition definition) {
            entryId = definition.entryId();
            content = definition.content();
            quantityEnabled = definition.quantityEnabled();
            counterEnabled = definition.counterEnabled();
            hintType = definition.hintType();
            itemHintValue = definition.hintValue();

            root = new LinearLayout(getContext());
            root.setOrientation(VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6),
                    UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6));
            root.setBackground(rect(COLOR_ROW, 3.0f, 1, COLOR_BORDER));

            LinearLayout contentRow = new LinearLayout(getContext());
            contentRow.setOrientation(HORIZONTAL);
            contentRow.setGravity(Gravity.CENTER_VERTICAL);
            contentInput = input(content.plain(), tr("geometry_node.graph_properties.quest.objective_placeholder"));
            contentInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            contentRow.addView(contentInput, new LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            TextView rich = button("...", tr("geometry_node.graph_properties.quest.edit_rich_text"));
            rich.setOnClickListener(v -> openRichEditor(rich));
            LayoutParams smallButtonLp = new LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(30));
            smallButtonLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(rich, smallButtonLp);
            LinearLayout order = orderButtons(
                    () -> moveRow(this, -1),
                    () -> moveRow(this, 1),
                    tr("geometry_node.graph_properties.quest.objective_move_up"),
                    tr("geometry_node.graph_properties.quest.objective_move_down"));
            LayoutParams orderLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            orderLp.leftMargin = UIUtils.dp2pxInt(4);
            contentRow.addView(order, orderLp);
            FrameLayout remove = iconButton(VectorIconView.Kind.CLOSE,
                    tr("geometry_node.graph_properties.quest.objective_remove"));
            styleButton(remove, COLOR_REMOVE);
            remove.setOnClickListener(v -> removeRow(this));
            LayoutParams removeLp = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(30));
            removeLp.leftMargin = UIUtils.dp2pxInt(3);
            contentRow.addView(remove, removeLp);
            root.addView(contentRow, new LayoutParams(
                    LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            LinearLayout quantityRow = new LinearLayout(getContext());
            quantityRow.setOrientation(HORIZONTAL);
            quantityRow.setGravity(Gravity.CENTER_VERTICAL);
            quantityToggle = button("", tr("geometry_node.graph_properties.quest.quantity_enabled"));
            quantityToggle.setOnClickListener(v -> {
                quantityEnabled = !quantityEnabled;
                if (!quantityEnabled) counterEnabled = false;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            quantityRow.addView(quantityToggle, new LayoutParams(
                    UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28)));
            targetInput = input(formatNumber(definition.targetValue()),
                    tr("geometry_node.graph_properties.quest.quantity_value"));
            targetInput.setGravity(Gravity.CENTER);
            targetInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams targetLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            targetLp.leftMargin = UIUtils.dp2pxInt(4);
            quantityRow.addView(targetInput, targetLp);
            root.addView(quantityRow, compactRowParams());

            LinearLayout counterRow = new LinearLayout(getContext());
            counterRow.setOrientation(HORIZONTAL);
            counterRow.setGravity(Gravity.CENTER_VERTICAL);
            counterToggle = button("", tr("geometry_node.graph_properties.quest.counter_enabled"));
            counterToggle.setOnClickListener(v -> {
                counterEnabled = !counterEnabled;
                if (counterEnabled) quantityEnabled = true;
                updateQuantityUi();
                updateCounterUi();
                changed();
            });
            counterRow.addView(counterToggle, new LayoutParams(
                    UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28)));
            counterKeyInput = input(definition.counterKey(),
                    tr("geometry_node.graph_properties.quest.counter_key"));
            counterKeyInput.setOnFocusChangeListener((v, focused) -> {
                if (!focused) changed();
            });
            LayoutParams keyLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            keyLp.leftMargin = UIUtils.dp2pxInt(4);
            counterRow.addView(counterKeyInput, keyLp);
            root.addView(counterRow, compactRowParams());

            LinearLayout hintRow = new LinearLayout(getContext());
            hintRow.setOrientation(HORIZONTAL);
            hintRow.setGravity(Gravity.CENTER_VERTICAL);
            hintToggle = button("", tr("geometry_node.graph_properties.quest.hint_enabled"));
            hintToggle.setOnClickListener(v -> {
                hintType = hintType == QuestHintType.NONE
                        ? QuestHintType.ITEM_STACK
                        : QuestHintType.NONE;
                updateHintUi();
                changed();
            });
            LayoutParams hintToggleLp = new LayoutParams(UIUtils.dp2pxInt(66), UIUtils.dp2pxInt(28));
            hintRow.addView(hintToggle, hintToggleLp);
            hintPreview = new QuestHintView(getContext());
            hintPreview.setDisplayClickAction(this::pickItem);
            LayoutParams previewLp = new LayoutParams(UIUtils.dp2pxInt(32), UIUtils.dp2pxInt(32));
            previewLp.leftMargin = UIUtils.dp2pxInt(4);
            hintRow.addView(hintPreview, previewLp);
            root.addView(hintRow, compactRowParams());

            updateQuantityUi();
            updateCounterUi();
            updateHintUi();
        }

        private QuestObjectiveDefinition definition() {
            RichTextValue updatedContent = contentInput.getText().toString().equals(content.plain())
                    ? content
                    : RichTextValue.plain(contentInput.getText().toString());
            return new QuestObjectiveDefinition(
                    entryId,
                    updatedContent,
                    counterEnabled,
                    QuestCounterKey.normalize(counterKeyInput.getText().toString()),
                    quantityEnabled,
                    parseDouble(targetInput.getText().toString(), 1.0),
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
            }, this::updateHintPreview);
        }

        private void updateQuantityUi() {
            quantityToggle.setText(tr("geometry_node.graph_properties.quest.quantity_value"));
            styleButton(quantityToggle, quantityEnabled ? COLOR_ACTIVE : COLOR_BUTTON);
            targetInput.setVisibility(quantityEnabled ? View.VISIBLE : View.GONE);
        }

        private void updateCounterUi() {
            counterToggle.setText(tr("geometry_node.graph_properties.quest.counter_label"));
            styleButton(counterToggle, counterEnabled ? COLOR_ACTIVE : COLOR_BUTTON);
            counterKeyInput.setVisibility(counterEnabled ? View.VISIBLE : View.GONE);
        }

        private void updateHintUi() {
            boolean hasHint = hintType != QuestHintType.NONE;
            hintToggle.setText(tr("geometry_node.graph_properties.quest.hint_label"));
            styleButton(hintToggle, hasHint ? COLOR_ACTIVE : COLOR_BUTTON);
            hintPreview.setVisibility(hasHint ? View.VISIBLE : View.GONE);
            updateHintPreview();
        }

        private void updateHintPreview() {
            hintPreview.setHint(hintType, itemHintValue);
        }
    }

    private LayoutParams compactRowParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32));
        lp.topMargin = UIUtils.dp2pxInt(4);
        return lp;
    }

    private LinearLayout orderButtons(Runnable moveUp, Runnable moveDown, String upTooltip, String downTooltip) {
        LinearLayout order = new LinearLayout(getContext());
        order.setOrientation(VERTICAL);
        FrameLayout up = iconButton(VectorIconView.Kind.CHEVRON_UP, upTooltip, 9);
        up.setOnClickListener(v -> moveUp.run());
        order.addView(up, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(14)));
        FrameLayout down = iconButton(VectorIconView.Kind.CHEVRON_DOWN, downTooltip, 9);
        down.setOnClickListener(v -> moveDown.run());
        LayoutParams downLp = new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(14));
        downLp.topMargin = UIUtils.dp2pxInt(2);
        order.addView(down, downLp);
        return order;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(value != null ? value : "");
        input.setHint(hint);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setTextSize(0, UIUtils.dp2px(10.5f));
        input.setPadding(UIUtils.dp2pxInt(7), 0, UIUtils.dp2pxInt(7), 0);
        input.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_BORDER));
        return input;
    }

    private TextView button(String text, String tooltip) {
        TextView button = label(text, 10.0f, COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setTooltipText(tooltip);
        styleButton(button, COLOR_BUTTON);
        return button;
    }

    private FrameLayout iconButton(VectorIconView.Kind kind, String tooltip) {
        return iconButton(kind, tooltip, 15);
    }

    private FrameLayout iconButton(VectorIconView.Kind kind, String tooltip, int iconSizeDp) {
        FrameLayout button = new FrameLayout(getContext());
        button.setTooltipText(tooltip);
        styleButton(button, COLOR_BUTTON);
        VectorIconView icon = new VectorIconView(getContext(), kind, COLOR_TEXT);
        icon.setClickable(false);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(iconSizeDp), UIUtils.dp2pxInt(iconSizeDp));
        iconLp.gravity = Gravity.CENTER;
        button.addView(icon, iconLp);
        return button;
    }

    private void styleButton(View button, int normalColor) {
        button.setBackground(rect(normalColor, 3.0f, 0, 0));
        button.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.setBackground(rect(COLOR_BUTTON_HOVER, 3.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.setBackground(rect(normalColor, 3.0f, 0, 0));
            }
            return false;
        });
    }

    private TextView label(String text, float sizeDp, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(0, UIUtils.dp2px(sizeDp));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? Math.max(0.0, parsed) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
