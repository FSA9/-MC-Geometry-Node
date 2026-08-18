package com.mine.geometry_node.client.model.render.backend.host.entity;

/** Bounds additional CPU memory retained by preparing and live HOST artifacts. */
public final class HostPreparationMemoryBudget {
    public static final long PER_ARTIFACT_BYTES = 640L << 20;
    public static final long GLOBAL_BYTES = 1L << 30;
    public static final HostPreparationMemoryBudget INSTANCE =
            new HostPreparationMemoryBudget(PER_ARTIFACT_BYTES, GLOBAL_BYTES);

    private final long perArtifactBytes;
    private final long globalBytes;
    private long reservedBytes;
    private int artifacts;

    HostPreparationMemoryBudget(long perArtifactBytes, long globalBytes) {
        if (perArtifactBytes < 1 || globalBytes < perArtifactBytes) {
            throw new IllegalArgumentException("HOST preparation memory limits are invalid");
        }
        this.perArtifactBytes = perArtifactBytes;
        this.globalBytes = globalBytes;
    }

    synchronized Reservation reserve(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("HOST preparation byte estimate must not be negative");
        if (bytes > perArtifactBytes) {
            throw new HostPreparationBudgetExceeded("HOST artifact requires " + bytes
                    + " additional CPU bytes; per-artifact limit is " + perArtifactBytes);
        }
        if (bytes > globalBytes - reservedBytes) {
            throw new HostPreparationBudgetExceeded("HOST preparations require " + bytes
                    + " additional CPU bytes; global available budget is " + (globalBytes - reservedBytes));
        }
        reservedBytes += bytes;
        artifacts++;
        return new Reservation(this, bytes);
    }

    synchronized long reservedBytes() { return reservedBytes; }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(reservedBytes, globalBytes, artifacts);
    }

    private synchronized void release(long bytes) {
        reservedBytes = Math.max(0, reservedBytes - bytes);
        artifacts = Math.max(0, artifacts - 1);
    }

    public record Diagnostics(long reservedBytes, long limitBytes, int artifacts) {}

    static final class Reservation implements AutoCloseable {
        private final HostPreparationMemoryBudget owner;
        private final long bytes;
        private boolean closed;

        private Reservation(HostPreparationMemoryBudget owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        long bytes() { return bytes; }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            owner.release(bytes);
        }
    }

    static final class HostPreparationBudgetExceeded extends RuntimeException {
        private HostPreparationBudgetExceeded(String message) { super(message); }
    }
}
