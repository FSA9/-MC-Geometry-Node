package com.mine.geometry_node.core.engine.system.entity.preview;

import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewLimits;

/** A connection gets a burst of two batches, then four batches per second, even while paused. */
final class EntityPreviewRateLimit {
    static final int BURST = 2;
    static final long INTERVAL_NANOS = EntityPreviewLimits.REQUEST_INTERVAL_NANOS;
    private double tokens = BURST;
    private long last;
    private boolean started;
    boolean acquire(long now) {
        if (started) tokens = Math.min(BURST, tokens + Math.max(0L, now - last) / (double)INTERVAL_NANOS);
        last = now; started = true;
        if (tokens < 1) return false;
        tokens--; return true;
    }
}
