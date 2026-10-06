package com.mine.geometry_node.client.model.render.diagnostic;

import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;

import java.util.*;

/** Render-thread-only CPU submission counters; these are not GPU timings or whole-game frame times. */
public final class ModelRenderMetrics {
    public enum Pass { MAIN, SHADOW }
    public enum IrisPhase { GEOMETRY_RESOLVE, UNIFORM_PREPARE, PASS_OPEN_AND_BIND, FIRST_DRAW_WITH_IRIS_SETUP, SUBSEQUENT_DRAW, PASS_CLOSE }

    private final PassMetrics mainPass = new PassMetrics();
    private final PassMetrics shadowPass = new PassMetrics();
    private long residentGeometryUploadBytes;
    private long residentGeometryUploadCpuNanos;
    private long residentGeometryUploads;
    private long residentGeometryBuildCpuNanos;
    private long residentGeometryBuilds;
    private long sortedIndexUploads, sortedIndexUploadBytes, sortedIndexUploadNanos;
    private long residentChunks, residentChunkCpuNanos, residentChunkCpuNanosMax;
    private long residentAllocations, residentAllocationCpuNanos, residentAllocationCpuNanosMax;
    private long frame;
    private int visiblePrimitives;
    private int culledPrimitives;
    private int opaqueDraws;
    private int transparentDraws;
    private int transparentTriangles;
    private long uploadBytes;
    private long uploadNanos;
    private long peakUploadBytes;
    private long peakUploadNanos;
    private long frames;
    private long totalCulled;
    private long totalOpaqueDraws;
    private long totalTransparentDraws;
    private long totalUploadBytes;
    private final long[] frameNanos = new long[256];
    private int frameNanosCount;
    private int frameNanosCursor;
    private long frameStarted;
    private long peakFrameNanos;
    private long peakResidentCpuBytes;
    private long peakResidentGpuBytes;
    private long peakResidentTextureBytes;
    private final SubsystemMetrics transparentCpu = new SubsystemMetrics();
    private final SubsystemMetrics lightingCpu = new SubsystemMetrics();
    /** Cluster visibility accounting is kept separate from primitive visibility so report consumers can
     * distinguish P7-02 metadata coverage from P7-03 pass culling. */
    private final ClusterCullingMetrics mainClusters = new ClusterCullingMetrics();
    private final ClusterCullingMetrics shadowClusters = new ClusterCullingMetrics();
    private final ClusterCullingMetrics transparentClusters = new ClusterCullingMetrics();
    /** Shadow model-level decisions. Cluster culling remains separately reported below. */
    private final ShadowCullingMetrics shadowCulling = new ShadowCullingMetrics();
    /** Whole-model LOD selection and upload fallback counters. */
    private final ModelLodMetrics modelLod = new ModelLodMetrics();

    public void beginFrame(long frame) {
        this.frame = frame;
        visiblePrimitives = culledPrimitives = opaqueDraws = transparentDraws = transparentTriangles = 0;
        uploadBytes = uploadNanos = 0;
        frameStarted = System.nanoTime();
        modelLod.beginFrame(frame);
    }
    public void recordTransparentCpu(long nanos) { transparentCpu.record(nanos); }
    public void recordLightingCpu(long nanos) { lightingCpu.record(nanos); }

    /** Records one completed main or shadow cluster-culling pass. Counts are non-negative and
     * intentionally do not require visible+culled == candidate: a candidate may be rejected by
     * a later pass (for example a conservative distance test), and older primitive fallback paths
     * may only know a subset of the cluster metadata. */
    public void recordClusterCulling(Pass pass, int candidates, int visible, int culled) {
        clusterMetrics(pass).record(candidates, visible, culled);
    }

    /** Records one model-level Shadow decision. Candidate is normally true once per model/instance. */
    public void recordShadowModelDecision(boolean candidate, boolean visible, boolean skipped,
                                          String reason, double quality, int tier) {
        recordShadowModelDecision(candidate, visible, skipped, reason, Double.NaN, quality, tier);
    }

