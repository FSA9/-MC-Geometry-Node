package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.*;
import com.mine.geometry_node.core.utils.ColumnFilters;
import net.minecraft.world.phys.Vec3;

/** Empty selections mean unrestricted. Nearby origin is deliberately absent from the wire request. */
public record WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                               String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                               double radius, int limit, ColumnFilters columns,
                               Set<String> searchFallbackTypes, Set<String> columnNameFallbackTypes,
                               String registryType, WorldObjectGraphFilter graphFilter,
                               WorldObjectEntityFilter entityFilter) {
    public enum Range { GLOBAL, CURRENT_DIMENSION, LOADED, NEARBY }
    public static final int MAX_RESULTS = 256, MAX_FILTERS = 16, MAX_FILTER_TEXT = 1024;
    // Vanilla ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE is 32767 bytes.
    // Reserve >2 KiB for payload ID/framing; estimate includes request ID and all primitive/count fields.
    public static final int MAX_REQUEST_BYTES = 30 * 1024, MAX_TOTAL_TEXT = 8192;
    public static final double MAX_RADIUS = 4096;
    public WorldObjectQuery {
        Objects.requireNonNull(columns); Objects.requireNonNull(range); check(dimension); check(name); check(registryType);
        Objects.requireNonNull(graphFilter);
        Objects.requireNonNull(entityFilter);
        providers = filters(providers); types = filters(types); tags = filters(tags);
        sources = filters(sources); lifetimes = filters(lifetimes);
        searchFallbackTypes = fallbackTypes(searchFallbackTypes);
        columnNameFallbackTypes = fallbackTypes(columnNameFallbackTypes);
        int textBudget = dimension.length() + name.length();
        for (Set<String> values : List.of(providers, types, tags, sources, lifetimes))
            for (String value : values) textBudget += value.length();
        int wireBytes = 128 + ColumnFilters.wireTextBytes(dimension) + ColumnFilters.wireTextBytes(name)
                + columns.estimatedWireBytes() + ColumnFilters.wireTextBytes(registryType)
                + ColumnFilters.wireTextBytes(graphFilter.type()) + ColumnFilters.wireTextBytes(graphFilter.name());
        for (Set<String> values : List.of(providers, types, tags, sources, lifetimes))
            for (String text : values) wireBytes += ColumnFilters.wireTextBytes(text);
        int fallbackText = 0;
        for (Set<String> values : List.of(searchFallbackTypes, columnNameFallbackTypes))
            for (String text : values) { fallbackText += text.length(); wireBytes += ColumnFilters.wireTextBytes(text); }
        if (textBudget + columns.textLength() + fallbackText + registryType.length()
                + graphFilter.type().length() + graphFilter.name().length() > MAX_TOTAL_TEXT || wireBytes > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("World query combined request budget exceeded");
        if (textBudget > 4096) throw new IllegalArgumentException("World query request text budget exceeded");
        if (!Double.isFinite(radius) || radius < 0 || radius > MAX_RADIUS || limit < 1 || limit > MAX_RESULTS)
            throw new IllegalArgumentException("Invalid world query limits");
    }
    /** Existing callers retain the combined world/entity scope. */
    public WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                            String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                            double radius, int limit, ColumnFilters columns,
                            Set<String> searchFallbackTypes, Set<String> columnNameFallbackTypes,
                            String registryType, WorldObjectGraphFilter graphFilter) {
        this(range, dimension, providers, types, name, tags, sources, lifetimes, radius, limit, columns,
                searchFallbackTypes, columnNameFallbackTypes, registryType, graphFilter, WorldObjectEntityFilter.empty());
    }
    /** Compatibility constructor for requests without bound graph predicates. */
    public WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                            String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                            double radius, int limit, ColumnFilters columns,
                            Set<String> searchFallbackTypes, Set<String> columnNameFallbackTypes, String registryType) {
        this(range, dimension, providers, types, name, tags, sources, lifetimes, radius, limit, columns,
                searchFallbackTypes, columnNameFallbackTypes, registryType, WorldObjectGraphFilter.empty());
    }
    /** Compatibility constructor for requests without a registry-type keyword. */
    public WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                            String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                            double radius, int limit, ColumnFilters columns,
                            Set<String> searchFallbackTypes, Set<String> columnNameFallbackTypes) {
        this(range, dimension, providers, types, name, tags, sources, lifetimes, radius, limit, columns,
                searchFallbackTypes, columnNameFallbackTypes, "");
    }
    /** Compatibility constructor for requests without column filters. */
    public WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                            String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                            double radius, int limit) {
        this(range, dimension, providers, types, name, tags, sources, lifetimes, radius, limit, ColumnFilters.empty());
    }
    /** Compatibility constructor for requests without localized-name fallback types. */
    public WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                            String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                            double radius, int limit, ColumnFilters columns) {
        this(range, dimension, providers, types, name, tags, sources, lifetimes, radius, limit, columns, Set.of(), Set.of());
    }
    public static String rawType(WorldObjectEntry entry) {
        return WorldObjectEntry.key(entry.provider(), "", entry.type());
    }
    private static Set<String> fallbackTypes(Set<String> values) {
        if (values.size() > ColumnFilters.MAX_CHOICES) throw new IllegalArgumentException("Too many name fallback types");
        values.forEach(WorldObjectQuery::check);
        return Set.copyOf(values);
    }
    public static String filterType(WorldObjectEntry entry) {
        var category = WorldObjectCategories.forEntry(entry);
        return category != null ? "category:" + category.id() : rawType(entry);
    }
    /** Current world metadata exposes no scalar value; positions and IDs are never numeric values. */
    public static Double numericValue(WorldObjectEntry entry) { return null; }
    public static WorldObjectQuery defaults() {
        return new WorldObjectQuery(Range.GLOBAL, "", Set.of(), Set.of(), "", Set.of(),
                Set.of(), Set.of(), 128, MAX_RESULTS);
    }
    public WorldObjectQuery withEntityFilter(WorldObjectEntityFilter filter) {
        return new WorldObjectQuery(range, dimension, providers, types, name, tags, sources, lifetimes,
                radius, limit, columns, searchFallbackTypes, columnNameFallbackTypes, registryType, graphFilter, filter);
    }
    /** Empty means every dimension, except for the explicit current/nearby protocol ranges. */
    public String selectedDimension(String currentDimension) {
        return dimension.isEmpty() && (range == Range.CURRENT_DIMENSION || range == Range.NEARBY)
                ? currentDimension : dimension;
    }
    private static Set<String> filters(Set<String> values) {
        Set<String> result = Set.copyOf(values);
        if (result.size() > MAX_FILTERS) throw new IllegalArgumentException("Too many world query filters");
        result.forEach(WorldObjectQuery::check);
        return result;
    }
    private static void check(String value) {
        if (value == null || value.length() > MAX_FILTER_TEXT) throw new IllegalArgumentException("World query filter too long");
    }
    public boolean matchesRegistryType(WorldObjectEntry entry) {
        return matchesRegistryType(entry, registryType);
    }
    public static boolean matchesRegistryType(WorldObjectEntry entry, String keyword) {
        return keyword.isEmpty() || entry.registryType().toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }
    public boolean matchesMetadata(WorldObjectEntry entry) {
        String rawType = rawType(entry);
        String effectiveName = entry.name().isEmpty() && columnNameFallbackTypes.contains(rawType)
                ? columns.name() : entry.name();
        return entityFilter.matches(entry)
                && (providers.isEmpty() || providers.contains(entry.provider()))
                && (types.isEmpty() || types.contains(entry.type()))
                && (name.isEmpty() || (entry.name().isEmpty() ? searchFallbackTypes.contains(rawType)
                    : entry.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))))
                && entry.tags().containsAll(tags)
                && (sources.isEmpty() || sources.contains(entry.source()))
                && (lifetimes.isEmpty() || lifetimes.contains(entry.lifetime()))
                && matchesRegistryType(entry)
                && graphFilter.matches(entry.graphs())
                && columns.matches(effectiveName, filterType(entry), numericValue(entry), entry.dimension(),
                        entry.position().x, entry.position().y, entry.position().z, entry.lifetime(), entry.tags());
    }
    /** Bounds distance includes large objects intersecting the search sphere even if their pivot is outside. */
    public boolean withinRadius(WorldObjectEntry entry, Vec3 origin) {
        var b = entry.bounds();
        double x = Math.max(b.minX - origin.x, Math.max(0, origin.x - b.maxX));
        double y = Math.max(b.minY - origin.y, Math.max(0, origin.y - b.maxY));
        double z = Math.max(b.minZ - origin.z, Math.max(0, origin.z - b.maxZ));
        return x*x + y*y + z*z <= radius*radius;
    }
}
