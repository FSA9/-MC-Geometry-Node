package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.*;

/**
 * Truncated describes incomplete object collection only. Incomplete type/dimension catalogs instead use
 * metadata:partial coverage and partial_metadata (or partial_results when objects are also incomplete).
 * Coverage never claims unloaded native entities were searched. Null counts mean no statistics update
 * (for example an error response); empty counts are an authoritative zero for the queried scope.
 */
public record WorldObjectPage(long requestId, List<WorldObjectEntry> entries, boolean truncated,
                              List<String> dimensionIds, List<WorldObjectType> types,
                              List<String> coverage, String message, Counts counts) {
    public static final int MAX_METADATA = 256;
    public static final int MAX_ENTRY_BYTES = 512 * 1024;
    public static final int MAX_METADATA_BYTES = 256 * 1024;
    /** Scope-wide statistics, independent of row predicates and the result-page limit. */
    public record Counts(Map<String, Long> categories, Map<String, Long> otherProviders, boolean partial) {
        public Counts {
            categories = checkedCounts(categories); otherProviders = checkedCounts(otherProviders);
            try {
                long total = 0;
                for (var values : List.of(categories, otherProviders))
                    for (long value : values.values()) total = Math.addExact(total, value);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Category count total exceeded", overflow);
            }
        }
        private static Map<String, Long> checkedCounts(Map<String, Long> values) {
            if (values.size() > MAX_METADATA) throw new IllegalArgumentException("Too many category counts");
            Map<String, Long> copy = Map.copyOf(values);
            copy.forEach((id, count) -> {
                WorldObjectEntry.validateText(id);
                if (id.isEmpty() || count < 0) throw new IllegalArgumentException("Invalid category count");
            });
            return copy;
        }
        public long total() {
            return java.util.stream.Stream.concat(categories.values().stream(), otherProviders.values().stream())
                    .mapToLong(Long::longValue).sum();
        }
        public static Counts empty() { return new Counts(Map.of(), Map.of(), false); }
        public static Counts fromEntries(List<WorldObjectEntry> entries, boolean partial) {
            Map<String, Long> categories = new LinkedHashMap<>(), otherProviders = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            for (var entry : entries) if (seen.add(entry.key())) {
                var category = WorldObjectCategories.forEntry(entry);
                (category == null ? otherProviders : categories).merge(category == null ? entry.provider() : category.id(), 1L, Long::sum);
            }
            return new Counts(categories, otherProviders, partial);
        }
        long estimatedBytes() {
            long bytes = 16;
            for (var values : List.of(categories, otherProviders))
                for (String id : values.keySet()) bytes += 3L * id.length() + 15;
            return bytes;
        }
    }
    public WorldObjectPage(long requestId, List<WorldObjectEntry> entries, boolean truncated,
                           List<String> dimensionIds, List<WorldObjectType> types,
                           List<String> coverage, String message) {
        this(requestId, entries, truncated, dimensionIds, types, coverage, message, Counts.fromEntries(entries, truncated));
    }
    public WorldObjectPage {
        entries = List.copyOf(entries); dimensionIds = List.copyOf(dimensionIds);
        types = List.copyOf(types); coverage = List.copyOf(coverage);
        if (entries.size() > WorldObjectQuery.MAX_RESULTS || dimensionIds.size() > MAX_METADATA
                || types.size() > MAX_METADATA || coverage.size() > MAX_METADATA)
            throw new IllegalArgumentException("World query page too large");
        if (entries.stream().mapToLong(WorldObjectEntry::estimatedBytes).sum() > MAX_ENTRY_BYTES)
            throw new IllegalArgumentException("World query entry bytes exceeded");
        dimensionIds.forEach(WorldObjectEntry::validateText); coverage.forEach(WorldObjectEntry::validateText);
        WorldObjectEntry.validateText(message);
        long metadataBytes = 3L * message.length();
        for (String value : dimensionIds) metadataBytes += 3L * value.length() + 5;
        for (String value : coverage) metadataBytes += 3L * value.length() + 5;
        for (var type : types) metadataBytes += 3L * (type.provider().length() + type.id().length() + type.labelKey().length()) + 15;
        if (counts != null) metadataBytes += counts.estimatedBytes();
        if (metadataBytes > MAX_METADATA_BYTES) throw new IllegalArgumentException("World query metadata byte limit exceeded");
    }
    public static WorldObjectPage empty(long requestId, String message) {
        return new WorldObjectPage(requestId, List.of(), false, List.of(), List.of(), List.of(), message, null);
    }
}
