package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic byte/object admission for artifact-owned HOST texture binding sets. */
public final class HostTextureBindingBudget {
    public static final long PER_ARTIFACT_BYTES = 256L << 20;
    public static final long GLOBAL_BYTES = 512L << 20;
    public static final int PER_ARTIFACT_OBJECTS = 1024;
    public static final int GLOBAL_OBJECTS = 4096;
    public static final HostTextureBindingBudget INSTANCE = new HostTextureBindingBudget(
            PER_ARTIFACT_BYTES, GLOBAL_BYTES, PER_ARTIFACT_OBJECTS, GLOBAL_OBJECTS);

    private final long perArtifactBytes;
    private final long globalBytes;
    private final int perArtifactObjects;
    private final int globalObjects;
    private final Map<Object, Usage> artifactUsage = new IdentityHashMap<>();
    private long reservedBytes;
    private int reservedObjects;
    private long residentBytes;
    private int residentObjects;
    private int residentBindings;

    HostTextureBindingBudget(long perArtifactBytes, long globalBytes,
                             int perArtifactObjects, int globalObjects) {
        if (perArtifactBytes < 1 || globalBytes < perArtifactBytes
                || perArtifactObjects < 1 || globalObjects < perArtifactObjects) {
            throw new IllegalArgumentException("HOST texture binding budgets are invalid");
        }
        this.perArtifactBytes = perArtifactBytes;
        this.globalBytes = globalBytes;
        this.perArtifactObjects = perArtifactObjects;
        this.globalObjects = globalObjects;
    }

    /** Reserves one complete missing binding set or leaves all counters unchanged. */
    public synchronized BatchReservation tryReserveBatch(Object artifact, List<Footprint> footprints) {
        Objects.requireNonNull(artifact, "artifact");
        BatchFootprint batch = validateBatch(footprints);
        Usage owned = artifactUsage.getOrDefault(artifact, Usage.ZERO);
        if (batch.bytes > perArtifactBytes - owned.bytes
                || batch.bytes > globalBytes - reservedBytes
                || batch.objects > perArtifactObjects - owned.objects
                || batch.objects > globalObjects - reservedObjects) return null;
        artifactUsage.put(artifact, new Usage(owned.bytes + batch.bytes, owned.objects + batch.objects));
        reservedBytes += batch.bytes;
        reservedObjects += batch.objects;
        return new BatchReservation(this, artifact, batch.counts, batch.bytes, batch.objects);
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(residentBytes, reservedBytes, globalBytes,
                residentObjects, reservedObjects, globalObjects, residentBindings, artifactUsage.size());
    }

    public synchronized ArtifactDiagnostics artifactDiagnostics(Object artifact) {
        Objects.requireNonNull(artifact, "artifact");
        Usage usage = artifactUsage.getOrDefault(artifact, Usage.ZERO);
        return new ArtifactDiagnostics(usage.bytes, usage.objects);
    }

    private synchronized void markResident(long bytes, int objects) {
        residentBytes = Math.addExact(residentBytes, bytes);
        residentObjects = Math.addExact(residentObjects, objects);
        residentBindings = Math.addExact(residentBindings, 1);
    }

    private synchronized void release(Object artifact, long bytes, int objects, boolean resident) {
        Usage owned = artifactUsage.get(artifact);
        if (owned == null || owned.bytes < bytes || owned.objects < objects
                || reservedBytes < bytes || reservedObjects < objects) {
            throw new IllegalStateException("HOST texture binding accounting underflow");
        }
        Usage remaining = new Usage(owned.bytes - bytes, owned.objects - objects);
        if (remaining.bytes == 0 && remaining.objects == 0) artifactUsage.remove(artifact);
        else artifactUsage.put(artifact, remaining);
        reservedBytes -= bytes;
        reservedObjects -= objects;
        if (!resident) return;
        if (residentBytes < bytes || residentObjects < objects || residentBindings < 1) {
            throw new IllegalStateException("HOST texture binding resident accounting underflow");
        }
        residentBytes -= bytes;
        residentObjects -= objects;
        residentBindings--;
    }