    /** Records one model-level Shadow decision, including the main-camera distance in world units. */
    public void recordShadowModelDecision(boolean candidate, boolean visible, boolean skipped,
                                          String reason, double distance, double quality, int tier) {
        shadowCulling.record(candidate, visible, skipped, reason, distance, quality, tier);
    }

    /** Records a batch of equivalent Shadow decisions without allocating one event per model. */
    public void recordShadowBatchDecision(int candidates, int visible, int culled, int skipped,
                                          String reason, double quality, int tier) {
        recordShadowBatchDecision(candidates, visible, culled, skipped, reason, Double.NaN, quality, tier);
    }

    /** Records a batch of equivalent Shadow decisions, including the representative distance. */
    public void recordShadowBatchDecision(int candidates, int visible, int culled, int skipped,
                                          String reason, double distance, double quality, int tier) {
        shadowCulling.recordBatch(candidates, visible, culled, skipped, reason, distance, quality, tier);
    }

    public ModelLodMetrics modelLodMetrics() { return modelLod; }

    public record HudSnapshot(long mainCpuNanos, long shadowCpuNanos, long lightingCpuNanos,
                              int mainDraws, int shadowDraws) {}

    public HudSnapshot hudSnapshot() {
        return new HudSnapshot(mainPass.lastCpuNanos, shadowPass.lastCpuNanos, lightingCpu.lastNanos,
                mainPass.lastDraws, shadowPass.lastDraws);
    }

    /** Records transparent cluster visibility independently from the main opaque pass. */
    public void recordTransparentClusterCulling(int candidates, int visible, int culled) {
        transparentClusters.record(candidates, visible, culled);
    }

    private ClusterCullingMetrics clusterMetrics(Pass pass) {
        return switch (Objects.requireNonNull(pass)) {
            case MAIN -> mainClusters;
            case SHADOW -> shadowClusters;
        };
    }

    public void visible() { visiblePrimitives++; }
    public void culled() { culledPrimitives++; totalCulled++; }
    public void opaqueDraw() { opaqueDraws++; totalOpaqueDraws++; }
    public void transparentDraw(int triangles) { transparentDraws++; transparentTriangles += triangles; totalTransparentDraws++; }
    public void upload(long bytes, long nanos) {
        uploadBytes = Math.addExact(uploadBytes, bytes); uploadNanos = Math.addExact(uploadNanos, nanos);
        peakUploadBytes = Math.max(peakUploadBytes, uploadBytes); peakUploadNanos = Math.max(peakUploadNanos, uploadNanos);
        totalUploadBytes = Math.addExact(totalUploadBytes, bytes);
    }
    public void residency(long cpu, long gpu, long texture) {
        peakResidentCpuBytes = Math.max(peakResidentCpuBytes, cpu);
        peakResidentGpuBytes = Math.max(peakResidentGpuBytes, gpu);
        peakResidentTextureBytes = Math.max(peakResidentTextureBytes, texture);
    }
    public void endFrame() {
        frames++;
        long elapsed = frameStarted == 0 ? 0 : Math.max(0, System.nanoTime() - frameStarted);
        // Empty-world frames must not dilute model-work percentiles. These legacy fields
        // describe only the CPU scope between beginFrame/endFrame, not shadow/GPU time.
        if (visiblePrimitives > 0 || culledPrimitives > 0 || opaqueDraws > 0 || transparentDraws > 0 || uploadBytes > 0) {
            peakFrameNanos = Math.max(peakFrameNanos, elapsed);
            frameNanos[frameNanosCursor++ & 255] = elapsed;
            if (frameNanosCount < frameNanos.length) frameNanosCount++;
        }
        frameStarted = 0;
        modelLod.endFrame();
    }
    private long percentile(double p) {
        if (frameNanosCount == 0) return 0;
        long[] values = Arrays.copyOf(frameNanos, frameNanosCount); Arrays.sort(values);
        return values[Math.min(values.length - 1, (int) Math.ceil(p * values.length) - 1)];
    }
    /** Records a completed pass independently of main-frame boundaries (shadow may run first). */
    public void recordPass(Pass pass, long cpuNanos, int draws, int primitives) {
        nonNegative(cpuNanos); nonNegative(draws); nonNegative(primitives);
        PassMetrics metrics = pass(pass);
        metrics.calls++;
        metrics.lastCpuNanos = cpuNanos; metrics.lastDraws = draws; metrics.lastPrimitives = primitives;
        metrics.lastImmediateBytes = metrics.immediateBytes - metrics.recordedImmediateBytes;
        metrics.lastResidentDraws = metrics.residentPassDraws - metrics.recordedResidentDraws;
        metrics.recordedImmediateBytes = metrics.immediateBytes;
        metrics.recordedResidentDraws = metrics.residentPassDraws;
        metrics.draws = Math.addExact(metrics.draws, draws);
        metrics.primitives = Math.addExact(metrics.primitives, primitives);
        if (draws > 0 || primitives > 0) {
            metrics.workCalls++;
            metrics.cpu.add(cpuNanos);
        }
    }

