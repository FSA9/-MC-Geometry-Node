package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.components.integration.faction.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.faction.model.FactionDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Rank labels interpret integers; editing them never rewrites entities' stored ranks. */
final class FactionRanksView extends LinearLayout {
    interface Actions {
        void put(Integer previous, int value, RichTextValue name);
        void remove(int value);
    }
    private record Row(UiTableRow view, FactionIntegerInput number, FactionRichField name, UiIconButton remove) {}
    private final UiTheme theme;
    private final Actions actions;
    private final BooleanSupplier editable;
    private final UiTableColumns columns = new UiTableColumns(List.of(
            new UiTableColumns.Column("level", 120, 90, true),
            new UiTableColumns.Column("name", 300, 160, true),
            new UiTableColumns.Column("remove", 42, 42, false)));
    private final UiTable table;
    private final UiTableItems<Integer, Map.Entry<Integer, RichTextValue>> items;
    private final UiIconButton add;
    private final Map<Integer, Row> rows = new TreeMap<>();
    private FactionDefinition faction;
    private boolean active = true;

    FactionRanksView(Context context, UiTheme theme, BooleanSupplier editable, Actions actions) {
        super(context); this.theme = theme; this.actions = actions; this.editable = editable; setOrientation(VERTICAL);
        LinearLayout toolbar = new LinearLayout(context); toolbar.setGravity(icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        toolbar.addView(UiTableStyle.cell(context, tr("ranks"), theme), new LayoutParams(0, -1, 1));
        add = UiIconButton.createThemed(context, theme, SvgIconView.Icon.PLUS, tr("add_rank"));
        toolbar.addView(add, new LayoutParams(px(30), px(30))); addView(toolbar, new LayoutParams(-1, px(38)));
        add.setOnClickListener(view -> {
            if (!canEdit() || faction == null) return;
            int next = 1; while (faction.ranks().containsKey(next)) next++;
            actions.put(null, next, RichTextValue.plain(tr("new_rank")));
        });
        table = new UiTable(context, theme, columns, List.of(
                UiTableColumn.label("level", tr("level")),
                UiTableColumn.label("name", tr("rank_name")),
                UiTableColumn.label("remove", "")), UiTableItems.body(context));
        items = new UiTableItems<>(table, Map.Entry::getKey, this::createItemRow);
        items.setReorderBlocked(() -> rows.values().stream().anyMatch(row -> row.number().isFocused()));
        addView(table, new LayoutParams(-1, 0, 1));
    }
    void refresh(FactionDefinition value) {
        faction = value;
        items.replace(new ArrayList<>(new TreeMap<>(value.ranks()).entrySet()));
        add.setEnabled(canEdit());
    }
    void setActive(boolean value) {
        active = value;
        rows.values().forEach(row -> { row.number().setEditable(canEdit()); row.name().setEditable(canEdit()); row.remove().setEnabled(canEdit()); });
        add.setEnabled(canEdit());
        if (!value) table.dismiss();
    }
    private boolean canEdit() { return active && editable.getAsBoolean(); }
    private UiTableItems.Row<Map.Entry<Integer, RichTextValue>> createItemRow(Map.Entry<Integer, RichTextValue> item) {
        int level = item.getKey();
        Row row = createRow(level);
        rows.put(level, row);
        return new UiTableItems.Row<>() {
            @Override public View view() { return row.view(); }
            @Override public void bind(Map.Entry<Integer, RichTextValue> current, int index) {
                row.number().refresh(current.getKey()); row.name().refresh(current.getValue());
                row.number().setEditable(canEdit()); row.name().setEditable(canEdit()); row.remove().setEnabled(canEdit());
            }
            @Override public void dispose() {
                // UiTableItems must invoke this before detaching a deleted row.
                row.number().dispose(); row.name().setEditable(false); row.remove().setEnabled(false);
                rows.remove(level, row);
            }
        };
    }
    private Row createRow(int level) {
        Context context = getContext();
        FactionIntegerInput number = new FactionIntegerInput(context, theme, level, Integer.MIN_VALUE, Integer.MAX_VALUE, next -> {
            if (!canEdit() || faction == null || !faction.ranks().containsKey(level) || faction.ranks().containsKey(next))
                throw new IllegalArgumentException("Duplicate or stale faction rank");
            actions.put(level, next, faction.ranks().get(level));
        });
        UIUtils.setLockedTextSize(number, UiTableStyle.CELL_TEXT_SIZE_DP);
        FrameLayout numberCell = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, px(34), icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        numberCell.addView(number, params);
        FactionRichField name = new FactionRichField(context, theme, tr("edit_rank"), false, FactionRichField.Appearance.INLINE, next -> {
            if (canEdit() && faction.ranks().containsKey(level)) actions.put(level, level, next);
        });
        UiIconButton remove = UiIconButton.createThemed(context, theme, SvgIconView.Icon.CLEAR,
                tr("remove_rank"), UiIconButton.Preset.INLINE);
        remove.setOnClickListener(view -> { if (canEdit()) actions.remove(level); });
        FrameLayout action = new FrameLayout(context);
        action.addView(remove, new FrameLayout.LayoutParams(px(28), px(28), icyllis.modernui.view.Gravity.CENTER));
        UiTableRow cells = table.createRow(Map.of("level", numberCell, "name", name, "remove", action));
        return new Row(cells, number, name, remove);
    }
}
