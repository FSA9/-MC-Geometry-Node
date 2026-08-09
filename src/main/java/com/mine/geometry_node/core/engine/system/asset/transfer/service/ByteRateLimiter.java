package com.mine.geometry_node.core.engine.system.asset.transfer.service;

/** Thread-safe byte token bucket. A rate of zero means unlimited. */
public final class ByteRateLimiter {
    private long bytesPerSecond;
    private double tokens;
    private long lastRefillNanos = System.nanoTime();

    public ByteRateLimiter(long bytesPerSecond) {
        setRate(bytesPerSecond);
    }

    public synchronized void setRate(long value) {
        refill();
        bytesPerSecond = Math.max(0L, value);
        tokens = bytesPerSecond == 0L ? 0.0 : Math.min(tokens, bytesPerSecond);
    }

    /** Returns the nanoseconds to wait before consuming the requested bytes. */
    public synchronized long reserveDelayNanos(int byteCount) {
        if (byteCount <= 0 || bytesPerSecond == 0L) return 0L;
        refill();
        if (tokens >= byteCount) {
            tokens -= byteCount;
            return 0L;
        }
        double missing = byteCount - tokens;
        tokens = 0.0;
        long delay = (long) Math.ceil(missing * 1_000_000_000.0 / bytesPerSecond);
        lastRefillNanos = System.nanoTime() + delay;
        return delay;
    }

    private void refill() {
        if (bytesPerSecond == 0L) return;
        long now = System.nanoTime();
        if (now <= lastRefillNanos) return;
        tokens = Math.min(bytesPerSecond, tokens + (now - lastRefillNanos) * bytesPerSecond / 1_000_000_000.0);
        lastRefillNanos = now;
    }
}
