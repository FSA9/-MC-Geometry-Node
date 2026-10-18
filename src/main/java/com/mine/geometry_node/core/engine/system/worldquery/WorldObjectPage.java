package com.mine.geometry_node.core.engine.system.worldquery;

import java.util.List;

/**
 * Truncated describes incomplete object collection only. Incomplete type/dimension catalogs instead use
 * metadata:partial coverage and partial_metadata (or partial_results when objects are also incomplete).
 * Coverage never claims unloaded native entities were searched.
 */
public record WorldObjectPage(long requestId, List<WorldObjectEntry> entries, boolean truncated,
                              List<String> dimensionIds, List<WorldObjectType> types,
                              List<String> coverage, String message) {
    public static final int MAX_METADATA = 256;
    public static final int MAX_ENTRY_BYTES = 512 * 1024;
    public static final int MAX_METADATA_BYTES = 256 * 1024;
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
        if (metadataBytes > MAX_METADATA_BYTES) throw new IllegalArgumentException("World query metadata byte limit exceeded");
    }
    public static WorldObjectPage empty(long requestId, String message) {
        return new WorldObjectPage(requestId, List.of(), false, List.of(), List.of(), List.of(), message);
    }
    static String resultMessage(boolean objectsIncomplete, boolean metadataIncomplete) {
        return objectsIncomplete ? "partial_results" : metadataIncomplete ? "partial_metadata" : "";
    }
}
