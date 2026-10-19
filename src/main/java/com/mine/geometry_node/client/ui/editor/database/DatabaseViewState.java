package com.mine.geometry_node.client.ui.editor.database;

import com.mine.geometry_node.client.ui.workspace.state.*;
import java.util.Map;

/** Presentation preferences only: never query results, selections or business objects. */
public final class DatabaseViewState {
    private DatabaseViewState() {}
    public static final String GLOBAL = "global";
    public static final UiStateKey<com.mine.geometry_node.core.query.ColumnFilters> WORLD_COLUMN_FILTERS = columnFilters("database:world.column_filters");
    public static final UiStateKey<com.mine.geometry_node.core.query.ColumnFilters> LIBRARY_COLUMN_FILTERS = columnFilters("database:library.column_filters");
    private static UiStateKey<com.mine.geometry_node.core.query.ColumnFilters> columnFilters(String id) {
        return new UiStateKey<>(id, 1, UiStateLifetime.MEMORY,
                com.mine.geometry_node.core.query.ColumnFilters.class,
                com.mine.geometry_node.core.query.ColumnFilters::empty, value -> value);
    }
    public record World(String search, String tags, String source, String range, String dimension,
                        String provider, String type, String typeProvider, String lifetime,
                        String category, String extraProvider, String sort, boolean ascending) {
        public World {
            search = text(search); tags = bounded(tags, 4096); source = text(source); range = text(range);
            dimension = text(dimension); provider = text(provider); type = text(type);
            typeProvider = text(typeProvider); lifetime = text(lifetime); category = text(category);
            extraProvider = text(extraProvider); sort = text(sort);
            if (!java.util.Set.of("GLOBAL", "LOADED").contains(range)) range = "GLOBAL";
            if (!java.util.Set.of("name", "type", "dimension", "position", "lifetime", "tags").contains(sort)) sort = "name";
        }
    }
    public record Library(String search, String type, String tag, boolean recursive,
                          boolean grouped, String activeFolder) {
        public Library { search = bounded(search, 4096); type = text(type); tag = bounded(tag, 4096); activeFolder = text(activeFolder); }
    }
    public record Split(float extentDp) {
        public Split { extentDp = Float.isFinite(extentDp) ? Math.max(0, extentDp) : 216; }
    }
    public record Sidebar(float extentDp, boolean visible) {
        public Sidebar { extentDp = Float.isFinite(extentDp) ? Math.max(180, Math.min(4096, extentDp)) : 320; }
    }
    public record Tab(boolean world) {}
    public static final UiStateKey<Split> WORLD_SPLIT = split("database:world.navigation");
    public static final UiStateKey<Split> LIBRARY_SPLIT = split("database:library.navigation");
    public static final UiStateKey<Split> LIBRARY_TREE_SPLIT = new UiStateKey<>("database:library.tree", 1,
            UiStateLifetime.PERSISTENT, Split.class, () -> new Split(360), value -> value);
    public static final UiStateKey<Sidebar> SIDEBAR = new UiStateKey<>("database:sidebar", 1,
            UiStateLifetime.PERSISTENT, Sidebar.class, () -> new Sidebar(320, true), value -> value);
    public static final UiStateKey<Tab> TAB = new UiStateKey<>("database:tab", 1,
            UiStateLifetime.MEMORY, Tab.class, () -> new Tab(true), value -> value);
    private static UiStateKey<Split> split(String id) {
        return new UiStateKey<>(id, 1, UiStateLifetime.PERSISTENT, Split.class, () -> new Split(216), value -> value);
    }
    public record Widths(Map<String, Float> columns) {
        public Widths {
            var clean = new java.util.LinkedHashMap<String, Float>();
            if (columns != null) columns.forEach((id, width) -> {
                if (id != null && !id.isBlank() && width != null && Float.isFinite(width) && width > 0)
                    clean.put(id, Math.min(4096f, width));
            });
            columns = Map.copyOf(clean);
        }
    }
    public static final UiStateKey<World> WORLD = new UiStateKey<>("database:world.filters", 1,
            UiStateLifetime.MEMORY, World.class,
            () -> new World("", "", "", "GLOBAL", "", "", "", "", "", "", "", "name", true),
            value -> value);
    public static final UiStateKey<Library> LIBRARY = new UiStateKey<>("database:library.filters", 1,
            UiStateLifetime.MEMORY, Library.class,
            () -> new Library("", "", "", false, false, ""), value -> value);
    public static final UiStateKey<Widths> WORLD_WIDTHS = widths("database:world.columns");
    public static final UiStateKey<Widths> LIBRARY_WIDTHS = widths("database:library.columns");
    private static UiStateKey<Widths> widths(String id) {
        return new UiStateKey<>(id, 1, UiStateLifetime.PERSISTENT, Widths.class,
                () -> new Widths(Map.of()), value -> new Widths(value.columns()));
    }
    private static String text(String value) { return bounded(value, 1024); }
    private static String bounded(String value, int maximum) {
        return value == null ? "" : value.substring(0, Math.min(maximum, value.length()));
    }
}
