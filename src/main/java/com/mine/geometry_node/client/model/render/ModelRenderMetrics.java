package com.mine.geometry_node.client.model.render;

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

    public void beginFrame(long frame) {
        this.frame = frame;
        visiblePrimitives = culledPrimitives = opaqueDraws = transparentDraws = transparentTriangles = 0;
        uploadBytes = uploadNanos = 0;
        frameStarted = System.nanoTime();
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
        mainPass.reset(); shadowPass.reset();
    }

    private static final class CpuWindow {
        private final long[] samples = new long[256];
        private int count;
        private int cursor;
        private long total;
        private long max;
        void add(long nanos) {
            total = Math.addExact(total, nanos);
            max = Math.max(max, nanos);
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
                    "cpuNanosP99", percentile(.99));
        }
        void reset() { count = cursor = 0; total = max = 0; Arrays.fill(samples, 0); }
    }

    private static final class PassMetrics {
        private long calls, workCalls, draws, primitives;
        private long lastCpuNanos;
        private int lastDraws, lastPrimitives;
        private long immediateUploads, immediateBytes, immediateCpuNanos, uniformUploads, uniformBytes;
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
