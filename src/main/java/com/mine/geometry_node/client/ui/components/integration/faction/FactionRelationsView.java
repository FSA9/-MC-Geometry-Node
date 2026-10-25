package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.query.ColumnFilters;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.function.*;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Directed score editor shared by faction definitions and independent entity relations. */
public final class FactionRelationsView extends LinearLayout {
    public record State(String query, Double minimum, Double maximum, String sort, int direction,
                        Map<String, Float> widths) {
        public State {
            query = query == null ? "" : query.substring(0, Math.min(1024, query.length()));
            sort = "score".equals(sort) ? "score" : "name";
            direction = Math.max(0, Math.min(2, direction));
            if (!validBound(minimum) || !validBound(maximum)
                    || minimum != null && maximum != null && minimum > maximum) { minimum = null; maximum = null; }
            widths = widths == null ? Map.of() : Map.copyOf(widths);
        }
    }
    private static final UiStateKey<State> STATE = new UiStateKey<>("faction:relations", 1,
            UiStateLifetime.MEMORY, State.class, () -> new State("", null, null, "name", 0, Map.of()), value -> value);
    private final UiTheme theme;
    private final Supplier<FactionCatalog> catalog;
    private final Supplier<Map<UUID, Integer>> relations;
    private final BiConsumer<UUID, Integer> change;
    private final UiTableColumns columns = new UiTableColumns(List.of(
            new UiTableColumns.Column("name", 590, 180, true),
            new UiTableColumns.Column("score", 200, 160, true)));
    private final UiColumnFilterPopover filters;
    private final UiTableFilterHeader nameHeader, scoreHeader;
    private final UiDisplayText count;
    private final LinearLayout body;
    private final Map<UUID, Row> rows = new LinkedHashMap<>();
    private record Row(UiTableRow view, UiDisplayText name, FactionIntegerInput score, UiIconButton clear) {}
    private record Target(UUID id, RichTextValue name, boolean missing) {}
    private BooleanSupplier editable = () -> true;
    private UiStateContext state;
    private String scope, query = "", sort = "name";
    private ColumnFilters.NumberRange range = ColumnFilters.NumberRange.unbounded();
    private int direction;
    private boolean disposed;

