package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Byte and variant limits for optional static HOST geometry caches. */
public final class HostStaticVariantBudget {
    public static final long PER_ARTIFACT_BYTES = 256L << 20;
    public static final long GLOBAL_BYTES = 512L << 20;
    public static final long PER_ARTIFACT_REPLACEMENT_HEADROOM_BYTES = 256L << 20;
    public static final long GLOBAL_REPLACEMENT_HEADROOM_BYTES = 256L << 20;
    public static final int MAX_VARIANTS_PER_INSTANCE_GEOMETRY = 4;
    public static final HostStaticVariantBudget INSTANCE = new HostStaticVariantBudget(
            PER_ARTIFACT_BYTES, GLOBAL_BYTES,
            PER_ARTIFACT_REPLACEMENT_HEADROOM_BYTES, GLOBAL_REPLACEMENT_HEADROOM_BYTES);

    private final long perArtifactBytes;
    private final long globalBytes;
    private final long perArtifactReplacementHeadroomBytes;
    private final long globalReplacementHeadroomBytes;
    private final Map<Object, Long> artifactBytes = new IdentityHashMap<>();
    private final Map<Object, Long> artifactSteadyBytes = new IdentityHashMap<>();
    private final Map<Object, Long> artifactResidentBytes = new IdentityHashMap<>();
    private final Map<Object, Integer> artifactResidentVariants = new IdentityHashMap<>();
    private long reservedBytes;
    private long steadyReservedBytes;
    private long residentBytes;
    private int residentVariants;
    private ReplacementGroup activeReplacement;

    HostStaticVariantBudget(long perArtifactBytes, long globalBytes) {
        this(perArtifactBytes, globalBytes, 0, 0);
    }

    HostStaticVariantBudget(long perArtifactBytes, long globalBytes,
                            long perArtifactReplacementHeadroomBytes,
                            long globalReplacementHeadroomBytes) {
        if (perArtifactBytes < 1 || globalBytes < perArtifactBytes
                || perArtifactReplacementHeadroomBytes < 0
                || globalReplacementHeadroomBytes < perArtifactReplacementHeadroomBytes) {
            throw new IllegalArgumentException("static HOST variant budgets are invalid");
        }
        this.perArtifactBytes = perArtifactBytes;
        this.globalBytes = globalBytes;
        this.perArtifactReplacementHeadroomBytes = perArtifactReplacementHeadroomBytes;
        this.globalReplacementHeadroomBytes = globalReplacementHeadroomBytes;
    }

    public synchronized Reservation tryReserve(Object artifact, long bytes) {
        validateArtifactAndBytes(artifact, bytes);
        if (activeReplacement != null) return null;
        return reserveSteady(artifact, bytes) ? new Reservation(this, artifact, bytes, false) : null;
    }

    /** Atomically admits a complete new-instance working set against steady capacity only. */
    public synchronized BatchReservation tryReserveBatch(Object artifact, List<Long> sizes) {
        BatchSizes batch = validateBatch(artifact, sizes);
        if (activeReplacement != null) return null;
        if (!reserveSteady(artifact, batch.totalBytes)) return null;
        return new BatchReservation(this, artifact, batch.counts, batch.totalBytes, false);
    }

    /** Atomically admits a replacement; only this path may consume the temporary replacement headroom. */
    public synchronized BatchReservation tryReserveReplacementBatch(
            Object artifact, List<Long> sizes, long replacedSteadyBytes) {
        BatchSizes batch = validateBatch(artifact, sizes);
        if (replacedSteadyBytes < 1 || activeReplacement != null) return null;
        long owned = artifactBytes.getOrDefault(artifact, 0L);
        long ownedSteady = artifactSteadyBytes.getOrDefault(artifact, 0L);
        long artifactLimit = Math.addExact(perArtifactBytes, perArtifactReplacementHeadroomBytes);
        long globalLimit = Math.addExact(globalBytes, globalReplacementHeadroomBytes);
        if (replacedSteadyBytes > ownedSteady || replacedSteadyBytes > steadyReservedBytes
                || batch.totalBytes > perArtifactBytes - (ownedSteady - replacedSteadyBytes)
                || batch.totalBytes > globalBytes - (steadyReservedBytes - replacedSteadyBytes)
                || batch.totalBytes > artifactLimit - owned
                || batch.totalBytes > globalLimit - reservedBytes) return null;
        artifactBytes.put(artifact, owned + batch.totalBytes);
        reservedBytes += batch.totalBytes;
        ReplacementGroup group = new ReplacementGroup(artifact, batch.totalBytes);
        activeReplacement = group;
        return new BatchReservation(this, artifact, batch.counts, batch.totalBytes, true, group);
    }