    /** Recorded per chunk even if the primitive is cancelled before completion. */
    public void residentGeometryChunk(long cpuNanos) {
        nonNegative(cpuNanos);
        residentChunks++; residentChunkCpuNanos = Math.addExact(residentChunkCpuNanos, cpuNanos);
        residentChunkCpuNanosMax = Math.max(residentChunkCpuNanosMax, cpuNanos);
    }

    /** Allocation is not a data upload and cannot be preempted inside the GL driver. */
    public void residentGeometryAllocation(long cpuNanos) {
        nonNegative(cpuNanos);
        residentAllocations++; residentAllocationCpuNanos = Math.addExact(residentAllocationCpuNanos, cpuNanos);
        residentAllocationCpuNanosMax = Math.max(residentAllocationCpuNanosMax, cpuNanos);
    }

    public void sortedIndexUpload(long bytes, long nanos) {
        nonNegative(bytes); nonNegative(nanos);
        sortedIndexUploads++; sortedIndexUploadBytes += bytes; sortedIndexUploadNanos += nanos;
    }

    public void residentGeometryBuild(long cpuNanos) {
        nonNegative(cpuNanos);
        residentGeometryBuilds++;
        residentGeometryBuildCpuNanos = Math.addExact(residentGeometryBuildCpuNanos, cpuNanos);
    }

    /** Actual vertex/index transfer bytes, distinct from the legacy bounded-upload queue. */
    public void residentGeometryUpload(long bytes, long cpuNanos) {
        nonNegative(bytes); nonNegative(cpuNanos);
        residentGeometryUploads++;
        residentGeometryUploadBytes = Math.addExact(residentGeometryUploadBytes, bytes);
        residentGeometryUploadCpuNanos = Math.addExact(residentGeometryUploadCpuNanos, cpuNanos);
    }

    public void immediateGeometryUpload(Pass pass, long bytes, long cpuNanos) {
        nonNegative(bytes); nonNegative(cpuNanos);
        PassMetrics metrics = pass(pass);
        metrics.immediateUploads++;
        metrics.immediateBytes = Math.addExact(metrics.immediateBytes, bytes);
        metrics.immediateCpuNanos = Math.addExact(metrics.immediateCpuNanos, cpuNanos);
    }

    public void dynamicUniformUpload(Pass pass, long bytes) {
        nonNegative(bytes);
        PassMetrics metrics = pass(pass);
        metrics.uniformUploads++;
        metrics.uniformBytes = Math.addExact(metrics.uniformBytes, bytes);
    }

    public void irisPhase(Pass pass, IrisPhase phase, long cpuNanos) {
        nonNegative(cpuNanos);
        pass(pass).irisPhases[Objects.requireNonNull(phase).ordinal()].add(cpuNanos);
    }

