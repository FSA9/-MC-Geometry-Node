package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Byte and variant limits for optional static HOST geometry caches. */
public final class HostStaticVariantBudget {
    public static final long PER_ARTIFACT_BYTES = 64L << 20;
    public static final long GLOBAL_BYTES = 256L << 20;
    public static final int MAX_VARIANTS_PER_GEOMETRY = 4;
    public static final HostStaticVariantBudget INSTANCE =
            new HostStaticVariantBudget(PER_ARTIFACT_BYTES, GLOBAL_BYTES);

    private final long perArtifactBytes;
    private final long globalBytes;
    private final Map<Object, Long> artifactBytes = new IdentityHashMap<>();
    private long reservedBytes;

    HostStaticVariantBudget(long perArtifactBytes, long globalBytes) {
        if (perArtifactBytes < 1 || globalBytes < perArtifactBytes) {
            throw new IllegalArgumentException("static HOST variant budgets are invalid");
        }
        this.perArtifactBytes = perArtifactBytes;
        this.globalBytes = globalBytes;
    }

    public synchronized Reservation tryReserve(Object artifact, long bytes) {
        Objects.requireNonNull(artifact, "artifact");
        if (bytes < 1) throw new IllegalArgumentException("static HOST variant bytes must be positive");
        long owned = artifactBytes.getOrDefault(artifact, 0L);
        if (bytes > perArtifactBytes - owned || bytes > globalBytes - reservedBytes) return null;
        artifactBytes.put(artifact, owned + bytes);
        reservedBytes += bytes;
        return new Reservation(this, artifact, bytes);
    }

    synchronized long reservedBytes() { return reservedBytes; }
    synchronized long artifactBytes(Object artifact) { return artifactBytes.getOrDefault(artifact, 0L); }

    private synchronized void release(Object artifact, long bytes) {
        long remaining = Math.max(0L, artifactBytes.getOrDefault(artifact, 0L) - bytes);
        if (remaining == 0L) artifactBytes.remove(artifact);
        else artifactBytes.put(artifact, remaining);
        reservedBytes = Math.max(0L, reservedBytes - bytes);
    }

    public static final class Reservation implements AutoCloseable {
        private final HostStaticVariantBudget owner;
        private final Object artifact;
        private final long bytes;
        private boolean closed;

        private Reservation(HostStaticVariantBudget owner, Object artifact, long bytes) {
            this.owner = owner;
            this.artifact = artifact;
            this.bytes = bytes;
        }

        public long bytes() { return bytes; }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            owner.release(artifact, bytes);
        }
    }
}
