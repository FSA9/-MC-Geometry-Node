package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectCategories;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.provider.WorldObjectProvider;

import java.util.*;
import java.util.function.Predicate;

/** Actual server result sink; injectable scope checks keep result-budget tests independent of a live level. */
class WorldObjectResultCollector implements WorldObjectProvider.Sink {
    private final WorldObjectQuery query;
    private final Predicate<WorldObjectEntry> inScope;
    final List<WorldObjectEntry> entries = new ArrayList<>();
    private final Set<String> keys = new HashSet<>();
    private final Set<String> countedKeys = new HashSet<>();
    private final Map<String, Long> categoryCounts = new LinkedHashMap<>(), otherProviderCounts = new LinkedHashMap<>();
    int scanned, bytes;
    boolean truncated, stopped, resultsFull, countsPartial;
    String provider;
    WorldObjectProvider ownerProvider;
    private long deadline;

    WorldObjectResultCollector(WorldObjectQuery query, Predicate<WorldObjectEntry> inScope) {
        this.query = Objects.requireNonNull(query);
        this.inScope = Objects.requireNonNull(inScope);
    }
    @Override public boolean scan() {
        if (stopped) return false;
        if (deadline == 0) deadline = System.nanoTime() + 10_000_000L;
        if (++scanned > ServerWorldObjectQueries.MAX_SCANNED || System.nanoTime() > deadline) {
            countsPartial = truncated = stopped = true; return false;
        }
        return true;
    }
    @Override public boolean emit(WorldObjectEntry entry) {
        if (stopped) return false;
        if (!entry.provider().equals(provider)) throw new IllegalArgumentException("Provider identity mismatch");
        if (!inScope.test(entry)) return true;
        if (countedKeys.add(entry.key())) {
            var category = WorldObjectCategories.forEntry(entry);
            var counts = category == null ? otherProviderCounts : categoryCounts;
            String id = category == null ? entry.provider() : category.id();
            if (counts.containsKey(id) || counts.size() < WorldObjectPage.MAX_METADATA)
                counts.merge(id, 1L, Long::sum);
            else countsPartial = true;
        }
        if (resultsFull || !query.matchesMetadata(entry)) return true;
        if (!keys.add(entry.key())) return true;
        int remaining = WorldObjectPage.MAX_ENTRY_BYTES - bytes;
        if (entries.size() >= query.limit() || entry.estimatedBytes() > remaining) {
            // Keep scanning for independent navigation totals; never retain further result rows.
            truncated = resultsFull = true; return true;
        }
        try {
            WorldObjectEntry advertised = advertise(entry, remaining);
            if (advertised.estimatedBytes() <= remaining) entry = advertised;
        } catch (RuntimeException unavailable) {
            // Metadata capability failure must not hide the object or fail a read-only query.
        }
        entries.add(entry); bytes += entry.estimatedBytes();
        return true;
    }
    protected WorldObjectEntry advertise(WorldObjectEntry entry, int remainingBytes) { return entry; }
    WorldObjectPage.Counts counts() { return new WorldObjectPage.Counts(categoryCounts, otherProviderCounts, countsPartial); }
}
