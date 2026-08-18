package com.mine.geometry_node.client.model.render.backend.host.light.solve;

/** Independent CPU-memory admission for immutable snapshots and solved fields. */
public final class HostLightingMemoryBudget {
    private final long snapshotLimit, fieldLimit, residentLimit;
    private long snapshots, fields;
    private int reservations;
    private long rejected;

    public HostLightingMemoryBudget(long snapshotLimit, long fieldLimit, long residentLimit) {
        if (snapshotLimit < 1 || fieldLimit < 1 || residentLimit < 1) {
            throw new IllegalArgumentException("lighting memory limits must be positive");
        }
        this.snapshotLimit = snapshotLimit;
        this.fieldLimit = fieldLimit;
        this.residentLimit = residentLimit;
    }

    public synchronized Reservation tryReserve(Kind kind, long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("bytes must not be negative");
        long kindLimit = kind == Kind.SNAPSHOT ? snapshotLimit : fieldLimit;
        long kindUsed = kind == Kind.SNAPSHOT ? snapshots : fields;
        if (bytes > kindLimit - kindUsed || bytes > residentLimit - snapshots - fields) {
            rejected++;
            return null;
        }
        if (kind == Kind.SNAPSHOT) snapshots += bytes;
        else fields += bytes;
        reservations++;
        return new Reservation(this, kind, bytes);
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(snapshots, fields, snapshots + fields, reservations, rejected,
                snapshotLimit, fieldLimit, residentLimit);
    }

    private synchronized void release(Kind kind, long bytes) {
        if (kind == Kind.SNAPSHOT) snapshots = Math.subtractExact(snapshots, bytes);
        else fields = Math.subtractExact(fields, bytes);
        reservations--;
    }

    public enum Kind { SNAPSHOT, FIELD }

    public static final class Reservation implements AutoCloseable {
        private HostLightingMemoryBudget owner;
        private final Kind kind;
        private final long bytes;

        private Reservation(HostLightingMemoryBudget owner, Kind kind, long bytes) {
            this.owner = owner;
            this.kind = kind;
            this.bytes = bytes;
        }

        public long bytes() { return bytes; }
        @Override public void close() {
            HostLightingMemoryBudget current;
            synchronized (this) {
                current = owner;
                owner = null;
            }
            if (current != null) current.release(kind, bytes);
        }
    }

    public record Diagnostics(long snapshotBytes, long fieldBytes, long residentBytes,
                              int reservations, long rejected, long snapshotLimit,
                              long fieldLimit, long residentLimit) {}
}
