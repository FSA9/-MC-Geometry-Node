package com.mine.geometry_node.client.model.render.backend.host.light.asset;

/** Independent admission for immutable F2 receiver/caster data. */
public final class HostLightingAssetBudget {
    public static final long PER_ARTIFACT_BYTES = 256L << 20;
    public static final long GLOBAL_BYTES = 512L << 20;
    public static final HostLightingAssetBudget INSTANCE =
            new HostLightingAssetBudget(PER_ARTIFACT_BYTES, GLOBAL_BYTES);

    private final long perArtifactBytes;
    private final long globalBytes;
    private long residentBytes;
    private int artifacts;
    private long rejected;

    public HostLightingAssetBudget(long perArtifactBytes, long globalBytes) {
        if (perArtifactBytes < 1 || globalBytes < perArtifactBytes) {
            throw new IllegalArgumentException("lighting asset budget limits are invalid");
        }
        this.perArtifactBytes = perArtifactBytes;
        this.globalBytes = globalBytes;
    }

    public synchronized Reservation tryReserve(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("bytes must not be negative");
        if (bytes > perArtifactBytes || bytes > globalBytes - residentBytes) {
            rejected++;
            return null;
        }
        residentBytes += bytes;
        if (bytes > 0) artifacts++;
        return new Reservation(this, bytes, bytes > 0);
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(residentBytes, globalBytes, artifacts, rejected, perArtifactBytes);
    }

    private synchronized void release(long bytes, boolean counted) {
        residentBytes = Math.subtractExact(residentBytes, bytes);
        if (counted) artifacts--;
    }

    public record Diagnostics(long residentBytes, long limitBytes, int artifacts,
                              long rejected, long perArtifactLimitBytes) {}

    public static final class Reservation implements AutoCloseable {
        private HostLightingAssetBudget owner;
        private final long bytes;
        private final boolean counted;

        private Reservation(HostLightingAssetBudget owner, long bytes, boolean counted) {
            this.owner = owner;
            this.bytes = bytes;
            this.counted = counted;
        }

        public long bytes() { return bytes; }
        @Override public void close() {
            HostLightingAssetBudget current;
            synchronized (this) {
                current = owner;
                owner = null;
            }
            if (current != null) current.release(bytes, counted);
        }
    }
}
