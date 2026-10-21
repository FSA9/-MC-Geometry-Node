package com.mine.geometry_node.core.cache;

/** Per-owner result. Retained count -1 is unknown; performed=false is an explicit no-op/skip. */
public record CacheClearOutcome(boolean performed, long retainedEntries) {
    public CacheClearOutcome {
        if (retainedEntries < -1) throw new IllegalArgumentException("Invalid retained count");
    }
    public static CacheClearOutcome completed() { return new CacheClearOutcome(true, -1); }
    public static CacheClearOutcome completed(long retained) { return new CacheClearOutcome(true, retained); }
    public static CacheClearOutcome retained(long retained) { return new CacheClearOutcome(false, retained); }
    public static CacheClearOutcome skipped() { return new CacheClearOutcome(false, 0); }
}