    private static BatchFootprint validateBatch(List<Footprint> footprints) {
        Objects.requireNonNull(footprints, "footprints");
        if (footprints.isEmpty()) throw new IllegalArgumentException("HOST texture binding set must not be empty");
        Map<Footprint, Integer> counts = new HashMap<>();
        long bytes = 0;
        int objects = 0;
        for (Footprint footprint : footprints) {
            Objects.requireNonNull(footprint, "footprint");
            bytes = Math.addExact(bytes, footprint.bytes);
            objects = Math.addExact(objects, footprint.objects);
            counts.merge(footprint, 1, Math::addExact);
        }
        return new BatchFootprint(Map.copyOf(counts), bytes, objects);
    }

    public record Footprint(long bytes, int objects) {
        public Footprint {
            if (bytes < 1 || objects < 1) {
                throw new IllegalArgumentException("HOST texture binding footprint must be positive");
            }
        }
    }

    public record Diagnostics(long residentBytes, long reservedBytes, long limitBytes,
                              int residentObjects, int reservedObjects, int objectLimit,
                              int residentBindings, int artifacts) {
        public Diagnostics {
            if (residentBytes < 0 || residentBytes > reservedBytes || reservedBytes > limitBytes
                    || residentObjects < 0 || residentObjects > reservedObjects
                    || reservedObjects > objectLimit || residentBindings < 0 || artifacts < 0) {
                throw new IllegalArgumentException("invalid HOST texture binding diagnostics");
            }
        }
    }

    public record ArtifactDiagnostics(long reservedBytes, int reservedObjects) {
        public ArtifactDiagnostics {
            if (reservedBytes < 0 || reservedObjects < 0) {
                throw new IllegalArgumentException("invalid artifact HOST texture diagnostics");
            }
        }
    }

    public static final class BatchReservation implements AutoCloseable {
        private final HostTextureBindingBudget owner;
        private final Object artifact;
        private final Map<Footprint, Integer> unclaimed;
        private long unclaimedBytes;
        private int unclaimedObjects;
        private boolean closed;

        private BatchReservation(HostTextureBindingBudget owner, Object artifact,
                                 Map<Footprint, Integer> footprints, long bytes, int objects) {
            this.owner = owner;
            this.artifact = artifact;
            this.unclaimed = new HashMap<>(footprints);
            this.unclaimedBytes = bytes;
            this.unclaimedObjects = objects;
        }

        public synchronized Reservation claim(Footprint footprint) {
            Objects.requireNonNull(footprint, "footprint");
            if (closed) return null;
            int count = unclaimed.getOrDefault(footprint, 0);
            if (count == 0) return null;
            if (count == 1) unclaimed.remove(footprint);
            else unclaimed.put(footprint, count - 1);
            unclaimedBytes -= footprint.bytes;
            unclaimedObjects -= footprint.objects;
            return new Reservation(owner, artifact, footprint.bytes, footprint.objects);
        }

        public synchronized boolean fullyClaimed() {
            return !closed && unclaimed.isEmpty();
        }

        @Override public void close() {
            long bytes;
            int objects;
            synchronized (this) {
                if (closed) return;
                closed = true;
                bytes = unclaimedBytes;
                objects = unclaimedObjects;
                unclaimedBytes = 0;
                unclaimedObjects = 0;
                unclaimed.clear();
            }
            if (bytes != 0 || objects != 0) owner.release(artifact, bytes, objects, false);
        }
    }

    public static final class Reservation implements AutoCloseable {
        private final HostTextureBindingBudget owner;
        private final Object artifact;
        private final long bytes;
        private final int objects;
        private boolean resident;
        private boolean closed;

        private Reservation(HostTextureBindingBudget owner, Object artifact, long bytes, int objects) {
            this.owner = owner;
            this.artifact = artifact;
            this.bytes = bytes;
            this.objects = objects;
        }

        public long bytes() { return bytes; }
        public int objects() { return objects; }

        public void markResident() {
            synchronized (this) {
                if (closed) throw new IllegalStateException("closed HOST texture reservation cannot become resident");
                if (resident) return;
                resident = true;
            }
            owner.markResident(bytes, objects);
        }

        @Override public void close() {
            boolean wasResident;
            synchronized (this) {
                if (closed) return;
                closed = true;
                wasResident = resident;
            }
            owner.release(artifact, bytes, objects, wasResident);
        }
    }

    private record Usage(long bytes, int objects) {
        private static final Usage ZERO = new Usage(0, 0);
    }

    private record BatchFootprint(Map<Footprint, Integer> counts, long bytes, int objects) {}
}
