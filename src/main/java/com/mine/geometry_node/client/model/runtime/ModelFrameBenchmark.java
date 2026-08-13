package com.mine.geometry_node.client.model.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded, render-thread-fed benchmark sampler for the shared model render path. */
public final class ModelFrameBenchmark {
    private final String asset;
    private final int instances;
    private final int warmupFrames;
    private final int targetFrames;
    private final List<Long> cpuSamples = new ArrayList<>();
    private final List<Long> gpuSamples = new ArrayList<>();
    private long totalDraws;
    private long totalSubmittedTriangles;
    private int framesSeen;

    public ModelFrameBenchmark(String asset, int instances, int warmupFrames, int targetFrames) {
        if (asset == null || asset.isBlank()) throw new IllegalArgumentException("asset must not be blank");
        if (instances < 1 || warmupFrames < 0 || targetFrames < 1) throw new IllegalArgumentException("invalid benchmark bounds");
        this.asset = asset;
        this.instances = instances;
        this.warmupFrames = warmupFrames;
        this.targetFrames = targetFrames;
    }

    public void recordCpu(long nanos, int draws, long triangles) {
        if (nanos < 0 || cpuComplete()) return;
        framesSeen++;
        if (framesSeen <= warmupFrames) return;
        cpuSamples.add(nanos);
        totalDraws += draws;
        totalSubmittedTriangles += triangles;
    }

    public void recordGpu(long nanos) {
        if (nanos >= 0 && framesSeen > warmupFrames && !gpuComplete()) gpuSamples.add(nanos);
    }

    public Snapshot snapshot() {
        return new Snapshot(asset, instances, warmupFrames, targetFrames, cpuSamples.size(), gpuSamples.size(),
                cpuComplete(), gpuComplete(), average(totalDraws, cpuSamples.size()),
                average(totalSubmittedTriangles, cpuSamples.size()), statistics(cpuSamples), statistics(gpuSamples));
    }

    private boolean cpuComplete() { return cpuSamples.size() >= targetFrames; }
    private boolean gpuComplete() { return gpuSamples.size() >= targetFrames; }
    private static long average(long total, int count) { return count == 0 ? 0 : total / count; }
    private static Statistics statistics(List<Long> samples) {
        if (samples.isEmpty()) return new Statistics(0, 0, 0);
        long total = 0;
        for (long sample : samples) total += sample;
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.naturalOrder());
        int percentile = (int) Math.ceil(sorted.size() * 0.95D) - 1;
        return new Statistics(total / samples.size(), sorted.get(Math.max(0, percentile)), sorted.getLast());
    }

    public record Snapshot(String asset, int instances, int warmupFrames, int targetFrames, int cpuSamples,
                           int gpuSamples, boolean cpuComplete, boolean gpuComplete, long averageDraws,
                           long averageSubmittedTriangles, Statistics cpu, Statistics gpu) {}
    public record Statistics(long meanNanos, long p95Nanos, long maxNanos) {}
}