    public void irisRenderPass(Pass pass, int completedDraws) {
        nonNegative(completedDraws);
        PassMetrics metrics = pass(pass);
        metrics.renderPasses++;
        metrics.residentPassDraws = Math.addExact(metrics.residentPassDraws, completedDraws);
        metrics.maxDrawsPerRenderPass = Math.max(metrics.maxDrawsPerRenderPass, completedDraws);
    }

    private PassMetrics pass(Pass pass) {
        return switch (Objects.requireNonNull(pass)) { case MAIN -> mainPass; case SHADOW -> shadowPass; };
    }

    private static void nonNegative(long value) {
        if (value < 0) throw new IllegalArgumentException("Metric values must be nonnegative");
    }

    /** Explicit session reset. beginFrame deliberately does not reset pass counters. */
    public void reset() {
        frame = frames = frameStarted = peakFrameNanos = 0;
        visiblePrimitives = culledPrimitives = opaqueDraws = transparentDraws = transparentTriangles = 0;
        uploadBytes = uploadNanos = peakUploadBytes = peakUploadNanos = 0;
        totalCulled = totalOpaqueDraws = totalTransparentDraws = totalUploadBytes = 0;
        peakResidentCpuBytes = peakResidentGpuBytes = peakResidentTextureBytes = 0;
        frameNanosCount = frameNanosCursor = 0;
        Arrays.fill(frameNanos, 0);
        residentGeometryUploadBytes = residentGeometryUploadCpuNanos = residentGeometryUploads = 0;
        residentGeometryBuildCpuNanos = residentGeometryBuilds = 0;
        sortedIndexUploads = sortedIndexUploadBytes = sortedIndexUploadNanos = 0;
        residentChunks = residentChunkCpuNanos = residentChunkCpuNanosMax = 0;
        residentAllocations = residentAllocationCpuNanos = residentAllocationCpuNanosMax = 0;
        mainPass.reset(); shadowPass.reset(); transparentCpu.reset(); lightingCpu.reset();
        mainClusters.reset(); shadowClusters.reset(); transparentClusters.reset();
        shadowCulling.reset();
        modelLod.reset();
    }

    private static final class ShadowCullingMetrics {
        private long calls, workCalls;
        private long candidateModels, visibleModels, culledModels, skippedModels;
        private long totalCandidateModels, totalVisibleModels, totalCulledModels, totalSkippedModels;
        private int latestCandidates, latestVisible, latestCulled, latestSkipped;
        private String latestReason = "NONE";
        private double latestDistance;
        private boolean latestDistanceAvailable;
        private double distanceTotal, distanceMin, distanceMax;
        private long distanceSamples;
        private double latestQuality, qualityTotal;
        private long qualitySamples;
        private double qualityMin = 1.0, qualityMax;
        private int latestTier;
        private int minTier, maxTier;
        private final Map<String, Long> skipReasons = new LinkedHashMap<>();
        private final Map<Integer, Long> tierCounts = new LinkedHashMap<>();

        void record(boolean candidate, boolean visible, boolean skipped, String reason,
                    double distance, double quality, int tier) {
            recordBatch(candidate ? 1 : 0, visible ? 1 : 0,
                    candidate && !visible ? 1 : 0, skipped ? 1 : 0,
                    reason, distance, quality, tier);
        }

