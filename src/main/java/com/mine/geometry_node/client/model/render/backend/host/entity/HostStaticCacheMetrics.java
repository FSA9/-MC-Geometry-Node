package com.mine.geometry_node.client.model.render.backend.host.entity;

/** Process-lifetime counters for static HOST cache decisions and ownership events. */
public final class HostStaticCacheMetrics {
    public static final HostStaticCacheMetrics INSTANCE = new HostStaticCacheMetrics();

    private long hits;
    private long misses;
    private long buildsStarted;
    private long buildsCompleted;
    private long buildsFailed;
    private long retiredVariants;
    private long retiredBytes;
    private long budgetWaits;
    private long budgetRejects;

    HostStaticCacheMetrics() {}

    synchronized void recordHit() { hits = increment(hits); }
    synchronized void recordMiss() { misses = increment(misses); }
    synchronized void recordBuildStarted() { buildsStarted = increment(buildsStarted); }
    synchronized void recordBuildCompleted() { buildsCompleted = increment(buildsCompleted); }
    synchronized void recordBuildFailed() { buildsFailed = increment(buildsFailed); }
    synchronized void recordBudgetWait() { budgetWaits = increment(budgetWaits); }
    synchronized void recordBudgetReject() { budgetRejects = increment(budgetRejects); }

    synchronized void recordRetired(long variants, long bytes) {
        if (variants < 0 || bytes < 0) throw new IllegalArgumentException("negative static HOST retirement");
        retiredVariants = add(retiredVariants, variants);
        retiredBytes = add(retiredBytes, bytes);
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(hits, misses, buildsStarted, buildsCompleted, buildsFailed,
                retiredVariants, retiredBytes, budgetWaits, budgetRejects);
    }

    synchronized void reset() {
        hits = misses = buildsStarted = buildsCompleted = buildsFailed = 0;
        retiredVariants = retiredBytes = budgetWaits = budgetRejects = 0;
    }

    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }

    private static long add(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public record Diagnostics(long hits, long misses,
                              long buildsStarted, long buildsCompleted, long buildsFailed,
                              long retiredVariants, long retiredBytes,
                              long budgetWaits, long budgetRejects) {
        public Diagnostics {
            if (hits < 0 || misses < 0 || buildsStarted < 0 || buildsCompleted < 0 || buildsFailed < 0
                    || retiredVariants < 0 || retiredBytes < 0 || budgetWaits < 0 || budgetRejects < 0) {
                throw new IllegalArgumentException("negative static HOST cache diagnostics");
            }
        }
    }
}
