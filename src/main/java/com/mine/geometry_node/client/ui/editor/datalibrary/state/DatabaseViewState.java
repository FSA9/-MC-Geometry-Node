package com.mine.geometry_node.client.ui.editor.datalibrary.state;

import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraphFilter;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter;
import java.util.Map;

/** Presentation preferences and in-memory selection keys; never query results or business objects. */
public final class DatabaseViewState {
    private DatabaseViewState() {}
    public static final String GLOBAL = "global";
    public static final UiStateKey<com.mine.geometry_node.core.utils.ColumnFilters> WORLD_COLUMN_FILTERS = columnFilters("database:world.column_filters");
    public static final UiStateKey<com.mine.geometry_node.core.utils.ColumnFilters> ENTITY_COLUMN_FILTERS = columnFilters("globaldata:entities.column_filters");
    public static final UiStateKey<WorldObjectGraphFilter> ENTITY_GRAPH_FILTER = new UiStateKey<>(
            "globaldata:entities.graph_filter", 1, UiStateLifetime.MEMORY, WorldObjectGraphFilter.class,
            WorldObjectGraphFilter::empty, value -> value);
    public static final UiStateKey<WorldObjectEntityFilter> ENTITY_FILTER = new UiStateKey<>(
            "globaldata:entities.entity_filter", 1, UiStateLifetime.MEMORY, WorldObjectEntityFilter.class,
            () -> new WorldObjectEntityFilter(WorldObjectEntityFilter.Scope.ENTITIES,
                    WorldObjectEntityFilter.Load.ANY, WorldObjectEntityFilter.Registration.ANY),
            value -> new WorldObjectEntityFilter(WorldObjectEntityFilter.Scope.ENTITIES, value.load(), value.registration()));
    public static final UiStateKey<WorldObjectGraphFilter> WORLD_GRAPH_FILTER = new UiStateKey<>(
            "database:world.graph_filter", 1, UiStateLifetime.MEMORY, WorldObjectGraphFilter.class,
            WorldObjectGraphFilter::empty, value -> value);
    public static final UiStateKey<com.mine.geometry_node.core.utils.ColumnFilters> LIBRARY_COLUMN_FILTERS = columnFilters("database:library.column_filters");
    private static UiStateKey<com.mine.geometry_node.core.utils.ColumnFilters> columnFilters(String id) {
        return new UiStateKey<>(id, 1, UiStateLifetime.MEMORY,
                com.mine.geometry_node.core.utils.ColumnFilters.class,
                com.mine.geometry_node.core.utils.ColumnFilters::empty, value -> value);
    }
    public record World(String tags, String source, String range, String dimension,
                        String provider, String type, String typeProvider, String lifetime,
                        String category, String extraProvider, String sort, boolean ascending, String registryType) {
        public World {
            tags = bounded(tags, 4096); source = text(source); range = text(range);
            dimension = text(dimension); provider = text(provider); type = text(type);
            typeProvider = text(typeProvider); lifetime = text(lifetime); category = text(category);
            extraProvider = text(extraProvider); sort = text(sort);
            registryType = text(registryType);
            if (!java.util.Set.of("GLOBAL", "LOADED").contains(range)) range = "GLOBAL";
            if (!java.util.Set.of("name", "type", "registry_type", "dimension", "position", "lifetime", "tags", "graphs", "registered", "loaded", "updated").contains(sort)) sort = "name";
        }
        public World(String tags, String source, String range, String dimension,
                     String provider, String type, String typeProvider, String lifetime,
                     String category, String extraProvider, String sort, boolean ascending) {
            this(tags, source, range, dimension, provider, type, typeProvider, lifetime,
                    category, extraProvider, sort, ascending, "");
        }
    }
    public record Library(String type, String tag, boolean recursive, String activeFolder, String registryType) {
        public Library {
            type = text(type); tag = bounded(tag, 4096);
            activeFolder = text(activeFolder); registryType = text(registryType);
        }
    }
    public record Split(float extentDp) {
        public Split { extentDp = Float.isFinite(extentDp) ? Math.max(0, extentDp) : 216; }
    }
    public record Sidebar(float extentDp, boolean visible) {
        public Sidebar { extentDp = Float.isFinite(extentDp) ? Math.max(180, Math.min(4096, extentDp)) : 320; }
    }
    public enum Page { WORLD, LIBRARY, GLOBAL_DATA }
    public record Tab(Page page) {
        public Tab { if (page == null) page = Page.WORLD; }
        public Tab(boolean world) { this(world ? Page.WORLD : Page.LIBRARY); }
        public boolean world() { return page == Page.WORLD; }
    }
    public static final UiStateKey<Split> WORLD_SPLIT = split("database:world.navigation");
    public static final UiStateKey<Split> LIBRARY_SPLIT = split("database:library.navigation");
    public static final UiStateKey<Sidebar> ENTITY_SIDEBAR = new UiStateKey<>("globaldata:entities.sidebar", 1,
            UiStateLifetime.PERSISTENT, Sidebar.class, () -> new Sidebar(320, false), value -> value);
    public static final UiStateKey<Sidebar> FACTION_MEMBERS_SIDEBAR = new UiStateKey<>("globaldata:factions.members.sidebar", 1,
            UiStateLifetime.PERSISTENT, Sidebar.class, () -> new Sidebar(320, false), value -> value);
    public static final UiStateKey<Split> LIBRARY_TREE_SPLIT = new UiStateKey<>("database:library.tree", 1,
            UiStateLifetime.PERSISTENT, Split.class, () -> new Split(360), value -> value);
    public static final UiStateKey<Sidebar> SIDEBAR = new UiStateKey<>("database:sidebar", 1,
            UiStateLifetime.PERSISTENT, Sidebar.class, () -> new Sidebar(320, true), value -> value);
    public static final UiStateKey<Tab> TAB = new UiStateKey<>("database:tab", 2,
            UiStateLifetime.MEMORY, Tab.class, () -> new Tab(Page.WORLD), value -> value,
            (version, payload) -> {
                if (version != 1 || !payload.isJsonObject()) return null;
                var migrated = new com.google.gson.JsonObject();
                migrated.addProperty("page", payload.getAsJsonObject().has("world")
                        && !payload.getAsJsonObject().get("world").getAsBoolean() ? "LIBRARY" : "WORLD");
                return migrated;
            });
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
            () -> new World("", "", "GLOBAL", "", "", "", "", "", "", "", "name", true),
            value -> value);
    public static final UiStateKey<Library> LIBRARY = new UiStateKey<>("database:library.filters", 1,
            UiStateLifetime.MEMORY, Library.class,
            () -> new Library("", "", false, "", ""), value -> value);
    public static final UiStateKey<World> ENTITIES = new UiStateKey<>("globaldata:entities.filters", 1,
            UiStateLifetime.MEMORY, World.class,
            () -> new World("", "", "GLOBAL", "", "", "", "", "", "", "", "registry_type", true), value -> value);
    public record EntitySelection(java.util.Set<String> keys) {
        public EntitySelection { keys = keys == null ? java.util.Set.of() : java.util.Set.copyOf(keys); }
    }
    public static final UiStateKey<EntitySelection> ENTITY_SELECTION = new UiStateKey<>("globaldata:entities.selection", 1,
            UiStateLifetime.MEMORY, EntitySelection.class, () -> new EntitySelection(java.util.Set.of()), value -> value);
    public static final UiStateKey<Widths> ENTITY_WIDTHS = widths("globaldata:entities.columns");
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
