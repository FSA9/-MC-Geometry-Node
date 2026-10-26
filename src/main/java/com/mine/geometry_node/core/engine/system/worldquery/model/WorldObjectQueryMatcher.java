package com.mine.geometry_node.core.engine.system.worldquery.model;


import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** Prepared metadata predicate shared by one-off matching and request-local result collection. */
final class WorldObjectQueryMatcher implements Predicate<WorldObjectEntry> {
    private final WorldObjectQuery query;
    private final String name, columnName, registryType, graphName;

    WorldObjectQueryMatcher(WorldObjectQuery query) {
        this.query = Objects.requireNonNull(query);
        name = query.name().toLowerCase(Locale.ROOT);
        columnName = query.columns().name().toLowerCase(Locale.ROOT);
        registryType = query.registryType().toLowerCase(Locale.ROOT);
        graphName = query.graphFilter().name().toLowerCase(Locale.ROOT);
    }

    @Override public boolean test(WorldObjectEntry entry) {
        var columns = query.columns();
        if (!query.entityFilter().matches(entry)
                || !query.providers().isEmpty() && !query.providers().contains(entry.provider())
                || !query.types().isEmpty() && !query.types().contains(entry.type())
                || !query.sources().isEmpty() && !query.sources().contains(entry.source())
                || !query.lifetimes().isEmpty() && !query.lifetimes().contains(entry.lifetime())
                || !columns.dimensions().matches(entry.dimension())
                || !columns.lifetimes().matches(entry.lifetime())
                || !columns.value().matches(WorldObjectQuery.numericValue(entry))) return false;

        // Avoid raw identity/category strings and boxed coordinates when their filters are inactive.
        if (columns.types().active() && !columns.types().matches(WorldObjectQuery.filterType(entry))) return false;
        if (columns.x().active() && !columns.x().matches(entry.position().x)
                || columns.y().active() && !columns.y().matches(entry.position().y)
                || columns.z().active() && !columns.z().matches(entry.position().z)) return false;
        if (!name.isEmpty() || !columnName.isEmpty()) {
            if (entry.name().isEmpty()) {
                String rawType = WorldObjectQuery.rawType(entry);
                if (!name.isEmpty() && !query.searchFallbackTypes().contains(rawType)
                        || !columnName.isEmpty() && !query.columnNameFallbackTypes().contains(rawType)) return false;
            } else {
                String folded = entry.name().toLowerCase(Locale.ROOT);
                if (!folded.contains(name) || !folded.contains(columnName)) return false;
            }
        }
        if (!registryType.isEmpty() && !entry.registryType().toLowerCase(Locale.ROOT).contains(registryType)) return false;
        if (query.graphFilter().active()) {
            boolean matched = false;
            for (var graph : entry.graphs()) {
                if ((query.graphFilter().type().isEmpty() || query.graphFilter().type().equals(graph.type()))
                        && (graphName.isEmpty() || (graph.name().isEmpty() ? graph.id() : graph.name())
                            .toLowerCase(Locale.ROOT).contains(graphName))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return entry.tags().containsAll(query.tags()) && entry.tags().containsAll(columns.includedTags())
                && java.util.Collections.disjoint(entry.tags(), columns.excludedTags());
    }
}
