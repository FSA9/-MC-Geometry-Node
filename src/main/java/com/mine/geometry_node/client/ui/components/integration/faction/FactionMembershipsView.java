package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.utils.ColumnFilters;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.*;
import java.util.function.*;

import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Registered-rank membership editor, composed from the same public table controls as relations. */
public final class FactionMembershipsView extends LinearLayout {
    private record Row(UiTableRow view, UiDisplayText name, UiDisplayText membership,
                       UiSelectButton rank, UiActionButton toggle) {}
    private final UiTheme theme;
    private final Supplier<FactionCatalog> catalog;
    private final Supplier<Map<UUID, Integer>> memberships;
    private final BooleanSupplier catalogLoaded;
    private final BiConsumer<UUID, Boolean> join;
    private final BiConsumer<UUID, Integer> rank;
    private final UiTableColumns columns = new UiTableColumns(List.of(
            new UiTableColumns.Column("name", 590, 180, true),
            new UiTableColumns.Column("member", 120, 100, true),
            new UiTableColumns.Column("rank", 240, 150, true),
            new UiTableColumns.Column("action", 120, 110, false)));
    private final UiTable table;
    private final UiColumnFilterPopover filters;
    private final UiDisplayText count;
    private final UiTableItems<UUID, UUID> items;
    private final Map<UUID, Row> rows = new LinkedHashMap<>();
    private List<UUID> displayedOrder = List.of();
    private BooleanSupplier editable = () -> true;
    private ColumnFilters.Choice memberFilter = ColumnFilters.Choice.all();
    private ColumnFilters.NumberRange rankFilter = ColumnFilters.NumberRange.unbounded();
    private String query = "", sort = "name";
    private int direction;
    private UiContextMenu rankMenu;
    private long menuGeneration;
    private FactionCatalog displayedCatalog;
    private boolean disposed;

