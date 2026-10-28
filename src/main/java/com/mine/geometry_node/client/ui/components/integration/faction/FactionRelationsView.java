package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.utils.ColumnFilters;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
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
    private final UiTable table;
    private final UiDisplayText count;
    private final UiTableItems<UUID, Target> items;
    private final Map<UUID, Row> rows = new LinkedHashMap<>();
    private record Row(UiTableRow view, UiDisplayText name, FactionIntegerInput score, UiIconButton clear) {}
    private record Target(UUID id, RichTextValue name, int score, boolean missing) {}
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
        table = new UiTable(context, theme, columns, List.of(
                UiTableColumn.interactive("name", tr("target"), () -> sort("name"), this::filterName),
                UiTableColumn.interactive("score", tr("score"), () -> sort("score"), this::filterScore)), UiTableItems.body(context));
        items = new UiTableItems<>(table, Target::id, this::createItemRow);
        items.setReorderBlocked(() -> rows.values().stream().anyMatch(row -> row.score().isFocused()));
        table.onDismiss(filters::dismiss);
        addView(table, new LayoutParams(-1, 0, 1));
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
    public void suspend() { table.dismiss(); save(); }
    public boolean hasPendingChanges() { return rows.values().stream().anyMatch(row -> row.score().hasPendingChanges()); }
    public void dispose() {
        if (disposed) return;
        save(); disposed = true; items.dispose(); table.dispose();
    }

    public void refresh() {
        if (disposed) return;
        FactionCatalog snapshot = catalog.get(); Map<UUID, Integer> scores = relations.get();
        Map<UUID, Target> targets = new LinkedHashMap<>();
        snapshot.factions().forEach(f -> targets.put(f.id(), new Target(f.id(), f.name(), scores.getOrDefault(f.id(), 0), false)));
        scores.forEach((id, score) -> targets.putIfAbsent(id,
                new Target(id, RichTextValue.plain(tr("missing_faction")), score, true)));
        Predicate<Target> visible = f -> {
            String name = f.name().plain().toLowerCase(Locale.ROOT);
            int score = f.score();
            return name.contains(query.toLowerCase(Locale.ROOT))
                    && (range.minimum() == null || score >= range.minimum())
                    && (range.maximum() == null || score <= range.maximum());
        };
        List<Target> ordered = new ArrayList<>(targets.values());
        count.setText(tr("visible_count", ordered.stream().filter(visible).count(), targets.size()));
        if (direction != 0) {
            Comparator<Target> order = "score".equals(sort)
                    ? Comparator.comparingInt(Target::score)
                    : Comparator.comparing(f -> f.name().plain(), String.CASE_INSENSITIVE_ORDER);
            if (direction == 2) order = order.reversed();
            ordered.sort(order.thenComparing(f -> f.id().toString()));
        }
        items.replace(ordered, visible);
        table.setFilterActive("name", !query.isEmpty());
        table.setFilterActive("score", range.minimum() != null || range.maximum() != null);
        table.setSort(sort, direction);
    }

    private UiTableItems.Row<Target> createItemRow(Target item) {
        UUID id = item.id();
        Row row = createRow(id);
        rows.put(id, row);
        return new UiTableItems.Row<>() {
            @Override public View view() { return row.view(); }
            @Override public void bind(Target current, int index) {
                boolean enabled = !disposed && editable.getAsBoolean() && index >= 0;
                // Disable before UiTableItems hides a filtered row and before any focus loss.
                row.score().setEditable(enabled && !current.missing());
                row.clear().setEnabled(enabled && current.missing());
                row.name().setText(FactionTextViews.display(current.name()));
                row.name().setTooltipText(current.name().plain());
                row.score().refresh(current.score());
                row.clear().setVisibility(current.missing() ? VISIBLE : GONE);
            }
            @Override public void dispose() {
                row.score().dispose(); row.clear().setEnabled(false); rows.remove(id, row);
            }
        };
    }

    private Row createRow(UUID id) {
        UiDisplayText name = UiTableStyle.cell(getContext(), "", theme);
        FactionIntegerInput score = new FactionIntegerInput(getContext(), theme, relations.get().getOrDefault(id, 0),
                -100, 100, value -> {
                    if (disposed || !editable.getAsBoolean() || catalog.get().find(id).isEmpty())
                        throw new IllegalArgumentException("Inactive faction relation editor");
                    change.accept(id, value);
                });
        score.setContentDescription(tr("score"));
        UIUtils.setLockedTextSize(score, UiTableStyle.CELL_TEXT_SIZE_DP);
        LinearLayout cell = new LinearLayout(getContext()); cell.setGravity(icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        cell.setBaselineAligned(false);
        cell.addView(score, new LayoutParams(0, px(34), 1));
        UiIconButton clear = UiIconButton.createThemed(getContext(), theme, SvgIconView.Icon.CLEAR,
                tr("remove_relation"), UiIconButton.Preset.INLINE);
        clear.setOnClickListener(view -> {
            if (!disposed && editable.getAsBoolean() && catalog.get().find(id).isEmpty()
                    && relations.get().containsKey(id)) change.accept(id, 0);
        });
        cell.addView(clear, new LayoutParams(px(28), px(28)));
        UiTableRow row = table.createRow(Map.of("name", name, "score", cell));
        return new Row(row, name, score, clear);
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
    private void sort(String key) {
        UiTable.Sort next = table.cycleSort(key);
        sort = next.column(); direction = next.direction(); save(); refresh();
    }
    private void filterName(View anchor) {
        filters.showText(anchor, tr("target"), query, value -> { query = value; save(); refresh(); });
    }
    private void filterScore(View anchor) {
        filters.showNumber(anchor, tr("score"), range, value -> {
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