        void recordBatch(int candidates, int visible, int culled, int skipped,
                         String reason, double distance, double quality, int tier) {
            if (candidates < 0 || visible < 0 || culled < 0 || skipped < 0) {
                throw new IllegalArgumentException("Shadow counts must be nonnegative");
            }
            if (!Double.isFinite(quality)) throw new IllegalArgumentException("Shadow quality must be finite");
            if (quality < 0.0 || quality > 1.0) throw new IllegalArgumentException("Shadow quality must be in [0,1]");
            if (tier < 0) throw new IllegalArgumentException("Shadow tier must be nonnegative");
            calls++;
            latestCandidates = candidates; latestVisible = visible; latestCulled = culled; latestSkipped = skipped;
            latestReason = normalizeReason(reason);
            latestDistanceAvailable = Double.isFinite(distance) && distance >= 0.0;
            latestDistance = latestDistanceAvailable ? distance : 0.0;
            if (latestDistanceAvailable) {
                distanceSamples++; distanceTotal += distance;
                distanceMin = distanceSamples == 1 ? distance : Math.min(distanceMin, distance);
                distanceMax = distanceSamples == 1 ? distance : Math.max(distanceMax, distance);
            }
            latestQuality = quality; latestTier = tier;
            candidateModels = candidates; visibleModels = visible; culledModels = culled; skippedModels = skipped;
            totalCandidateModels = Math.addExact(totalCandidateModels, candidates);
            totalVisibleModels = Math.addExact(totalVisibleModels, visible);
            totalCulledModels = Math.addExact(totalCulledModels, culled);
            totalSkippedModels = Math.addExact(totalSkippedModels, skipped);
            if (candidates > 0 || visible > 0 || culled > 0 || skipped > 0) workCalls++;
            if (skipped > 0) skipReasons.merge(latestReason, (long) skipped, Long::sum);
            qualitySamples++; qualityTotal += quality;
            qualityMin = Math.min(qualityMin, quality); qualityMax = Math.max(qualityMax, quality);
            minTier = qualitySamples == 1 ? tier : Math.min(minTier, tier);
            maxTier = qualitySamples == 1 ? tier : Math.max(maxTier, tier);
            tierCounts.merge(tier, 1L, Long::sum);
        }

        private static String normalizeReason(String reason) {
            if (reason == null || reason.isBlank()) return "NONE";
            return reason.trim().toUpperCase(Locale.ROOT);
        }

        Map<String, Object> report() {
            Map<String, Object> latest = new LinkedHashMap<>();
            latest.put("candidateModels", latestCandidates);
            latest.put("visibleModels", latestVisible);
            latest.put("culledModels", latestCulled);
            latest.put("skippedModels", latestSkipped);
            latest.put("skipReason", latestReason);
            latest.put("distance", latestDistanceAvailable ? latestDistance : null);
            latest.put("quality", latestQuality);
            latest.put("tier", latestTier);
            latest.put("hasWork", latestCandidates > 0 || latestVisible > 0 || latestCulled > 0 || latestSkipped > 0);
            Map<String, Object> quality = new LinkedHashMap<>();
            quality.put("samples", qualitySamples);
            quality.put("last", latestQuality);
            quality.put("average", qualitySamples == 0 ? 0.0 : qualityTotal / qualitySamples);
            quality.put("min", qualitySamples == 0 ? 0.0 : qualityMin);
            quality.put("max", qualitySamples == 0 ? 0.0 : qualityMax);
            quality.put("lastTier", latestTier);
            quality.put("minTier", qualitySamples == 0 ? 0 : minTier);
            quality.put("maxTier", qualitySamples == 0 ? 0 : maxTier);
            quality.put("tierCounts", new LinkedHashMap<>(tierCounts));
            Map<String, Object> distance = new LinkedHashMap<>();
            distance.put("samples", distanceSamples);
            distance.put("last", latestDistanceAvailable ? latestDistance : null);
            distance.put("average", distanceSamples == 0 ? null : distanceTotal / distanceSamples);
            distance.put("min", distanceSamples == 0 ? null : distanceMin);
            distance.put("max", distanceSamples == 0 ? null : distanceMax);
            return Map.ofEntries(
                    Map.entry("calls", calls), Map.entry("workCalls", workCalls),
                    Map.entry("candidateModels", candidateModels), Map.entry("visibleModels", visibleModels),
                    Map.entry("culledModels", culledModels), Map.entry("skippedModels", skippedModels),
                    Map.entry("totalCandidateModels", totalCandidateModels), Map.entry("totalVisibleModels", totalVisibleModels),
                    Map.entry("totalCulledModels", totalCulledModels), Map.entry("totalSkippedModels", totalSkippedModels),
                    Map.entry("skipReasons", new LinkedHashMap<>(skipReasons)),
                    Map.entry("distance", distance), Map.entry("quality", quality), Map.entry("latest", latest));
        }

