package com.mine.geometry_node.core.engine.system.entity.index;

import java.util.*;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

/** Authoritative persistent records; no cache eviction, TTL, world lookup or entity references. */
final class EntityIndexCatalog {
    static final int MAX_RECORDS = 65_536;
    static final long MAX_DOCUMENT_BYTES = 64L * 1024 * 1024;
    private final Map<UUID, EntityIndexRecord> records = new LinkedHashMap<>();
    private long sequence;
    private long estimatedBytes;

    EntityIndexCatalog() {}
    EntityIndexCatalog(long sequence, Collection<EntityIndexRecord> values) {
        if (sequence < 0 || values.size() > MAX_RECORDS) throw new IllegalArgumentException("Invalid entity catalogue");
        for (EntityIndexRecord record : values) {
            if (record.revision() > sequence || records.putIfAbsent(record.uuid(), record) != null)
                throw new IllegalArgumentException("Invalid or duplicate entity registration");
            estimatedBytes = Math.addExact(estimatedBytes, size(record));
            if (estimatedBytes > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Entity directory byte budget exceeded");
        }
        this.sequence = sequence;
    }
    long sequence() { return sequence; }
    EntityIndexRecord get(UUID id) { return records.get(id); }
    long revision(UUID id) { var record = get(id); return record == null ? 0 : record.revision(); }
    List<EntityIndexRecord> records() { return List.copyOf(records.values()); }
    void visit(Predicate<EntityIndexRecord> visitor) {
        for (EntityIndexRecord record : records.values()) if (!visitor.test(record)) return;
    }
    void expect(UUID id, long expected) {
        if (expected < 0 || revision(id) != expected) throw new IllegalStateException("conflict");
    }
    boolean register(UUID id, long expected, LongFunction<EntityIndexRecord> capture) {
        expect(id, expected);
        if (records.containsKey(id)) return false;
        if (records.size() >= MAX_RECORDS) throw new IllegalStateException("entity_index_full");
        long next = Math.incrementExact(sequence);
        EntityIndexRecord candidate = Objects.requireNonNull(capture.apply(next));
        if (!candidate.uuid().equals(id) || candidate.revision() != next)
            throw new IllegalArgumentException("Registration identity mismatch");
        long bytes = Math.addExact(estimatedBytes, size(candidate));
        if (bytes > MAX_DOCUMENT_BYTES) throw new IllegalStateException("entity_index_full");
        records.put(id, candidate);
        estimatedBytes = bytes;
        sequence = next;
        return true;
    }
    boolean unregister(UUID id, long expected) {
        expect(id, expected);
        if (!records.containsKey(id)) return false;
        long next = Math.incrementExact(sequence);
        estimatedBytes -= size(records.remove(id)); sequence = next;
        return true;
    }
    boolean refresh(EntityIndexRecord candidate) {
        return refresh(candidate, false);
    }
    boolean refresh(EntityIndexRecord candidate, boolean observation) {
        EntityIndexRecord previous = records.get(candidate.uuid());
        if (previous == null || previous.revision() != candidate.revision()) return false;
        // Observation time alone does not dirty an otherwise identical snapshot every tick.
        if ((!observation || previous.observedAtMillis() == candidate.observedAtMillis())
                && previous.alive() == candidate.alive() && previous.entry().equals(candidate.entry())
                && previous.appearance().equals(candidate.appearance()) && previous.metadata().equals(candidate.metadata())
                && previous.appearanceSpec().equals(candidate.appearanceSpec())
                && previous.factions().equals(candidate.factions())) return false;
        long bytes = Math.addExact(estimatedBytes - size(previous), size(candidate));
        if (bytes > MAX_DOCUMENT_BYTES) throw new IllegalStateException("entity_index_full");
        records.put(candidate.uuid(), candidate); estimatedBytes = bytes;
        return true;
    }
    boolean destroy(UUID id) {
        if (!records.containsKey(id)) return false;
        long next = Math.incrementExact(sequence);
        estimatedBytes -= size(records.remove(id)); sequence = next;
        return true;
    }
    boolean departure(UUID id, Entity.RemovalReason reason, boolean successorOwnsIdentity) {
        return reason != null && reason.shouldDestroy() && !successorOwnsIdentity && destroy(id);
    }
    private static long size(EntityIndexRecord record) {
        return 256L + record.entry().estimatedBytes() + record.metadata().encodedSize()
                + record.appearance().data().sizeInBytes() + record.appearanceSpec().estimatedBytes() + 40L * record.factions().size();
    }
}
