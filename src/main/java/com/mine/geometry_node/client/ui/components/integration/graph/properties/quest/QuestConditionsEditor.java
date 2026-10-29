package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.integration.faction.FactionRichField;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;

/** The same ordered rich-text list editor serves all three condition-description groups. */
public final class QuestConditionsEditor extends LinearLayout {
    private final LinearLayout rows;
    private final Runnable onCountChanged;
    private final Runnable onChanged;
    private final List<ConditionRow> editors = new ArrayList<>();
    private boolean active;

    QuestConditionsEditor(Context context, Runnable onChanged, Runnable onCountChanged) {
        super(context);
        this.onChanged = onChanged;
        this.onCountChanged = onCountChanged;
        setOrientation(VERTICAL);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void addCondition() {
        if (!canAdd()) return;
        editors.add(new ConditionRow(RichTextValue.EMPTY));
        rebuildRows();
        changed();
    }

    boolean canAdd() { return active && editors.size() < QuestDefinition.MAX_CONDITIONS_PER_GROUP; }
    int count() { return editors.size(); }

    void setConditions(List<RichTextValue> values) {
        List<RichTextValue> incoming = values == null ? List.of() : values;
        // A save/preview refresh must not invalidate an editor already open for an unchanged row.
        if (active && conditions().equals(incoming)) return;
        clear();
        active = true;
        for (RichTextValue value : incoming) editors.add(new ConditionRow(value));
        rebuildRows();
    }

    List<RichTextValue> conditions() {
        return editors.stream().map(row -> row.value).toList();
    }

    void clear() {
        active = false;
        editors.forEach(row -> row.field.setEditable(false));
        editors.clear();
        rows.removeAllViews();
        onCountChanged.run();
    }

    private void remove(ConditionRow row) {
        if (!active || !editors.remove(row)) return;
        row.field.setEditable(false);
        rebuildRows();
        changed();
    }

    private void move(ConditionRow row, int delta) {
        if (!active) return;
        int current = editors.indexOf(row);
        int next = current + delta;
        if (current < 0 || next < 0 || next >= editors.size()) return;
        editors.remove(current);
        editors.add(next, row);
        rebuildRows();
        changed();
    }

    private void rebuildRows() {
        rows.removeAllViews();
        for (int i = 0; i < editors.size(); i++) {
            ConditionRow row = editors.get(i);
            int number = i + 1;
            row.number.setText(number < 10 ? "0" + number : Integer.toString(number));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UIUtils.dp2pxInt(6);
            rows.addView(row.root, lp);
        }
        onCountChanged.run();
    }

    private void changed() {
        if (active && onChanged != null) onChanged.run();
    }

    private final class ConditionRow {
        private RichTextValue value;
        private final LinearLayout root;
        private final FactionRichField field;
        private final TextView number;

        private void update(RichTextValue next) {
            if (!active || !editors.contains(this)) return;
            value = next;
            field.refresh(next);
            changed();
        }

        ConditionRow(RichTextValue value) {
            this.value = value;
            root = new LinearLayout(getContext());
            root.setOrientation(HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4));
            root.setBackground(com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect(
                    UiThemes.EDITOR.surface().panel(), 3, 0, 0));
            root.addView(QuestEntryUi.reorderHandle(getContext(), rows, root,
                    delta -> move(this, delta)), QuestEntryUi.handleParams());
            number = QuestEntryUi.entryNumber(getContext());
            root.addView(number, new LayoutParams(UIUtils.dp2pxInt(22), UIUtils.dp2pxInt(28)));
            field = new FactionRichField(getContext(), UiThemes.EDITOR,
                    tr("geometry_node.graph_properties.quest.edit_rich_text"), true, FactionRichField.Appearance.INLINE, this::update);
            TextView preview = (TextView) field.getChildAt(0);
            UIUtils.setLockedTextSize(preview, 12);
            preview.setMinimumHeight(UIUtils.dp2pxInt(28));
            preview.setPadding(0, UIUtils.dp2pxInt(4), 0, UIUtils.dp2pxInt(4));
            preview.setHint(tr("geometry_node.graph_properties.quest.condition_placeholder"));
            preview.setHintTextColor(UiThemes.EDITOR.text().hint());
            field.refresh(value);
            root.addView(field, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            UiIconButton remove = QuestEntryUi.action(getContext(), SvgIconView.Icon.CLOSE,
                    "geometry_node.graph_properties.quest.condition_remove");
            remove.setOnClickListener(v -> remove(this));
            root.addView(remove, GraphPropertiesUi.actionParams());
        }
    }
}