        void reset() {
            calls = workCalls = candidateModels = visibleModels = culledModels = skippedModels = 0;
            totalCandidateModels = totalVisibleModels = totalCulledModels = totalSkippedModels = 0;
            latestCandidates = latestVisible = latestCulled = latestSkipped = 0;
            latestReason = "NONE"; latestDistance = distanceTotal = distanceMin = distanceMax = 0.0;
            latestDistanceAvailable = false; distanceSamples = 0;
            latestQuality = qualityTotal = 0.0; qualitySamples = 0;
            qualityMin = 1.0; qualityMax = 0.0; latestTier = minTier = maxTier = 0;
            skipReasons.clear(); tierCounts.clear();
        }
    }

    private static final class ClusterCullingMetrics {
        private long calls, workCalls;
        private long candidateClusters, visibleClusters, culledClusters;
        private long totalCandidateClusters, totalVisibleClusters, totalCulledClusters;
        private int latestCandidates, latestVisible, latestCulled;

        void record(int candidates, int visible, int culled) {
            if (candidates < 0 || visible < 0 || culled < 0) {
                throw new IllegalArgumentException("Cluster counts must be nonnegative");
            }
            calls++;
            latestCandidates = candidates;
            latestVisible = visible;
            latestCulled = culled;
            candidateClusters = candidates;
            visibleClusters = visible;
            culledClusters = culled;
            totalCandidateClusters = Math.addExact(totalCandidateClusters, candidates);
            totalVisibleClusters = Math.addExact(totalVisibleClusters, visible);
            totalCulledClusters = Math.addExact(totalCulledClusters, culled);
            if (candidates > 0 || visible > 0 || culled > 0) workCalls++;
        }

        Map<String, Object> report() {
            Map<String, Object> latest = new LinkedHashMap<>();
            latest.put("candidateClusters", latestCandidates);
            latest.put("visibleClusters", latestVisible);
            latest.put("culledClusters", latestCulled);
            latest.put("hasWork", latestCandidates > 0 || latestVisible > 0 || latestCulled > 0);
            return Map.ofEntries(
                    Map.entry("calls", calls),
                    Map.entry("workCalls", workCalls),
                    Map.entry("candidateClusters", candidateClusters),
                    Map.entry("visibleClusters", visibleClusters),
                    Map.entry("culledClusters", culledClusters),
                    Map.entry("totalCandidateClusters", totalCandidateClusters),
                    Map.entry("totalVisibleClusters", totalVisibleClusters),
                    Map.entry("totalCulledClusters", totalCulledClusters),
                    Map.entry("latest", latest));
        }

        void reset() {
            calls = workCalls = candidateClusters = visibleClusters = culledClusters = 0;
            totalCandidateClusters = totalVisibleClusters = totalCulledClusters = 0;
            latestCandidates = latestVisible = latestCulled = 0;
        }
    }

    private static final class SubsystemMetrics {
        private long calls, workCalls, lastNanos, totalNanos, peakNanos;
        private final CpuWindow cpu = new CpuWindow();
        void record(long nanos) {
            nonNegative(nanos); calls++; lastNanos = nanos; peakNanos = Math.max(peakNanos, nanos);
            if (nanos > 0) { workCalls++; totalNanos = Math.addExact(totalNanos, nanos); cpu.add(nanos); }
        }
        Map<String,Object> report() {
            return Map.of("calls", calls, "workCalls", workCalls, "lastNanos", lastNanos,
                    "totalNanos", totalNanos, "peakNanos", peakNanos, "cpu", cpu.report(),
                    "status", calls == 0 ? "UNTESTED" : workCalls == 0 ? "ZERO_WORK" : "ACTIVE");
        }
        void reset() { calls = workCalls = lastNanos = totalNanos = peakNanos = 0; cpu.reset(); }
    }

