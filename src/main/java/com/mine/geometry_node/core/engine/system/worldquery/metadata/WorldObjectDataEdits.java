package com.mine.geometry_node.core.engine.system.worldquery.metadata;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;

import com.mine.geometry_node.core.metadata.*;
import net.minecraft.core.HolderLookup;
import java.util.*;
import java.util.function.LongSupplier;

/** Server-thread, connection-owned DATA leases. Guarded owners enforce identity and lifetime on every access. */
public final class WorldObjectDataEdits {
    public static final String TARGET_REFERENCE = "dataEditTarget";
    public static final String COUNT_REFERENCE = "data_count", SUMMARY_REFERENCE = "data_summary";
    public static final String READ_ONLY_REFERENCE = "dataReadOnly";
    public static final String PREFIX = "database.data_update_";
    private record Lease(String key, DataAccess data, long touched, boolean editable) {}
    public record Result(Map<String, Object> values, String revision, String message) {
        public static Result error(String reason) { return new Result(Map.of(), "", PREFIX + reason); }
    }
    private final LinkedHashMap<String, Lease> leases = new LinkedHashMap<>();
    private final Map<String, String> tokensByKey = new HashMap<>();
    private final LongSupplier clock;
    private final HolderLookup.Provider registries;
    private long lastEdit = Long.MIN_VALUE;
    public WorldObjectDataEdits(HolderLookup.Provider registries) { this(registries, System::nanoTime); }
    WorldObjectDataEdits(HolderLookup.Provider registries, LongSupplier clock) { this.registries = registries; this.clock = clock; }
    public WorldObjectEntry advertise(WorldObjectEntry entry, MetadataCapabilities.Access access) {
        return advertise(entry, access, true);
    }
    public WorldObjectEntry advertise(WorldObjectEntry entry, MetadataCapabilities.Access access, boolean editable) {
        if (access == null || !access.supports(MetadataCapabilities.Capability.DATA)
                || entry.references().size() > WorldObjectEntry.MAX_REFERENCES - (editable ? 3 : 4)) return entry;
        DataAccess data = access.requireData();
        Map<String, Object> values = null;
        long now = clock.getAsLong(); String token = tokensByKey.get(entry.key());
        Lease previous = leases.get(token);
        if (previous != null && previous.editable() != editable) {
            leases.remove(token); tokensByKey.remove(entry.key()); previous = null; token = null;
        }
        if (previous != null) {
            try { values = previous.data().values(); data = previous.data(); }
            catch (IllegalStateException | UnsupportedOperationException stale) {
                leases.remove(token); tokensByKey.remove(entry.key()); token = null;
            }
        }
        if (previous == null) token = null;
        if (values == null) values = data.values();
        var iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (now - item.getValue().touched() >= WorldObjectTagEdits.LEASE_NANOS) {
                tokensByKey.remove(item.getValue().key(), item.getKey()); iterator.remove();
                if (item.getKey().equals(token)) token = null;
            }
        }
        if (token == null) token = UUID.randomUUID().toString();
        leases.remove(token);
        while (leases.size() >= WorldObjectTagEdits.MAX_LEASES) {
            String oldest = leases.keySet().iterator().next(); tokensByKey.remove(leases.remove(oldest).key(), oldest);
        }
        leases.put(token, new Lease(entry.key(), data, now, editable)); tokensByKey.put(entry.key(), token);
        Map<String, String> refs = new LinkedHashMap<>(entry.references());
        refs.put(TARGET_REFERENCE, token); refs.put(COUNT_REFERENCE, Integer.toString(values.size()));
        refs.put(SUMMARY_REFERENCE, GraphMetadataDataValues.summary(values));
        if (!editable) refs.put(READ_ONLY_REFERENCE, "true"); else refs.remove(READ_ONLY_REFERENCE);
        return new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(), entry.dimension(),
                entry.position(), entry.bounds(), entry.tags(), entry.source(), entry.lifetime(), entry.sceneObjectId(), entry.entityId(), refs, entry.graphs(), entry.entityState());
    }
    public Result read(boolean permitted, String token, String key) {
        if (!permitted) return Result.error("forbidden");
        Lease lease = current(token, key);
        if (lease == null) return Result.error("unavailable");
        try {
            Map<String, Object> values = GraphMetadataDataValues.snapshot(lease.data().values());
            GraphMetadataDataValues.encode(values, registries, GraphMetadataDataValues.MAX_READ_BYTES);
            renew(token, lease);
            return new Result(values, GraphMetadataDataValues.revision(values, registries), "");
        } catch (IllegalArgumentException invalid) { return Result.error("too_large"); }
        catch (IllegalStateException | UnsupportedOperationException stale) { return Result.error("unavailable"); }
    }
    /** Compare and replace inside DataAccess.update so no mutation can occur between comparison and owner commit. */
    public Result replace(boolean permitted, String token, String key, String expectedRevision, Map<String, Object> replacement) {
        if (!permitted) return Result.error("forbidden");
        Lease lease = current(token, key);
        if (lease == null) return Result.error("unavailable");
        if (!lease.editable()) return Result.error("forbidden");
        long now = clock.getAsLong();
        if (lastEdit != Long.MIN_VALUE && now - lastEdit < 100_000_000L) return Result.error("failed");
        lastEdit = now;
        try {
            Map<String, Object> next = GraphMetadataDataValues.snapshot(replacement);
            GraphMetadataDataValues.encode(next, registries, GraphMetadataDataValues.MAX_WRITE_BYTES);
            lease.data().update(current -> {
                if (!GraphMetadataDataValues.revision(current, registries).equals(expectedRevision)) throw new Conflict();
                return next;
            });
            // Some historical scoped stores signal capacity rejection with false instead of throwing.
            // A no-op is also false, so verify the authoritative value before reporting success.
            Map<String, Object> committed = GraphMetadataDataValues.snapshot(lease.data().values());
            if (!GraphMetadataDataValues.equivalent(committed, next)) return Result.error("invalid");
            renew(token, lease);
            return new Result(committed, GraphMetadataDataValues.revision(committed, registries), "");
        } catch (Conflict conflict) { return Result.error("conflict"); }
        catch (IllegalArgumentException invalid) { return Result.error("invalid"); }
        catch (IllegalStateException | UnsupportedOperationException stale) { return Result.error("unavailable"); }
    }
    private Lease current(String token, String key) {
        Lease lease = leases.get(token);
        return lease != null && lease.key().equals(key) && clock.getAsLong() - lease.touched() < WorldObjectTagEdits.LEASE_NANOS ? lease : null;
    }
    private void renew(String token, Lease lease) {
        leases.remove(token); leases.put(token, new Lease(lease.key(), lease.data(), clock.getAsLong(), lease.editable()));
    }
    private static final class Conflict extends RuntimeException {}
}
