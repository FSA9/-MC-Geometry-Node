package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.query.AssetListQuery;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Asset-specific table columns and filter editors; shared table components own layout and interaction. */
final class AssetListTable {
    final UiTableColumns columns = createColumns();
    static UiTableColumns createColumns() { return new UiTableColumns(List.of(
            UiTableStyle.selectionColumn(), new UiTableColumns.Column("type", 78, 60, true),
            new UiTableColumns.Column("name", 220, 140, true),
            new UiTableColumns.Column("description", 260, 140, true),
            new UiTableColumns.Column("tags", 180, 100, true),
            new UiTableColumns.Column("favorite", 72, 64, true))); }
    final UiTableRow header;
    private final UiColumnFilterPopover filters;
    private final UiCheckBox selectAll;
    private final Map<AssetListQuery.Column, UiTableFilterHeader> headers = new EnumMap<>(AssetListQuery.Column.class);
    private final Map<String, String> typeOptions = new LinkedHashMap<>();
    private final Runnable changed;
    private AssetListQuery query = AssetListQuery.empty();

    AssetListTable(Context context, View owner, Runnable changed, Consumer<Boolean> select) {
        this.changed = changed;
        filters = new UiColumnFilterPopover(context, owner);
        header = UiTableStyle.header(context, columns);
        selectAll = new UiCheckBox(context);
        selectAll.setContentDescription(tr("geometry_node.database.select_all"));
        selectAll.setOnCheckedChangeListener((view, checked) -> select.accept(checked));
        header.addView(UiTableStyle.selectionCell(context, selectAll));
        addColumn(context, AssetListQuery.Column.TYPE, tr("geometry_node.database.type"));
        addColumn(context, AssetListQuery.Column.NAME, tr("geometry_node.data_library.column_name"));
        addColumn(context, AssetListQuery.Column.DESCRIPTION, tr("geometry_node.asset_library.column.description"));
        addColumn(context, AssetListQuery.Column.TAGS, tr("geometry_node.data_library.column_tags"));
        addColumn(context, AssetListQuery.Column.FAVORITE, tr("geometry_node.asset_library.column.favorite"));
    }

    AssetListQuery query() { return query; }
    void dismiss() { filters.dismiss(); }
    void clear() { query = query.withoutFilters(); updateIndicators(); }
    void setEntries(List<AssetEntry> entries) {
        typeOptions.clear();
        for (AssetEntry entry : entries) {
            String key = AssetListQuery.typeKey(entry);
            var graph = GraphTypeRegistry.INSTANCE.get(entry.metadata().variantId());
            String label = graph != null ? Component.translatable(graph.translationKey()).getString() : Component.translatableWithFallback(
                    "geometry_node.asset_library.filter.type." + entry.type().id(), entry.type().id()).getString();
            typeOptions.putIfAbsent(key, label);
        }
    }
    void updateSelection(int selected, int total) {
        selectAll.setChecked(total > 0 && selected == total);
        selectAll.setIndeterminate(selected > 0 && selected < total);
        selectAll.setEnabled(total > 0);
    }
    private void addColumn(Context context, AssetListQuery.Column column, String label) {
        UiTableFilterHeader cell = new UiTableFilterHeader(context, label, () -> apply(query.sortBy(column)),
                () -> showFilter(column, label));
        headers.put(column, cell);
        header.addView(cell);
    }
    private void showFilter(AssetListQuery.Column column, String title) {
        View anchor = headers.get(column).filterButton();
        switch (column) {
            case NAME -> filters.showText(anchor, title, query.name(), text -> apply(query.withName(text)));
            case DESCRIPTION -> filters.showText(anchor, title, query.description(), text -> apply(query.withDescription(text)));
            case TYPE -> filters.showChoices(anchor, title, typeOptions, query.types(), choice -> apply(query.withTypes(choice)));
            case TAGS -> filters.showTags(anchor, query.includedTags(), query.excludedTags(),
                    (include, exclude) -> apply(query.withTags(include, exclude)));
            case FAVORITE -> filters.showToggleChoices(anchor, Map.of("true", tr("geometry_node.asset_library.filter.favorited"),
                    "false", tr("geometry_node.asset_library.filter.not_favorite")), query.favorites(),
                    choice -> apply(query.withFavorites(choice)));
        }
    }
    private void apply(AssetListQuery value) { query = value; updateIndicators(); changed.run(); }
    private void updateIndicators() {
        headers.forEach((column, header) -> header.setFilterActive(switch (column) {
            case NAME -> !query.name().isEmpty();
            case DESCRIPTION -> !query.description().isEmpty();
            case TYPE -> query.types().active();
            case TAGS -> !query.includedTags().isEmpty() || !query.excludedTags().isEmpty();
            case FAVORITE -> query.favorites().active();
        }));
    }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
