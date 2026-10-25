package com.mine.geometry_node.client.ui.editor.globaldata;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.components.integration.faction.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.faction.model.FactionDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
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
    private final LinearLayout body;
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
        LinearLayout table = new LinearLayout(context); table.setOrientation(VERTICAL);
        UiTableRow header = UiTableStyle.header(context, columns, theme);
        header.addView(UiTableStyle.cell(context, tr("level"), theme));
        header.addView(UiTableStyle.cell(context, tr("rank_name"), theme)); header.addView(new FrameLayout(context));
        table.addView(header, new LayoutParams(-1, px(UiTableStyle.DETAILED_HEADER_HEIGHT)));
        body = new LinearLayout(context); body.setOrientation(VERTICAL);
        UiScrollView scroll = new UiScrollView(context, theme.scroll()); scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        table.addView(scroll, new LayoutParams(-1, 0, 1));
        addView(new UiTableViewport(context, columns, table, theme), new LayoutParams(-1, 0, 1));
    }
    void refresh(FactionDefinition value) {
        faction = value;
        for (int level : List.copyOf(rows.keySet())) if (!value.ranks().containsKey(level)) {
            Row row = rows.remove(level); row.number().setEditable(false); row.name().setEditable(false); body.removeView(row.view());
        }
        int index = 0;
        for (var rank : new TreeMap<>(value.ranks()).entrySet()) {
            Row row = rows.computeIfAbsent(rank.getKey(), this::createRow);
            row.number().refresh(rank.getKey()); row.name().refresh(rank.getValue());
            row.view().setDataStyle(index % 2 != 0);
            if (row.view().getParent() == null) body.addView(row.view(), index, new LayoutParams(-1, px(UiTableStyle.DETAILED_ROW_HEIGHT)));
            row.number().setEditable(canEdit()); row.name().setEditable(canEdit()); row.remove().setEnabled(canEdit()); index++;
        }
        add.setEnabled(canEdit());
    }
    void setActive(boolean value) {
        active = value;
        rows.values().forEach(row -> { row.number().setEditable(canEdit()); row.name().setEditable(canEdit()); row.remove().setEnabled(canEdit()); });
        add.setEnabled(canEdit());
    }
    private boolean canEdit() { return active && editable.getAsBoolean(); }
    private Row createRow(int level) {
        Context context = getContext(); UiTableRow cells = new UiTableRow(context, columns, theme);
        FactionIntegerInput number = new FactionIntegerInput(context, theme, level, Integer.MIN_VALUE, Integer.MAX_VALUE, next -> {
            if (!canEdit() || faction == null || !faction.ranks().containsKey(level) || faction.ranks().containsKey(next))
                throw new IllegalArgumentException("Duplicate or stale faction rank");
            actions.put(level, next, faction.ranks().get(level));
        });
        UIUtils.setLockedTextSize(number, UiMetrics.TEXT_BODY);
        FrameLayout numberCell = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, px(34), icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        params.leftMargin = px(12); params.rightMargin = px(12); numberCell.addView(number, params);
        FactionRichField name = new FactionRichField(context, theme, tr("edit_rank"), false, FactionRichField.Appearance.INLINE, next -> {
            if (canEdit() && faction.ranks().containsKey(level)) actions.put(level, level, next);
        }); name.setPadding(px(9), 0, px(6), 0);
        UiIconButton remove = UiIconButton.createThemed(context, theme, SvgIconView.Icon.CLEAR,
                tr("remove_rank"), UiIconButton.Preset.INLINE);
        remove.setOnClickListener(view -> { if (canEdit()) actions.remove(level); });
        FrameLayout action = new FrameLayout(context);
        action.addView(remove, new FrameLayout.LayoutParams(px(28), px(28), icyllis.modernui.view.Gravity.CENTER));
        cells.addView(numberCell); cells.addView(name); cells.addView(action);
        return new Row(cells, number, name, remove);
    }
}
