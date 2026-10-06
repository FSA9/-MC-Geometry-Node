package com.mine.geometry_node.client.model.render.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Shared render-thread upload gate; each consumer can make one bounded step per frame. */
public final class ModelIrisUploadBudget {
    public static final ModelIrisUploadBudget INSTANCE = new ModelIrisUploadBudget();
    public static final long MAX_OCCLUSION_CHUNK_BYTES = 128 * 1024;
    private boolean initialized;
    private long frame;
    private long byteLimit, nanoLimit;
    private long frameBytes, frameNanos, frameItems;
    private long frameGeometryItems, frameOcclusionItems, frameOcclusionBytes, progressAdmissions;
    private long admittedItems, deferredAttempts, oversizeItems, timeOverruns;

    public void beginFrame(long frame, ModelRenderBudget budget) {
        Objects.requireNonNull(budget);
        if (initialized && this.frame == frame) return;
        initialized = true; this.frame = frame;
        byteLimit = budget.maxUploadBytesPerFrame(); nanoLimit = budget.maxUploadNanosPerFrame();
        frameBytes = frameNanos = frameItems = 0;
        frameGeometryItems = frameOcclusionItems = frameOcclusionBytes = 0;
    }

    public long frame() { return initialized ? frame : Long.MIN_VALUE; }

    /** The first chunk may exceed a sub-triangle byte limit so one complete triangle can always progress. */
    public boolean tryAcquire(long bytes) {
        return tryAcquire(bytes, false);
    }

    /** A late BVH upload must not starve behind shadow geometry on every frame. */
    public boolean tryAcquireOcclusion(long bytes) {
        if (bytes > MAX_OCCLUSION_CHUNK_BYTES) throw new IllegalArgumentException("occlusion chunk exceeds progress allowance");
        return tryAcquire(bytes, true);
    }

    private boolean tryAcquire(long bytes, boolean occlusion) {
        if (bytes <= 0) throw new IllegalArgumentException("nonpositive Iris geometry upload");
        if (!initialized) throw new IllegalStateException("Iris upload budget frame has not begun");
        boolean exhausted = frameItems > 0 && (frameNanos >= nanoLimit || frameBytes >= byteLimit || bytes > byteLimit - frameBytes);
        long consumerItems = occlusion ? frameOcclusionItems : frameGeometryItems;
        if (exhausted && consumerItems > 0) {
            deferredAttempts++; return false;
        }
        // Limits remain shared. The only extra work is the other consumer's first atomic chunk.
        if (exhausted) progressAdmissions++;
        if (bytes > byteLimit) oversizeItems++;
        frameBytes = Math.addExact(frameBytes, bytes); frameItems++; admittedItems++;
        if (occlusion) { frameOcclusionItems++; frameOcclusionBytes += bytes; }
        else frameGeometryItems++;
        return true;
    }

    /** Include CPU construction and upload submission; an individual chunk or GPU allocation cannot be preempted. */
    public void recordElapsed(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("negative Iris upload time");
        if (!initialized) throw new IllegalStateException("Iris upload budget frame has not begun");
        long previous = frameNanos;
        frameNanos = Math.addExact(frameNanos, nanos);
        if (previous <= nanoLimit && frameNanos > nanoLimit) timeOverruns++;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initialized", initialized); result.put("frame", frame);
        result.put("frameAdmittedBytes", frameBytes); result.put("frameBuildAndUploadCpuNanos", frameNanos);
        // The general gate also serves transparent indices and placed-light field uploads.
        result.put("frameAdmittedItems", frameItems); result.put("frameGeneralItems", frameGeometryItems);
        result.put("frameOcclusionItems", frameOcclusionItems); result.put("frameOcclusionBytes", frameOcclusionBytes);
        result.put("admittedItems", admittedItems); result.put("deferredAttempts", deferredAttempts);
        result.put("oversizeItems", oversizeItems); result.put("timeOverrunFrames", timeOverruns);
        result.put("progressAdmissions", progressAdmissions);
        return result;
    }

    public void reset() {
        initialized = false; frame = byteLimit = nanoLimit = 0;
        frameBytes = frameNanos = frameItems = admittedItems = deferredAttempts = oversizeItems = timeOverruns = 0;
        frameGeometryItems = frameOcclusionItems = frameOcclusionBytes = progressAdmissions = 0;
    }
}
