package com.mine.geometry_node.client.model.render;

import java.util.*;

/** Per-frame and cumulative counters used by diagnostics and performance regression tests. */
public final class ModelRenderMetrics {
    private long frame;
    private int visiblePrimitives;
    private int culledPrimitives;
    private int opaqueDraws;
    private int transparentDraws;
    private int transparentTriangles;
    private long residentCpuBytes;
    private long residentGpuBytes;
    private long residentTextureBytes;
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
        residentCpuBytes = cpu; residentGpuBytes = gpu; residentTextureBytes = texture;
        peakResidentCpuBytes = Math.max(peakResidentCpuBytes, cpu);
        peakResidentGpuBytes = Math.max(peakResidentGpuBytes, gpu);
        peakResidentTextureBytes = Math.max(peakResidentTextureBytes, texture);
    }
    public void endFrame() {
        frames++;
        long elapsed = frameStarted == 0 ? 0 : Math.max(0, System.nanoTime() - frameStarted);
        peakFrameNanos = Math.max(peakFrameNanos, elapsed);
        frameNanos[frameNanosCursor++ & 255] = elapsed;
        if (frameNanosCount < frameNanos.length) frameNanosCount++;
    }
    private long percentile(double p) {
        if (frameNanosCount == 0) return 0;
        long[] values = Arrays.copyOf(frameNanos, frameNanosCount); Arrays.sort(values);
        return values[Math.min(values.length - 1, (int) Math.ceil(p * values.length) - 1)];
    }
    public long frame() { return frame; }
    public Map<String,Object> report() {
        return Map.ofEntries(
                Map.entry("frame", frame), Map.entry("frames", frames),
                Map.entry("visiblePrimitives", visiblePrimitives), Map.entry("culledPrimitives", culledPrimitives),
                Map.entry("opaqueDraws", opaqueDraws), Map.entry("transparentDraws", transparentDraws),
                Map.entry("transparentTriangles", transparentTriangles), Map.entry("residentCpuBytes", residentCpuBytes),
                Map.entry("residentGpuBytes", residentGpuBytes), Map.entry("residentTextureBytes", residentTextureBytes),
                Map.entry("uploadBytes", uploadBytes), Map.entry("uploadNanos", uploadNanos),
                Map.entry("peakUploadBytes", peakUploadBytes), Map.entry("peakUploadNanos", peakUploadNanos),
                Map.entry("peakResidentCpuBytes", peakResidentCpuBytes), Map.entry("peakResidentGpuBytes", peakResidentGpuBytes),
                Map.entry("peakResidentTextureBytes", peakResidentTextureBytes), Map.entry("frameNanosMax", peakFrameNanos),
                Map.entry("totalCulled", totalCulled), Map.entry("totalOpaqueDraws", totalOpaqueDraws),
                Map.entry("totalTransparentDraws", totalTransparentDraws), Map.entry("totalUploadBytes", totalUploadBytes),
                Map.entry("frameNanosP50", percentile(.50)), Map.entry("frameNanosP95", percentile(.95)), Map.entry("frameNanosP99", percentile(.99)),
                Map.entry("frameMillisP50", percentile(.50) / 1_000_000.0), Map.entry("frameMillisP95", percentile(.95) / 1_000_000.0),
                Map.entry("frameMillisP99", percentile(.99) / 1_000_000.0), Map.entry("frameMillisMax", peakFrameNanos / 1_000_000.0)
        );
    }
}
