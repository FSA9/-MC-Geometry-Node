package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.query.ColumnFilters;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

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
    private final UiTableFilterHeader nameHeader, memberHeader, rankHeader;
    private final UiColumnFilterPopover filters;
    private final UiDisplayText count;
    private final LinearLayout body;
    private final Map<UUID, Row> rows = new LinkedHashMap<>();
    private BooleanSupplier editable = () -> true;
    private ColumnFilters.Choice memberFilter = ColumnFilters.Choice.all();
    private ColumnFilters.NumberRange rankFilter = ColumnFilters.NumberRange.unbounded();
    private String query = "", sort = "name";
    private int direction;
    private UiPopover rankMenu;
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
        LinearLayout table = new LinearLayout(context); table.setOrientation(VERTICAL);
        UiTableRow header = UiTableStyle.header(context, columns, theme);
        nameHeader = new UiTableFilterHeader(context, tr("faction"), () -> sort("name"), this::filterName, theme);
        memberHeader = new UiTableFilterHeader(context, tr("membership_state"), () -> sort("member"), this::filterMember, theme);
        rankHeader = new UiTableFilterHeader(context, tr("level"), () -> sort("rank"), this::filterRank, theme);
        header.addView(nameHeader); header.addView(memberHeader); header.addView(rankHeader);
        header.addView(UiTableStyle.cell(context, tr("action"), theme));
        table.addView(header, new LayoutParams(-1, px(UiTableStyle.DETAILED_HEADER_HEIGHT)));
        body = new LinearLayout(context); body.setOrientation(VERTICAL);
        UiScrollView scroll = new UiScrollView(context, theme.scroll());
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> dismissRankMenu());
        table.addView(scroll, new LayoutParams(-1, 0, 1));
        addView(new UiTableViewport(context, columns, table, theme), new LayoutParams(-1, 0, 1));
    }

    public void setEditable(BooleanSupplier value) { editable = Objects.requireNonNull(value); refreshInputState(); }
    private boolean canEdit() { return !disposed && editable.getAsBoolean(); }
    private boolean exists(UUID id) { return catalogLoaded.getAsBoolean() && catalog.get().find(id).isPresent(); }

    public void refresh() {
        if (disposed) return;
        FactionCatalog definitions = catalog.get(); Map<UUID, Integer> values = memberships.get();
        if (!Objects.equals(displayedCatalog, definitions)) { displayedCatalog = definitions; dismissRankMenu(); }
        LinkedHashSet<UUID> live = new LinkedHashSet<>(); definitions.factions().forEach(f -> live.add(f.id())); live.addAll(values.keySet());
        for (UUID id : List.copyOf(rows.keySet())) if (!live.contains(id)) body.removeView(rows.remove(id).view());
        List<UUID> visible = live.stream().filter(id -> {
            String name = definitions.find(id).map(f -> f.name().plain()).orElse("");
            Integer level = values.get(id);
            return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                    && memberFilter.matches(Boolean.toString(values.containsKey(id)))
                    && rankFilter.matches(level == null ? null : level.doubleValue());
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        count.setText(tr("visible_count", visible.size(), live.size()));
        if (direction != 0) {
            Comparator<UUID> comparator = switch (sort) {
                case "member" -> Comparator.comparing(values::containsKey);
                case "rank" -> Comparator.comparing(values::get, Comparator.nullsFirst(Integer::compareTo));
                default -> Comparator.comparing(id -> definitions.find(id).map(f -> f.name().plain()).orElse(id.toString()), String.CASE_INSENSITIVE_ORDER);
            };
            if (direction == 2) comparator = comparator.reversed();
            visible.sort(comparator.thenComparing(UUID::toString));
        }
        Set<UUID> displayed = new HashSet<>(visible);
        rows.forEach((id, row) -> { if (!displayed.contains(id)) row.view().setVisibility(GONE); });
        int index = 0;
        for (UUID id : visible) {
            Row row = rows.computeIfAbsent(id, this::createRow);
            var definition = definitions.find(id); boolean joined = values.containsKey(id); Integer level = values.get(id);
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
            row.view().setDataStyle(index % 2 != 0);
            row.view().setVisibility(VISIBLE);
            if (row.view().getParent() != body || body.indexOfChild(row.view()) != index) {
                dismissRankMenu(); if (row.view().getParent() == body) body.removeView(row.view());
                body.addView(row.view(), index, new LayoutParams(-1, px(UiTableStyle.DETAILED_ROW_HEIGHT)));
            }
            index++;
        }
        title(nameHeader, "faction", "name"); title(memberHeader, "membership_state", "member"); title(rankHeader, "level", "rank");
        nameHeader.setFilterActive(!query.isEmpty()); memberHeader.setFilterActive(memberFilter.active()); rankHeader.setFilterActive(rankFilter.active());
        refreshInputState();
    }

    private Row createRow(UUID id) {
        Context context = getContext(); UiTableRow row = new UiTableRow(context, columns, theme);
        UiDisplayText name = UiTableStyle.cell(context, "", theme);
        UIUtils.setLockedTextSize(name, UiMetrics.TEXT_BODY);
        name.setPadding(px(12), 0, px(12), 0);
        UiDisplayText membership = UiTableStyle.cell(context, "", theme);
        UIUtils.setLockedTextSize(membership, UiMetrics.TEXT_BODY);
        membership.setPadding(px(12), 0, px(12), 0);
        UiSelectButton rank = new UiSelectButton(context, "", UiMetrics.TEXT_BODY, () -> 2, theme);
        rank.setContentDescription(tr("level")); rank.setOnClickListener(view -> showRanks(id));
        FrameLayout rankCell = new FrameLayout(context);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-1, px(34), Gravity.CENTER_VERTICAL);
        rp.leftMargin = px(12); rp.rightMargin = px(12); rankCell.addView(rank, rp);
        UiActionButton toggle = UiActionButton.createThemed(context, theme, "", UiActionButton.Role.SECONDARY, null);
        UIUtils.setLockedTextSize(toggle, UiMetrics.TEXT_BODY);
        toggle.setPadding(px(4), 0, px(4), 0);
        toggle.setSingleLine(true); toggle.setEllipsize(TextUtils.TruncateAt.END);
        toggle.setOnClickListener(view -> {
            if (!canEdit()) return;
            boolean joined = memberships.get().containsKey(id);
            if (joined || exists(id)) { suspend(); join.accept(id, !joined); }
        });
        FrameLayout action = new FrameLayout(context);
        action.addView(toggle, new FrameLayout.LayoutParams(px(96), px(32), Gravity.CENTER));
        row.addView(name); row.addView(membership); row.addView(rankCell); row.addView(action);
        return new Row(row, name, membership, rank, toggle);
    }

    private void showRanks(UUID id) {
        suspend();
        if (!canEdit() || !exists(id) || !memberships.get().containsKey(id)) return;
        FactionDefinition definition = catalog.get().find(id).orElseThrow();
        if (definition.ranks().isEmpty()) return;
        FrameLayout host = menuHost(); if (host == null) return;
        LinearLayout content = new LinearLayout(getContext()); content.setOrientation(VERTICAL);
        long generation = menuGeneration;
        UiPopover menu = new UiPopover(getContext(), content, 300, 360, theme); rankMenu = menu;
        new TreeMap<>(definition.ranks()).forEach((level, name) -> {
            TextView option = UiMenuItems.row(getContext(), "", Objects.equals(memberships.get().get(id), level), true, () -> {
                if (disposed || generation != menuGeneration || rankMenu != menu || !menu.isOpen()
                        || !canEdit() || !exists(id) || !memberships.get().containsKey(id)
                        || catalog.get().find(id).filter(f -> f.ranks().containsKey(level)).isEmpty()) return;
                dismissRankMenu(); rank.accept(id, level);
            }, theme);
            var label = new icyllis.modernui.text.SpannableStringBuilder();
            label.append(level + " · ").append(FactionTextViews.display(name)); option.setText(label);
            UIUtils.setLockedTextSize(option, UiMetrics.TEXT_BODY);
            content.addView(option, new LayoutParams(-1, px(32)));
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
    private void title(UiTableFilterHeader header, String key, String column) {
        if (header.getChildAt(0) instanceof UiActionButton label)
            label.setText(tr(key) + (!sort.equals(column) || direction == 0 ? "" : direction == 1 ? " ↑" : " ↓"));
    }
    private void sort(String key) { suspend(); direction = sort.equals(key) ? (direction + 1) % 3 : 1; sort = key; refresh(); }
    private void filterName() { suspend(); filters.showText(nameHeader.filterButton(), tr("faction"), query, value -> { query = value; refresh(); }); }
    private void filterMember() {
        suspend(); LinkedHashMap<String, String> choices = new LinkedHashMap<>(); choices.put("true", tr("joined")); choices.put("false", tr("not_joined"));
        filters.showChoices(memberHeader.filterButton(), tr("membership_state"), choices, memberFilter, value -> { memberFilter = value; refresh(); });
    }
    private void filterRank() {
        suspend(); filters.showNumber(rankHeader.filterButton(), tr("level"), rankFilter, value -> {
            if (!validBound(value.minimum()) || !validBound(value.maximum())) throw new IllegalArgumentException("Integer rank range required");
            rankFilter = value; refresh();
        });
    }
    private static boolean validBound(Double value) { return value == null || Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value); }
    private void dismissRankMenu() { menuGeneration++; UiPopover previous = rankMenu; rankMenu = null; if (previous != null) previous.dismiss(); }
    public void suspend() { dismissRankMenu(); filters.dismiss(); }
    public void dispose() { if (disposed) return; disposed = true; suspend(); rows.clear(); }
    @Override protected void onDetachedFromWindow() { suspend(); super.onDetachedFromWindow(); }
}
