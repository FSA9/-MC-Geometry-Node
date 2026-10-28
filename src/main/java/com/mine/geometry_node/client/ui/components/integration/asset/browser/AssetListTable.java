package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.query.AssetListQuery;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.composite.table.*;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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
    final UiTable table;
    private final UiColumnFilterPopover filters;
    private final UiCheckBox selectAll;
    private final Map<String, String> typeOptions = new LinkedHashMap<>();
    private final Runnable changed;
    private AssetListQuery query = AssetListQuery.empty();

    AssetListTable(Context context, View owner, View content, Runnable changed, Consumer<Boolean> select) {
        this.changed = changed;
        filters = new UiColumnFilterPopover(context, owner);
        selectAll = new UiCheckBox(context);
        selectAll.setContentDescription(tr("geometry_node.database.select_all"));
        selectAll.setOnCheckedChangeListener((view, checked) -> select.accept(checked));
        table = new UiTable(context, UiThemes.EDITOR, columns, List.of(
                UiTableColumn.custom("selection", () -> UiTableStyle.selectionCell(context, selectAll)),
                column(AssetListQuery.Column.TYPE, tr("geometry_node.database.type")),
                column(AssetListQuery.Column.NAME, tr("geometry_node.data_library.column_name")),
                column(AssetListQuery.Column.DESCRIPTION, tr("geometry_node.asset_library.column.description")),
                column(AssetListQuery.Column.TAGS, tr("geometry_node.data_library.column_tags")),
                column(AssetListQuery.Column.FAVORITE, tr("geometry_node.asset_library.column.favorite"))), content);
        table.onDismiss(filters::dismiss);
        updateIndicators();
    }

    AssetListQuery query() { return query; }
    void dismiss() { table.dismiss(); }
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
    private UiTableColumn column(AssetListQuery.Column column, String label) {
        return UiTableColumn.interactive(id(column), label, () -> sortBy(column),
                anchor -> showFilter(column, label, anchor));
    }
    private void sortBy(AssetListQuery.Column column) {
        UiTable.Sort sort = table.toggleSort(id(column));
        apply(new AssetListQuery(query.name(), query.description(), query.types(), query.includedTags(),
                query.excludedTags(), query.favorites(), column, sort.direction() == 2));
    }
    private void showFilter(AssetListQuery.Column column, String title, View anchor) {
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
        for (AssetListQuery.Column column : AssetListQuery.Column.values()) table.setFilterActive(id(column), switch (column) {
            case NAME -> !query.name().isEmpty();
            case DESCRIPTION -> !query.description().isEmpty();
            case TYPE -> query.types().active();
            case TAGS -> !query.includedTags().isEmpty() || !query.excludedTags().isEmpty();
            case FAVORITE -> query.favorites().active();
        });
        table.setSort(id(query.sort()), query.descending() ? 2 : 1);
    }
    private static String id(AssetListQuery.Column column) { return column.name().toLowerCase(Locale.ROOT); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