    private boolean reserveSteady(Object artifact, long bytes) {
        long ownedSteady = artifactSteadyBytes.getOrDefault(artifact, 0L);
        long artifactTotal = artifactBytes.getOrDefault(artifact, 0L);
        long combinedArtifactLimit = Math.addExact(perArtifactBytes, perArtifactReplacementHeadroomBytes);
        long combinedGlobalLimit = Math.addExact(globalBytes, globalReplacementHeadroomBytes);
        if (bytes > perArtifactBytes - ownedSteady || bytes > globalBytes - steadyReservedBytes
                || bytes > combinedArtifactLimit - artifactTotal
                || bytes > combinedGlobalLimit - reservedBytes) return false;
        artifactSteadyBytes.put(artifact, ownedSteady + bytes);
        artifactBytes.put(artifact, artifactTotal + bytes);
        steadyReservedBytes += bytes;
        reservedBytes += bytes;
        return true;
    }

    private static void validateArtifactAndBytes(Object artifact, long bytes) {
        Objects.requireNonNull(artifact, "artifact");
        if (bytes < 1) throw new IllegalArgumentException("static HOST variant bytes must be positive");
    }

    private static BatchSizes validateBatch(Object artifact, List<Long> sizes) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(sizes, "sizes");
        if (sizes.isEmpty()) throw new IllegalArgumentException("static HOST working set must not be empty");
        Map<Long, Integer> counts = new HashMap<>();
        long total = 0;
        for (Long boxed : sizes) {
            if (boxed == null || boxed < 1) {
                throw new IllegalArgumentException("static HOST working-set sizes must be positive");
            }
            total = Math.addExact(total, boxed);
            counts.merge(boxed, 1, Math::addExact);
        }
        return new BatchSizes(Map.copyOf(counts), total);
    }

    synchronized long reservedBytes() { return reservedBytes; }
    synchronized long artifactBytes(Object artifact) { return artifactBytes.getOrDefault(artifact, 0L); }

    public synchronized ArtifactDiagnostics artifactDiagnostics(Object artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return new ArtifactDiagnostics(artifactResidentBytes.getOrDefault(artifact, 0L),
                artifactBytes.getOrDefault(artifact, 0L),
                artifactResidentVariants.getOrDefault(artifact, 0));
    }

    /** Exact live static HOST allocations, including variants awaiting fenced retirement. */
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(residentBytes, reservedBytes,
                Math.addExact(globalBytes, globalReplacementHeadroomBytes), residentVariants);
    }

    private synchronized void markResident(Object artifact, long bytes) {
        residentBytes = Math.addExact(residentBytes, bytes);
        residentVariants++;
        artifactResidentBytes.merge(artifact, bytes, Math::addExact);
        artifactResidentVariants.merge(artifact, 1, Math::addExact);
    }

    private synchronized void release(Object artifact, long bytes, boolean resident, boolean replacement,
                                      ReplacementGroup replacementGroup) {
        long remaining = artifactBytes.getOrDefault(artifact, 0L) - bytes;
        if (remaining < 0) throw new IllegalStateException("static HOST artifact accounting underflow");
        if (remaining == 0) artifactBytes.remove(artifact);
        else artifactBytes.put(artifact, remaining);
        reservedBytes -= bytes;
        if (reservedBytes < 0) throw new IllegalStateException("static HOST reservation accounting underflow");
        boolean steady = !replacement || replacementGroup != null && replacementGroup.promoted;
        if (steady) {
            long steadyRemaining = artifactSteadyBytes.getOrDefault(artifact, 0L) - bytes;
            if (steadyRemaining < 0 || steadyReservedBytes < bytes) {
                throw new IllegalStateException("static HOST steady accounting underflow");
            }
            if (steadyRemaining == 0) artifactSteadyBytes.remove(artifact);
            else artifactSteadyBytes.put(artifact, steadyRemaining);
            steadyReservedBytes -= bytes;
        }
        if (replacementGroup != null) {
            replacementGroup.remainingBytes -= bytes;
            if (replacementGroup.remainingBytes < 0) {
                throw new IllegalStateException("static HOST replacement accounting underflow");
            }
            if (replacementGroup.remainingBytes == 0 && !replacementGroup.promoted) {
                if (activeReplacement != replacementGroup) {
                    throw new IllegalStateException("static HOST replacement ownership mismatch");
                }
                activeReplacement = null;
            }
        }
        if (!resident) return;
        if (residentVariants <= 0 || residentBytes < bytes) {
            throw new IllegalStateException("static HOST resident accounting underflow");
        }
        residentBytes -= bytes;
        residentVariants--;
        long artifactResident = artifactResidentBytes.getOrDefault(artifact, 0L) - bytes;
        int artifactVariants = artifactResidentVariants.getOrDefault(artifact, 0) - 1;
        if (artifactResident < 0 || artifactVariants < 0) {
            throw new IllegalStateException("static HOST artifact resident accounting underflow");
        }
        if (artifactResident == 0) artifactResidentBytes.remove(artifact);
        else artifactResidentBytes.put(artifact, artifactResident);
        if (artifactVariants == 0) artifactResidentVariants.remove(artifact);
        else artifactResidentVariants.put(artifact, artifactVariants);
    }

    private synchronized void promoteReplacement(ReplacementGroup group) {
        if (group != null && group.remainingBytes == 0 && activeReplacement != group) return;
        if (group == null || group.promoted || activeReplacement != group || group.remainingBytes < 1) {
            throw new IllegalStateException("static HOST replacement cannot be promoted");
        }
        long ownedSteady = artifactSteadyBytes.getOrDefault(group.artifact, 0L);
        if (group.remainingBytes > perArtifactBytes - ownedSteady
                || group.remainingBytes > globalBytes - steadyReservedBytes) {
            throw new IllegalStateException("static HOST replacement no longer fits steady capacity");
        }
        artifactSteadyBytes.put(group.artifact, ownedSteady + group.remainingBytes);
        steadyReservedBytes += group.remainingBytes;
        group.promoted = true;
        activeReplacement = null;
    }

    public record Diagnostics(long residentBytes, long reservedBytes, long limitBytes, int variants) {
        public Diagnostics {
            if (residentBytes < 0 || reservedBytes < residentBytes || limitBytes < 1
                    || reservedBytes > limitBytes || variants < 0) {
                throw new IllegalArgumentException("invalid static HOST diagnostics");
            }
        }
    }

    public static final class Reservation implements AutoCloseable {
        private final HostStaticVariantBudget owner;
        private final Object artifact;
        private final long bytes;
        private final boolean replacement;
        private final ReplacementGroup replacementGroup;
        private boolean resident;
        private boolean closed;

        private Reservation(HostStaticVariantBudget owner, Object artifact, long bytes, boolean replacement) {
            this(owner, artifact, bytes, replacement, null);
        }

        private Reservation(HostStaticVariantBudget owner, Object artifact, long bytes, boolean replacement,
                            ReplacementGroup replacementGroup) {
            this.owner = owner;
            this.artifact = artifact;
            this.bytes = bytes;
            this.replacement = replacement;
            this.replacementGroup = replacementGroup;
        }

        public long bytes() { return bytes; }

        void markResident() {
            synchronized (this) {
                if (closed) throw new IllegalStateException("closed static HOST reservation cannot become resident");
                if (resident) return;
                resident = true;
            }
            owner.markResident(artifact, bytes);
        }

        @Override public void close() {
            boolean wasResident;
            synchronized (this) {
                if (closed) return;
                closed = true;
                wasResident = resident;
            }
            owner.release(artifact, bytes, wasResident, replacement, replacementGroup);
        }
    }

    /** One atomic admission split into exact per-buffer reservations without double-accounting claims. */
    public static final class BatchReservation implements AutoCloseable {
        private final HostStaticVariantBudget owner;
        private final Object artifact;
        private final Map<Long, Integer> unclaimed;
        private final boolean replacement;
        private final ReplacementGroup replacementGroup;
        private long unclaimedBytes;
        private boolean closed;

        private BatchReservation(HostStaticVariantBudget owner, Object artifact, Map<Long, Integer> sizes,
                                 long bytes, boolean replacement) {
            this(owner, artifact, sizes, bytes, replacement, null);
        }

        private BatchReservation(HostStaticVariantBudget owner, Object artifact, Map<Long, Integer> sizes,
                                 long bytes, boolean replacement, ReplacementGroup replacementGroup) {
            this.owner = owner;
            this.artifact = artifact;
            this.unclaimed = new HashMap<>(sizes);
            this.unclaimedBytes = bytes;
            this.replacement = replacement;
            this.replacementGroup = replacementGroup;
        }

        public synchronized Reservation claim(long bytes) {
            if (closed || bytes < 1) return null;
            int count = unclaimed.getOrDefault(bytes, 0);
            if (count == 0) return null;
            if (count == 1) unclaimed.remove(bytes);
            else unclaimed.put(bytes, count - 1);
            unclaimedBytes -= bytes;
            return new Reservation(owner, artifact, bytes, replacement, replacementGroup);
        }

        public synchronized long unclaimedBytes() { return unclaimedBytes; }

        @Override public void close() {
            long release;
            synchronized (this) {
                if (closed) return;
                closed = true;
                release = unclaimedBytes;
                unclaimedBytes = 0;
                unclaimed.clear();
            }
            if (release > 0) owner.release(artifact, release, false, replacement, replacementGroup);
        }

        void promoteReplacementToSteady() { owner.promoteReplacement(replacementGroup); }
    }

    public record ArtifactDiagnostics(long residentBytes, long reservedBytes, int variants) {
        public ArtifactDiagnostics {
            if (residentBytes < 0 || reservedBytes < residentBytes || variants < 0) {
                throw new IllegalArgumentException("invalid static HOST artifact diagnostics");
            }
        }
    }

    private static final class ReplacementGroup {
        private final Object artifact;
        private long remainingBytes;
        private boolean promoted;

        private ReplacementGroup(Object artifact, long bytes) {
            this.artifact = artifact;
            this.remainingBytes = bytes;
        }
    }

    private record BatchSizes(Map<Long, Integer> counts, long totalBytes) {}
}