    public FactionMembershipsView(Context context, UiTheme theme, Supplier<FactionCatalog> catalog,
                                   Supplier<Map<UUID, Integer>> memberships, BooleanSupplier catalogLoaded,
                                   BiConsumer<UUID, Boolean> join, BiConsumer<UUID, Integer> rank) {
        super(context); this.theme = theme; this.catalog = catalog; this.memberships = memberships;
        this.catalogLoaded = catalogLoaded; this.join = join; this.rank = rank; setOrientation(VERTICAL);
        count = new UiDisplayText(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        count.setGravity(Gravity.CENTER_VERTICAL);
        addView(count, new LayoutParams(-1, px(32)));
        filters = new UiColumnFilterPopover(context, this, theme);
        table = new UiTable(context, theme, columns, List.of(
                UiTableColumn.interactive("name", tr("faction"), () -> sort("name"), this::filterName),
                UiTableColumn.interactive("member", tr("membership_state"), () -> sort("member"), this::filterMember),
                UiTableColumn.interactive("rank", tr("level"), () -> sort("rank"), this::filterRank),
                UiTableColumn.label("action", tr("action"))), UiTableItems.body(context));
        items = new UiTableItems<>(table, Function.identity(), this::createItemRow);
        table.onDismiss(this::dismissRankMenu);
        table.onDismiss(filters::dismiss);
        table.setOnScrollChanged(this::dismissRankMenu);
        addView(table, new LayoutParams(-1, 0, 1));
    }

    public void setEditable(BooleanSupplier value) { editable = Objects.requireNonNull(value); refreshInputState(); }
    private boolean canEdit() { return !disposed && editable.getAsBoolean(); }
    private boolean exists(UUID id) { return catalogLoaded.getAsBoolean() && catalog.get().find(id).isPresent(); }

    public void refresh() {
        if (disposed) return;
        FactionCatalog definitions = catalog.get(); Map<UUID, Integer> values = memberships.get();
        if (!Objects.equals(displayedCatalog, definitions)) { displayedCatalog = definitions; dismissRankMenu(); }
        LinkedHashSet<UUID> live = new LinkedHashSet<>(); definitions.factions().forEach(f -> live.add(f.id())); live.addAll(values.keySet());
        Predicate<UUID> visible = id -> {
            String name = definitions.find(id).map(f -> f.name().plain()).orElse("");
            Integer level = values.get(id);
            return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                    && memberFilter.matches(Boolean.toString(values.containsKey(id)))
                    && rankFilter.matches(level == null ? null : level.doubleValue());
        };
        List<UUID> ordered = new ArrayList<>(live);
        if (direction != 0) {
            Comparator<UUID> comparator = switch (sort) {
                case "member" -> Comparator.comparing(values::containsKey);
                case "rank" -> Comparator.comparing(values::get, Comparator.nullsFirst(Integer::compareTo));
                default -> Comparator.comparing(id -> definitions.find(id).map(f -> f.name().plain()).orElse(id.toString()), String.CASE_INSENSITIVE_ORDER);
            };
            if (direction == 2) comparator = comparator.reversed();
            ordered.sort(comparator.thenComparing(UUID::toString));
        }
        List<UUID> displayed = ordered.stream().filter(visible).toList();
        if (!displayedOrder.equals(displayed)) dismissRankMenu();
        displayedOrder = displayed;
        count.setText(tr("visible_count", displayed.size(), live.size()));
        items.replace(ordered, visible);
        table.setSort(sort, direction);
        table.setFilterActive("name", !query.isEmpty());
        table.setFilterActive("member", memberFilter.active()); table.setFilterActive("rank", rankFilter.active());
        refreshInputState();
    }

    private UiTableItems.Row<UUID> createItemRow(UUID key) {
        Row row = createRow(key);
        rows.put(key, row);
        return new UiTableItems.Row<>() {
            @Override public View view() { return row.view(); }
            @Override public void bind(UUID id, int index) {
                var definition = catalog.get().find(id);
                Map<UUID, Integer> values = memberships.get();
                boolean joined = values.containsKey(id); Integer level = values.get(id);
                row.name().setText(definition.map(f -> FactionTextViews.display(f.name()))
                        .orElseGet(() -> tr(catalogLoaded.getAsBoolean() ? "deleted" : "unavailable")));
                row.name().setTooltipText(row.name().getText());
                row.membership().setText(tr(joined ? "joined" : "not_joined"));
                String rankLabel = !joined ? tr("not_joined") : definition.filter(f -> !f.ranks().isEmpty())
                        .map(f -> f.rankName(level).map(name -> level + " · " + name.plain())
                                .orElseGet(() -> level == 0 ? tr("choose_rank") : tr("unregistered_level", level)))
                        .orElseGet(() -> level == 0 ? tr("no_ranks") : tr("unregistered_level", level));
                row.rank().setValue(rankLabel); row.rank().setTooltipText(rankLabel);
                row.toggle().setText(tr(joined ? "leave_faction" : "join_faction")); row.toggle().setSelected(joined);
                boolean shown = index >= 0, exists = exists(id), enabled = canEdit();
                row.toggle().setEnabled(enabled && shown && (joined || exists));
                row.rank().setEnabled(enabled && shown && joined && exists
                        && definition.filter(f -> !f.ranks().isEmpty()).isPresent());
            }
            @Override public void dispose() {
                row.rank().setEnabled(false); row.toggle().setEnabled(false);
                rows.remove(key, row);
            }
        };
    }

    private Row createRow(UUID id) {
        Context context = getContext();
        UiDisplayText name = UiTableStyle.cell(context, "", theme);
        UiDisplayText membership = UiTableStyle.cell(context, "", theme);
        UiSelectButton rank = new UiSelectButton(context, "", UiTableStyle.CELL_TEXT_SIZE_DP, () -> 2, theme);
        rank.setContentDescription(tr("level")); rank.setOnClickListener(view -> showRanks(id));
        FrameLayout rankCell = new FrameLayout(context);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-1, px(34), Gravity.CENTER_VERTICAL);
        rankCell.addView(rank, rp);
        UiActionButton toggle = UiActionButton.createThemed(context, theme, "", UiActionButton.Role.SECONDARY, null);
        UIUtils.setLockedTextSize(toggle, UiTableStyle.CELL_TEXT_SIZE_DP);
        toggle.setSingleLine(true); toggle.setEllipsize(TextUtils.TruncateAt.END);
        toggle.setOnClickListener(view -> {
            if (!canEdit()) return;
            boolean joined = memberships.get().containsKey(id);
            if (joined || exists(id)) { suspend(); join.accept(id, !joined); }
        });
        FrameLayout action = new FrameLayout(context);
        action.addView(toggle, new FrameLayout.LayoutParams(px(96), px(32), Gravity.CENTER));
        UiTableRow row = table.createRow(Map.of("name", name, "member", membership, "rank", rankCell, "action", action));
        return new Row(row, name, membership, rank, toggle);
    }