    public FactionRelationsView(Context context, UiTheme theme, Supplier<FactionCatalog> catalog,
                                Supplier<Map<UUID, Integer>> relations, BiConsumer<UUID, Integer> change) {
        super(context); this.theme = theme; this.catalog = catalog; this.relations = relations; this.change = change;
        setOrientation(VERTICAL);
        count = new UiDisplayText(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        count.setGravity(icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        addView(count, new LayoutParams(-1, px(32)));
        filters = new UiColumnFilterPopover(context, this, theme);
        LinearLayout table = new LinearLayout(context); table.setOrientation(VERTICAL);
        UiTableRow header = UiTableStyle.header(context, columns, theme);
        nameHeader = new UiTableFilterHeader(context, tr("target"), () -> sort("name"), this::filterName, theme);
        scoreHeader = new UiTableFilterHeader(context, tr("score"), () -> sort("score"), this::filterScore, theme);
        header.addView(nameHeader); header.addView(scoreHeader);
        table.addView(header, new LayoutParams(-1, px(UiTableStyle.DETAILED_HEADER_HEIGHT)));
        body = new LinearLayout(context); body.setOrientation(VERTICAL);
        UiScrollView scroll = new UiScrollView(context, theme.scroll());
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        table.addView(scroll, new LayoutParams(-1, 0, 1));
        addView(new UiTableViewport(context, columns, table, theme), new LayoutParams(-1, 0, 1));
        columns.addUserStateListener(this::save);
    }

    public void bindState(UiStateContext context, String scope) {
        save(); this.state = Objects.requireNonNull(context); this.scope = Objects.requireNonNull(scope);
        State saved = state.read(STATE, scope);
        query = saved.query(); sort = saved.sort(); direction = saved.direction();
        range = new ColumnFilters.NumberRange(saved.minimum(), saved.maximum());
        columns.restoreCustomWidths(saved.widths()); refresh();
    }
    public void setEditable(BooleanSupplier value) { editable = Objects.requireNonNull(value); refreshEditable(); }
    public void suspend() { filters.dismiss(); save(); }
    public boolean hasPendingChanges() { return rows.values().stream().anyMatch(row -> row.score().hasPendingChanges()); }
    public void dispose() {
        if (disposed) return;
        save(); disposed = true; rows.values().forEach(row -> row.score().dispose());
        filters.dismiss(); rows.clear();
    }

    public void refresh() {
        if (disposed) return;
        FactionCatalog snapshot = catalog.get(); Map<UUID, Integer> scores = relations.get();
        Map<UUID, Target> targets = new LinkedHashMap<>();
        snapshot.factions().forEach(f -> targets.put(f.id(), new Target(f.id(), f.name(), false)));
        scores.keySet().forEach(id -> targets.putIfAbsent(id, new Target(id, RichTextValue.plain(tr("missing_faction")), true)));
        List<Target> visible = targets.values().stream().filter(f -> {
            String name = f.name().plain().toLowerCase(Locale.ROOT);
            int score = scores.getOrDefault(f.id(), 0);
            return name.contains(query.toLowerCase(Locale.ROOT))
                    && (range.minimum() == null || score >= range.minimum())
                    && (range.maximum() == null || score <= range.maximum());
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        count.setText(tr("visible_count", visible.size(), targets.size()));
        if (direction != 0) {
            Comparator<Target> order = "score".equals(sort)
                    ? Comparator.comparingInt(f -> scores.getOrDefault(f.id(), 0))
                    : Comparator.comparing(f -> f.name().plain(), String.CASE_INSENSITIVE_ORDER);
            if (direction == 2) order = order.reversed();
            visible.sort(order.thenComparing(f -> f.id().toString()));
        }
        Set<UUID> live = targets.keySet();
        for (UUID id : List.copyOf(rows.keySet())) if (!live.contains(id)) {
            Row old = rows.remove(id); old.score().dispose(); body.removeView(old.view());
        }
        Set<UUID> displayed = new HashSet<>(); visible.forEach(faction -> displayed.add(faction.id()));
        rows.forEach((id, row) -> {
            if (!displayed.contains(id)) { row.score().setEditable(false); row.view().setVisibility(GONE); }
        });
        boolean focused = rows.values().stream().anyMatch(row -> row.score().isFocused());
        int index = 0;
        for (Target faction : visible) {
            Row row = rows.computeIfAbsent(faction.id(), this::createRow);
            row.name().setText(FactionTextViews.display(faction.name()));
            row.name().setTooltipText(faction.name().plain());
            row.score().refresh(scores.getOrDefault(faction.id(), 0));
            row.clear().setVisibility(faction.missing() ? VISIBLE : GONE);
            row.view().setDataStyle(index % 2 != 0);
            row.view().setVisibility(VISIBLE);
            if (row.view().getParent() == null) body.addView(row.view(), new LayoutParams(-1, px(UiTableStyle.DETAILED_ROW_HEIGHT)));
            if (!focused && (index >= body.getChildCount() || body.getChildAt(index) != row.view())) {
                if (row.view().getParent() == body) body.removeView(row.view());
                body.addView(row.view(), index, new LayoutParams(-1, px(UiTableStyle.DETAILED_ROW_HEIGHT)));
            }
            index++;
        }
        nameHeader.setFilterActive(!query.isEmpty()); scoreHeader.setFilterActive(range.minimum() != null || range.maximum() != null);
        title(nameHeader, "target", "name"); title(scoreHeader, "score", "score");
        refreshEditable();
    }

    private Row createRow(UUID id) {
        UiTableRow row = new UiTableRow(getContext(), columns, theme);
        UiDisplayText name = UiTableStyle.cell(getContext(), "", theme);
        UIUtils.setLockedTextSize(name, UiMetrics.TEXT_BODY);
        name.setPadding(px(12), 0, px(12), 0);
        FactionIntegerInput score = new FactionIntegerInput(getContext(), theme, relations.get().getOrDefault(id, 0),
                -100, 100, value -> {
                    if (disposed || !editable.getAsBoolean() || catalog.get().find(id).isEmpty())
                        throw new IllegalArgumentException("Inactive faction relation editor");
                    change.accept(id, value);
                });
        score.setContentDescription(tr("score"));
        UIUtils.setLockedTextSize(score, UiMetrics.TEXT_BODY);
        LinearLayout cell = new LinearLayout(getContext()); cell.setGravity(icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        cell.setPadding(px(12), 0, px(12), 0); cell.setBaselineAligned(false);
        cell.addView(score, new LayoutParams(0, px(34), 1));
        UiIconButton clear = UiIconButton.createThemed(getContext(), theme, SvgIconView.Icon.CLEAR,
                tr("remove_relation"), UiIconButton.Preset.INLINE);
        clear.setOnClickListener(view -> {
            if (!disposed && editable.getAsBoolean() && catalog.get().find(id).isEmpty()
                    && relations.get().containsKey(id)) change.accept(id, 0);
        });
        cell.addView(clear, new LayoutParams(px(28), px(28)));
        row.addView(name); row.addView(cell); return new Row(row, name, score, clear);
    }
    private void refreshEditable() {
        boolean enabled = !disposed && editable.getAsBoolean();
        rows.values().forEach(row -> {
            boolean visible = row.view().getVisibility() == VISIBLE;
            boolean missing = row.clear().getVisibility() == VISIBLE;
            row.score().setEditable(enabled && visible && !missing);
            row.clear().setEnabled(enabled && visible && missing);
        });
    }
    private void title(UiTableFilterHeader header, String key, String column) {
        if (header.getChildAt(0) instanceof UiActionButton label)
            label.setText(tr(key) + (!sort.equals(column) || direction == 0 ? "" : direction == 1 ? " ↑" : " ↓"));
    }
    private void sort(String key) {
        direction = sort.equals(key) ? (direction + 1) % 3 : 1; sort = key; save(); refresh();
    }
    private void filterName() {
        filters.showText(nameHeader.filterButton(), tr("target"), query, value -> { query = value; save(); refresh(); });
    }
    private void filterScore() {
        filters.showNumber(scoreHeader.filterButton(), tr("score"), range, value -> {
            if (!validBound(value.minimum()) || !validBound(value.maximum())) throw new IllegalArgumentException("Integer score range required");
            range = value; save(); refresh();
        });
    }
    private static boolean validBound(Double value) {
        return value == null || Double.isFinite(value) && value >= -100 && value <= 100 && value == Math.rint(value);
    }
    private void save() {
        if (state != null && !disposed) state.write(STATE, scope,
                new State(query, range.minimum(), range.maximum(), sort, direction, columns.exportCustomWidths()));
    }
}