    private static final class CpuWindow {
        private final long[] samples = new long[256];
        private final long[] sampledAt = new long[256];
        private long lastSampledAt;
        private int count;
        private int cursor;
        private long total;
        private long max;
        void add(long nanos) {
            total = Math.addExact(total, nanos);
            max = Math.max(max, nanos);
            lastSampledAt = System.nanoTime();
            sampledAt[cursor & 255] = lastSampledAt;
            samples[cursor++ & 255] = nanos;
            count = Math.min(count + 1, samples.length);
        }
        long percentile(double p) {
            if (count == 0) return 0;
            long[] sorted = Arrays.copyOf(samples, count);
            Arrays.sort(sorted);
            return sorted[(int) Math.ceil(p * count) - 1];
        }
        Map<String, Object> report() {
            return Map.of("windowSamples", count, "windowCapacity", samples.length,
                    "cpuNanosTotal", total, "cpuNanosMax", max,
                    "cpuNanosP50", percentile(.50), "cpuNanosP95", percentile(.95),
                    "cpuNanosP99", percentile(.99),
                    "sampleAgeMs", count == 0 ? -1.0 : Math.max(0, System.nanoTime() - lastSampledAt) / 1_000_000.0,
                    "windowSpanMs", count == 0 ? 0.0 : Math.max(0, lastSampledAt - sampledAt[count == samples.length ? cursor & 255 : 0]) / 1_000_000.0);
        }
        void reset() { count = cursor = 0; total = max = 0; Arrays.fill(samples, 0); }
    }

    private static final class PassMetrics {
        private long calls, workCalls, draws, primitives;
        private long lastCpuNanos;
        private int lastDraws, lastPrimitives;
        private long immediateUploads, immediateBytes, immediateCpuNanos, uniformUploads, uniformBytes;
        private long lastImmediateBytes, lastResidentDraws, recordedImmediateBytes, recordedResidentDraws;
        private final CpuWindow cpu = new CpuWindow();
        private final CpuWindow[] irisPhases = Arrays.stream(IrisPhase.values()).map(ignored -> new CpuWindow()).toArray(CpuWindow[]::new);
        private long renderPasses, residentPassDraws, maxDrawsPerRenderPass;
        private Map<String, Object> submissionReport() {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("renderPasses", renderPasses);
            report.put("completedDraws", residentPassDraws);
            report.put("maxDrawsPerRenderPass", maxDrawsPerRenderPass);
            report.put("scope", "CPU submission phases; first draw includes deferred Iris setup and actual draw submission, not isolated setup or GPU time");
            for (IrisPhase phase : IrisPhase.values()) report.put(phase.name(), irisPhases[phase.ordinal()].report());
            return report;
        }
        Map<String, Object> report() {
            return Map.ofEntries(Map.entry("calls", calls), Map.entry("workCalls", workCalls),
                    Map.entry("draws", draws), Map.entry("primitives", primitives), Map.entry("cpu", cpu.report()),
                    Map.entry("latest", Map.of("cpuNanos", lastCpuNanos, "draws", lastDraws, "primitives", lastPrimitives,
                            "immediateGeometryUploadBytes", lastImmediateBytes, "irisResidentDraws", lastResidentDraws,
                            "hasWork", lastDraws > 0 || lastPrimitives > 0)),
                    Map.entry("irisSubmission", submissionReport()),
                    Map.entry("immediateGeometryUploads", immediateUploads), Map.entry("immediateGeometryUploadBytes", immediateBytes),
                    Map.entry("immediateGeometryUploadCpuNanos", immediateCpuNanos),
                    Map.entry("dynamicUniformUploads", uniformUploads), Map.entry("dynamicUniformUploadBytes", uniformBytes));
        }
        void reset() {
            calls = workCalls = draws = primitives = lastCpuNanos = 0;
            lastDraws = lastPrimitives = 0;
            immediateUploads = immediateBytes = immediateCpuNanos = uniformUploads = uniformBytes = 0;
            lastImmediateBytes = lastResidentDraws = recordedImmediateBytes = recordedResidentDraws = 0;
            cpu.reset();
            renderPasses = residentPassDraws = maxDrawsPerRenderPass = 0;
            for (CpuWindow phase : irisPhases) phase.reset();
        }
    }