    private void showRanks(UUID id) {
        suspend();
        if (!canEdit() || !exists(id) || !memberships.get().containsKey(id)) return;
        FactionDefinition definition = catalog.get().find(id).orElseThrow();
        if (definition.ranks().isEmpty()) return;
        FrameLayout host = menuHost(); if (host == null) return;
        long generation = menuGeneration;
        UiContextMenu menu = new UiContextMenu(getContext(), theme); rankMenu = menu;
        new TreeMap<>(definition.ranks()).forEach((level, name) -> {
            var label = new icyllis.modernui.text.SpannableStringBuilder();
            label.append(level + " · ").append(FactionTextViews.display(name));
            menu.addChoiceItem(label, Objects.equals(memberships.get().get(id), level), () -> {
                if (disposed || generation != menuGeneration || rankMenu != menu || !menu.isOpen()
                        || !canEdit() || !exists(id) || !memberships.get().containsKey(id)
                        || catalog.get().find(id).filter(f -> f.ranks().containsKey(level)).isEmpty()) return;
                dismissRankMenu(); rank.accept(id, level);
            });
        });
        menu.showBelow(rows.get(id).rank(), host);
    }
    private FrameLayout menuHost() {
        for (View view = this; view.getParent() instanceof View parent;) { view = parent; if (view instanceof FrameLayout host) return host; }
        return getRootView() instanceof FrameLayout host ? host : null;
    }
    public void refreshInputState() {
        if (disposed) return;
        Map<UUID, Integer> values = memberships.get(); boolean enabled = canEdit();
        rows.forEach((id, row) -> {
            boolean visible = row.view().getVisibility() == VISIBLE, joined = values.containsKey(id), exists = exists(id);
            row.toggle().setEnabled(enabled && visible && (joined || exists));
            row.rank().setEnabled(enabled && visible && joined && exists
                    && catalog.get().find(id).filter(f -> !f.ranks().isEmpty()).isPresent());
        });
        if (!enabled) dismissRankMenu();
    }
    private void sort(String key) {
        suspend(); UiTable.Sort next = table.cycleSort(key);
        sort = next.column(); direction = next.direction(); refresh();
    }
    private void filterName(View anchor) { suspend(); filters.showText(anchor, tr("faction"), query, value -> { query = value; refresh(); }); }
    private void filterMember(View anchor) {
        suspend(); LinkedHashMap<String, String> choices = new LinkedHashMap<>(); choices.put("true", tr("joined")); choices.put("false", tr("not_joined"));
        filters.showToggleChoices(anchor, choices, memberFilter, value -> { memberFilter = value; refresh(); });
    }
    private void filterRank(View anchor) {
        suspend(); filters.showNumber(anchor, tr("level"), rankFilter, value -> {
            if (!validBound(value.minimum()) || !validBound(value.maximum())) throw new IllegalArgumentException("Integer rank range required");
            rankFilter = value; refresh();
        });
    }
    private static boolean validBound(Double value) { return value == null || Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value); }
    private void dismissRankMenu() { menuGeneration++; UiContextMenu previous = rankMenu; rankMenu = null; if (previous != null) previous.dismiss(); }
    public void suspend() { table.dismiss(); }
    public void dispose() {
        if (disposed) return;
        disposed = true; suspend();
        items.dispose(); table.dispose(); displayedOrder = List.of();
    }
    @Override protected void onDetachedFromWindow() { suspend(); super.onDetachedFromWindow(); }
}
