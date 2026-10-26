package com.mine.geometry_node.core.engine.system.entity.index;

import java.util.*;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

/** Authoritative persistent records; no cache eviction, TTL, world lookup or entity references. */
final class EntityIndexCatalog {
    static final int MAX_RECORDS = 65_536;
    static final long MAX_DOCUMENT_BYTES = 64L * 1024 * 1024;
    // Size belongs to the immutable snapshot, so removal/replacement never copies its NBT again.
    private record StoredRecord(EntityIndexRecord record, long bytes, long appearanceBytes) {}
    private final Map<UUID, StoredRecord> records = new LinkedHashMap<>();
    private long sequence;
    private long estimatedBytes;

    EntityIndexCatalog() {}
    EntityIndexCatalog(long sequence, Collection<EntityIndexRecord> values) {
        if (sequence < 0 || values.size() > MAX_RECORDS) throw new IllegalArgumentException("Invalid entity catalogue");
        for (EntityIndexRecord record : values) {
            if (record.revision() > sequence || records.containsKey(record.uuid()))
                throw new IllegalArgumentException("Invalid or duplicate entity registration");
            StoredRecord stored = size(record, null);
            estimatedBytes = Math.addExact(estimatedBytes, stored.bytes());
            if (estimatedBytes > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Entity directory byte budget exceeded");
            records.put(record.uuid(), stored);
        }
        this.sequence = sequence;
    }
    long sequence() { return sequence; }
    EntityIndexRecord get(UUID id) { var stored = records.get(id); return stored == null ? null : stored.record(); }
    long revision(UUID id) { var record = get(id); return record == null ? 0 : record.revision(); }
    List<EntityIndexRecord> records() { return records.values().stream().map(StoredRecord::record).toList(); }
    void visit(Predicate<EntityIndexRecord> visitor) {
        for (StoredRecord stored : records.values()) if (!visitor.test(stored.record())) return;
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
        StoredRecord stored = size(candidate, null);
        long bytes = Math.addExact(estimatedBytes, stored.bytes());
        if (bytes > MAX_DOCUMENT_BYTES) throw new IllegalStateException("entity_index_full");
        records.put(id, stored);
        estimatedBytes = bytes;
        sequence = next;
        return true;
    }
    boolean unregister(UUID id, long expected) {
        expect(id, expected);
        if (!records.containsKey(id)) return false;
        long next = Math.incrementExact(sequence);
        estimatedBytes -= records.remove(id).bytes(); sequence = next;
        return true;
    }
    boolean refresh(EntityIndexRecord candidate) {
        return refresh(candidate, false);
    }
    boolean refresh(EntityIndexRecord candidate, boolean observation) {
        StoredRecord stored = records.get(candidate.uuid());
        if (stored == null || stored.record().revision() != candidate.revision()) return false;
        EntityIndexRecord previous = stored.record();
        // Observation time alone does not dirty an otherwise identical snapshot every tick.
        boolean sameContent = previous.alive() == candidate.alive() && previous.entry().equals(candidate.entry())
                && previous.metadata().equals(candidate.metadata())
                && previous.appearanceSpec().equals(candidate.appearanceSpec())
                && previous.factions().equals(candidate.factions())
                && previous.appearance().equals(candidate.appearance());
        if (sameContent && (!observation || previous.observedAtMillis() == candidate.observedAtMillis())) return false;
        StoredRecord replacement = sameContent
                ? new StoredRecord(candidate, stored.bytes(), stored.appearanceBytes()) : size(candidate, stored);
        long bytes = Math.addExact(estimatedBytes - stored.bytes(), replacement.bytes());
        if (bytes > MAX_DOCUMENT_BYTES) throw new IllegalStateException("entity_index_full");
        records.put(candidate.uuid(), replacement); estimatedBytes = bytes;
        return true;
    }
    boolean destroy(UUID id) {
        if (!records.containsKey(id)) return false;
        long next = Math.incrementExact(sequence);
        estimatedBytes -= records.remove(id).bytes(); sequence = next;
        return true;
    }
    boolean departure(UUID id, Entity.RemovalReason reason, boolean successorOwnsIdentity) {
        return reason != null && reason.shouldDestroy() && !successorOwnsIdentity && destroy(id);
    }
    private static StoredRecord size(EntityIndexRecord record, StoredRecord previous) {
        long appearanceBytes = previous != null && record.appearance().equals(previous.record().appearance())
                ? previous.appearanceBytes() : record.appearance().data().sizeInBytes();
        long bytes = 256L + record.entry().estimatedBytes() + record.metadata().encodedSize()
                + appearanceBytes + record.appearanceSpec().estimatedBytes() + 40L * record.factions().size();
        return new StoredRecord(record, bytes, appearanceBytes);
    }
}
