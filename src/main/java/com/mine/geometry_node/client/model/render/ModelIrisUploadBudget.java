package com.mine.geometry_node.client.model.render;

import java.util.Map;
import java.util.Objects;

/** Render-thread gate shared by main and shadow cache construction. Time limits are checked between triangle-aligned chunks. */
public final class ModelIrisUploadBudget {
    public static final ModelIrisUploadBudget INSTANCE = new ModelIrisUploadBudget();
    private boolean initialized;
    private long frame;
    private long byteLimit, nanoLimit;
    private long frameBytes, frameNanos, frameItems;
    private long admittedItems, deferredAttempts, oversizeItems, timeOverruns;

    public void beginFrame(long frame, ModelRenderBudget budget) {
        Objects.requireNonNull(budget);
        if (initialized && this.frame == frame) return;
        initialized = true; this.frame = frame;
        byteLimit = budget.maxUploadBytesPerFrame(); nanoLimit = budget.maxUploadNanosPerFrame();
        frameBytes = frameNanos = frameItems = 0;
    }

    /** The first chunk may exceed a sub-triangle byte limit so one complete triangle can always progress. */
    public boolean tryAcquire(long bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("nonpositive Iris geometry upload");
        if (!initialized) throw new IllegalStateException("Iris upload budget frame has not begun");
        if (frameItems > 0 && (frameNanos >= nanoLimit || frameBytes >= byteLimit || bytes > byteLimit - frameBytes)) {
            deferredAttempts++; return false;
        }
        if (bytes > byteLimit) oversizeItems++;
        frameBytes = Math.addExact(frameBytes, bytes); frameItems++; admittedItems++;
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
        return Map.of("initialized", initialized, "frame", frame, "frameAdmittedBytes", frameBytes,
                "frameBuildAndUploadCpuNanos", frameNanos, "frameAdmittedItems", frameItems,
                "admittedItems", admittedItems, "deferredAttempts", deferredAttempts,
                "oversizeItems", oversizeItems, "timeOverrunFrames", timeOverruns);
    }

    public void reset() {
        initialized = false; frame = byteLimit = nanoLimit = 0;
        frameBytes = frameNanos = frameItems = admittedItems = deferredAttempts = oversizeItems = timeOverruns = 0;
    }
}
