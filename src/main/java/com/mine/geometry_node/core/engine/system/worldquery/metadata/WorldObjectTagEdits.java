package com.mine.geometry_node.core.engine.system.worldquery.metadata;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;

import com.mine.geometry_node.core.metadata.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded connection-owned leases of guarded capabilities, never editable DTO copies. Server thread only. */
public final class WorldObjectTagEdits {
    public static final String TARGET_REFERENCE = "tagEditTarget";
    public static final String PREFIX = "database.tag_update_";
    public static final int MAX_LEASES = 1024;
    public static final long LEASE_NANOS = 600_000_000_000L;
    private record Lease(String key, TagAccess tags, long created) {}
    private final LinkedHashMap<String, Lease> leases = new LinkedHashMap<>();
    private final Map<String, String> tokensByKey = new HashMap<>();
    private final LongSupplier clock;
    private long lastEdit = Long.MIN_VALUE;
    public WorldObjectTagEdits() { this(System::nanoTime); }
    WorldObjectTagEdits(LongSupplier clock) { this.clock = clock; }

    public WorldObjectEntry advertise(WorldObjectEntry entry, MetadataCapabilities.Access access) {
        if (access == null || !access.supports(MetadataCapabilities.Capability.TAG)
                || entry.references().size() >= WorldObjectEntry.MAX_REFERENCES) return entry;
        // Verify the owner is still current before advertising support.
        access.requireTags().values();
        long now = clock.getAsLong();
        String previousToken = tokensByKey.get(entry.key());
        Lease previous = leases.get(previousToken);
        if (previous != null) {
            try {
                // Reuse only while the original guarded owner remains live. Replacements must get a fresh token.
                previous.tags().values();
                leases.remove(previousToken);
                leases.put(previousToken, new Lease(previous.key(), previous.tags(), now));
                return withToken(entry, previousToken);
            } catch (IllegalStateException | UnsupportedOperationException stale) {
                leases.remove(previousToken); tokensByKey.remove(entry.key());
            }
        }
        var expired = leases.entrySet().iterator();
        while (expired.hasNext()) {
            var item = expired.next();
            if (now - item.getValue().created() >= LEASE_NANOS) {
                tokensByKey.remove(item.getValue().key(), item.getKey()); expired.remove();
            }
        }
        while (leases.size() >= MAX_LEASES) {
            String oldest = leases.keySet().iterator().next();
            tokensByKey.remove(leases.remove(oldest).key(), oldest);
        }
        String token = UUID.randomUUID().toString();
        leases.put(token, new Lease(entry.key(), access.requireTags(), now));
        tokensByKey.put(entry.key(), token);
        return withToken(entry, token);
    }
    private static WorldObjectEntry withToken(WorldObjectEntry entry, String token) {
        Map<String, String> refs = new LinkedHashMap<>(entry.references()); refs.put(TARGET_REFERENCE, token);
        return new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(), entry.dimension(),
                entry.position(), entry.bounds(), entry.tags(), entry.source(), entry.lifetime(), entry.sceneObjectId(), entry.entityId(), refs, entry.graphs());
    }

    /** Permission is checked before reading or mutating any owner. Expected tags compare as an exact ordered snapshot. */
    public String edit(boolean permitted, String token, String key, List<String> expected, String tag, boolean add) {
        if (!permitted) return PREFIX + "forbidden";
        long now = clock.getAsLong();
        if (lastEdit != Long.MIN_VALUE && now - lastEdit < 100_000_000L) return PREFIX + "failed";
        lastEdit = now;
        Lease lease = leases.get(token);
        if (lease == null || !lease.key().equals(key) || now - lease.created() >= LEASE_NANOS) return PREFIX + "unavailable";
        final String normalized;
        try { normalized = TagPolicy.EXACT.normalize(tag); }
        catch (IllegalArgumentException invalid) { return PREFIX + "invalid"; }
        try {
            List<String> current = lease.tags().values();
            if (!current.equals(expected)) return PREFIX + "conflict";
            // Validate the complete candidate before entering the owner's mutation boundary.
            if (add) TagSet.add(current, List.of(normalized), TagPolicy.EXACT);
            if (add) lease.tags().add(normalized); else lease.tags().remove(normalized);
            leases.remove(token); leases.put(token, new Lease(lease.key(), lease.tags(), now));
            return "";
        } catch (IllegalArgumentException invalid) { return PREFIX + "invalid"; }
        catch (IllegalStateException | UnsupportedOperationException unavailable) { return PREFIX + "unavailable"; }
    }
}
