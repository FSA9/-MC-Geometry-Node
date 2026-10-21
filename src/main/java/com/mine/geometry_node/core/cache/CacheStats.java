package com.mine.geometry_node.core.cache;

/** -1 means unavailable, never an estimate of zero. Bytes exclude shared resources counted elsewhere. */
public record CacheStats(long entries, long memoryBytes, long diskBytes, long retainedEntries) {
    public CacheStats {
        if (entries < -1 || memoryBytes < -1 || diskBytes < -1 || retainedEntries < -1)
            throw new IllegalArgumentException("Invalid cache usage");
    }
    public static CacheStats memory(long entries, long bytes) { return new CacheStats(entries, bytes, 0, 0); }
    public static CacheStats disk(long entries, long bytes) { return new CacheStats(entries, 0, bytes, 0); }
    public static CacheStats unknown() { return new CacheStats(-1, -1, -1, -1); }
    public CacheStats plus(CacheStats other) {
        return new CacheStats(add(entries, other.entries), add(memoryBytes, other.memoryBytes),
                add(diskBytes, other.diskBytes), add(retainedEntries, other.retainedEntries));
    }
    private static long add(long a, long b) {
        if (a < 0 || b < 0) return -1;
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }
}