    public long frame() { return frame; }
    public Map<String,Object> report() {
        return Map.ofEntries(
                Map.entry("timingScope", "mainPass/shadowPass: CPU submission only; excludes GPU and whole-game frame time; active-work samples only"),
                Map.entry("legacyUploadScope", "asset texture initialization queue (including generated material textures); geometryTransfers tracks lazy resident geometry"),
                Map.entry("mainPass", mainPass.report()), Map.entry("shadowPass", shadowPass.report()),
                Map.entry("transparentCpu", transparentCpu.report()), Map.entry("lightingCpu", lightingCpu.report()),
                Map.entry("clusterCulling", Map.of(
                        "main", mainClusters.report(),
                        "shadow", shadowClusters.report(),
                        "transparent", transparentClusters.report())),
                Map.entry("shadowCulling", shadowCulling.report()),
                Map.entry("modelLod", modelLod.report()),
                Map.entry("sortedIndexTransfers", Map.of("uploads", sortedIndexUploads, "bytes", sortedIndexUploadBytes, "cpuNanos", sortedIndexUploadNanos)),
                Map.entry("geometryConstruction", Map.of("chunks", residentChunks, "chunkCpuNanos", residentChunkCpuNanos,
                        "chunkCpuNanosMax", residentChunkCpuNanosMax, "allocations", residentAllocations,
                        "allocationCpuNanos", residentAllocationCpuNanos, "allocationCpuNanosMax", residentAllocationCpuNanosMax)),
                Map.entry("geometryTransfers", Map.of(
                        "residentBuilds", residentGeometryBuilds, "residentBuildCpuNanos", residentGeometryBuildCpuNanos,
                        "residentUploads", residentGeometryUploads, "residentUploadBytes", residentGeometryUploadBytes,
                        "residentUploadCpuNanos", residentGeometryUploadCpuNanos,
                        "immediateUploadBytes", mainPass.immediateBytes + shadowPass.immediateBytes,
                        "totalGeometryUploadBytes", residentGeometryUploadBytes + mainPass.immediateBytes + shadowPass.immediateBytes + sortedIndexUploadBytes,
                        "dynamicUniformUploadBytes", mainPass.uniformBytes + shadowPass.uniformBytes)),
                Map.entry("hostInterval", Map.of("scope", "Elapsed main submission to transparent completion, including host/Iris stages; NOT model-only or whole-game frame time",
                        "windowSamples", frameNanosCount, "windowCapacity", frameNanos.length,
                        "nanosP50", percentile(.50), "nanosP95", percentile(.95), "nanosP99", percentile(.99), "nanosMax", peakFrameNanos)),
                Map.entry("frames", frames),
                Map.entry("visiblePrimitives", visiblePrimitives), Map.entry("culledPrimitives", culledPrimitives),
                Map.entry("opaqueDraws", opaqueDraws), Map.entry("transparentDraws", transparentDraws),
                Map.entry("transparentTriangles", transparentTriangles),
                Map.entry("uploadBytes", uploadBytes), Map.entry("uploadNanos", uploadNanos),
                Map.entry("peakUploadBytes", peakUploadBytes), Map.entry("peakUploadNanos", peakUploadNanos),
                Map.entry("peakResidentCpuBytes", peakResidentCpuBytes), Map.entry("peakResidentGpuBytes", peakResidentGpuBytes),
                Map.entry("peakResidentTextureBytes", peakResidentTextureBytes),
                Map.entry("totalCulled", totalCulled), Map.entry("totalOpaqueDraws", totalOpaqueDraws),
                Map.entry("totalTransparentDraws", totalTransparentDraws), Map.entry("totalUploadBytes", totalUploadBytes)
        );
    }
}
