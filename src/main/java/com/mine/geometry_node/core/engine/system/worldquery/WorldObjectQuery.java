package com.mine.geometry_node.core.engine.system.worldquery;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Empty selections mean unrestricted. Nearby origin is deliberately absent from the wire request. */
public record WorldObjectQuery(Range range, String dimension, Set<String> providers, Set<String> types,
                               String name, Set<String> tags, Set<String> sources, Set<String> lifetimes,
                               double radius, int limit) {
    public enum Range { GLOBAL, CURRENT_DIMENSION, LOADED, NEARBY }
    public static final int MAX_RESULTS = 256, MAX_FILTERS = 16, MAX_FILTER_TEXT = 1024;
    public static final double MAX_RADIUS = 4096;
    public WorldObjectQuery {
        Objects.requireNonNull(range); check(dimension); check(name);
        providers = filters(providers); types = filters(types); tags = filters(tags);
        sources = filters(sources); lifetimes = filters(lifetimes);
        int textBudget = dimension.length() + name.length();
        for (Set<String> values : List.of(providers, types, tags, sources, lifetimes))
            for (String value : values) textBudget += value.length();
        if (textBudget > 4096) throw new IllegalArgumentException("World query request text budget exceeded");
        if (!Double.isFinite(radius) || radius < 0 || radius > MAX_RADIUS || limit < 1 || limit > MAX_RESULTS)
            throw new IllegalArgumentException("Invalid world query limits");
    }
    public static WorldObjectQuery defaults() {
        return new WorldObjectQuery(Range.GLOBAL, "", Set.of(), Set.of(), "", Set.of(),
                Set.of(), Set.of(), 128, MAX_RESULTS);
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
    public boolean matchesMetadata(WorldObjectEntry entry) {
        return (providers.isEmpty() || providers.contains(entry.provider()))
                && (types.isEmpty() || types.contains(entry.type()))
                && (name.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                && entry.tags().containsAll(tags)
                && (sources.isEmpty() || sources.contains(entry.source()))
                && (lifetimes.isEmpty() || lifetimes.contains(entry.lifetime()));
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
