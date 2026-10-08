package com.mine.geometry_node.core.engine.system.model.collision.runtime;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Sample outer operations, never GPU waits or a timer for every separating axis. */
public final class ModelCollisionMetrics {
    private static final int INTERVAL = 16, WINDOW = 128;
    private static final Bucket CLIENT_QUERY = new Bucket(), SERVER_QUERY = new Bucket();
    private static final Bucket CLIENT_MOVEMENT = new Bucket(), SERVER_MOVEMENT = new Bucket();
    private static final Bucket DEBUG_BUILD = new Bucket(), DEBUG_RENDER = new Bucket();
    private static final ThreadLocal<Sample> MOVEMENT = new ThreadLocal<>();
    private static volatile Map<String, Object> debugState = Map.of("observed", false);

    private ModelCollisionMetrics() {}

    public static Sample query(boolean client) { return (client ? CLIENT_QUERY : SERVER_QUERY).begin(false, INTERVAL); }
    public static Sample movement(boolean client) { return (client ? CLIENT_MOVEMENT : SERVER_MOVEMENT).begin(true, INTERVAL); }
    public static Sample debugBuild() { return DEBUG_BUILD.begin(false, 1); }
    public static Sample debugRender() { return DEBUG_RENDER.begin(false, INTERVAL); }
    public static Sample currentMovement() { return MOVEMENT.get(); }
    public static void debugEnabled(boolean enabled) {
        var state = new LinkedHashMap<String,Object>(debugState);
        state.put("enabled", enabled);
        debugState = Map.copyOf(state);
    }

    public static void debugState(boolean enabled, int availableParts, int selectedParts, int displayedParts,
                                  int displayedFaces, int displayedEdges, boolean truncated) {
        debugState = Map.of("observed", true, "enabled", enabled, "availableParts", availableParts,
                "selectedParts", selectedParts, "displayedParts", displayedParts, "displayedFaces", displayedFaces,
                "displayedEdges", displayedEdges, "truncated", truncated);
    }

    public static Map<String, Object> report() {
        return Map.of("client", Map.of("query", CLIENT_QUERY.report(), "movement", CLIENT_MOVEMENT.report()),
                "server", Map.of("query", SERVER_QUERY.report(), "movement", SERVER_MOVEMENT.report()),
                "debug", Map.of("state", debugState, "build", DEBUG_BUILD.report(), "render", DEBUG_RENDER.report(),
                        "gpuStatus", "NOT_MEASURED"),
                "samplingInterval", INTERVAL, "debugBuildSamplingInterval", 1,
                "scope", "Counters are process-lifetime; windows carry sample age. Query excludes SAT; movement includes query, SAT and repeated vanilla clipping. Do not add nested timings. SAT counters cover sampled movement only; sneak/floating support probes are outside that scope. Debug render covers the entire shared geometry debug CPU pass when collision meshes are present, not collision-only or GPU execution. Upload bytes are an estimate of submitted vertex/index payload, not driver traffic. Debug state is the last observed viewer snapshot. Counts in sampled windows are not all-frame totals.");
    }

    public static final class Sample implements AutoCloseable {
        private static final Sample NONE = new Sample(null, false);
        private final Bucket bucket;
        private final long started;
        private final boolean movement;
        private final Sample previous;
        public long candidates, nodes, partsTested, satTests, axisTests, hits, contacts, stepAttempts, vanillaClips;
        public long meshes, faces, edges, uploadedBytes, satNanos;

        private Sample(Bucket bucket, boolean movement) {
            this.bucket = bucket;
            this.movement = movement;
            started = bucket == null ? 0 : System.nanoTime();
            previous = movement ? MOVEMENT.get() : null;
            if (movement) MOVEMENT.set(this);
        }

        public boolean sampled() { return bucket != null; }

        @Override public void close() {
            if (bucket == null) return;
            long ended = System.nanoTime();
            if (movement) {
                if (previous == null) MOVEMENT.remove(); else MOVEMENT.set(previous);
            }
            bucket.record(this, ended);
        }
    }

    private static final class Bucket {
        private final AtomicLong calls = new AtomicLong();
        private final long[] duration = new long[WINDOW], times = new long[WINDOW];
        private final long[][] counters = new long[WINDOW][];
        private int count, cursor;
        private long samples, totalNanos, maxNanos;

        Sample begin(boolean movement, int interval) {
            long call = calls.incrementAndGet();
            return (call - 1) % interval == 0 ? new Sample(this, movement) : Sample.NONE;
        }

        synchronized void record(Sample sample, long ended) {
            long elapsed = ended - sample.started;
            duration[cursor] = elapsed; times[cursor] = ended;
            counters[cursor] = new long[]{sample.candidates, sample.nodes, sample.partsTested, sample.satTests,
                    sample.axisTests, sample.hits, sample.contacts, sample.stepAttempts, sample.vanillaClips,
                    sample.meshes, sample.faces, sample.edges, sample.uploadedBytes, sample.satNanos};
            cursor = (cursor + 1) % WINDOW; count = Math.min(WINDOW, count + 1);
            samples++; totalNanos += elapsed; maxNanos = Math.max(maxNanos, elapsed);
        }

        synchronized Map<String, Object> report() {
            long[] sorted = Arrays.copyOf(duration, count); Arrays.sort(sorted);
            long[] totals = new long[14];
            long oldest = Long.MAX_VALUE, latest = 0;
            for (int i = 0; i < count; i++) {
                oldest = Math.min(oldest, times[i]); latest = Math.max(latest, times[i]);
                for (int j = 0; j < totals.length; j++) totals[j] += counters[i][j];
            }
            Map<String, Object> report = new LinkedHashMap<>();
            double ageMs = count == 0 ? -1 : (System.nanoTime() - latest) / 1e6;
            report.put("status", count == 0 ? "NO_SAMPLES" : ageMs > 5000 ? "STALE" : count < 20 ? "LOW_SAMPLE" : "SAMPLED");
            report.put("calls", calls.get()); report.put("timedSamples", samples); report.put("windowSamples", count);
            report.put("sampleAgeMs", ageMs);
            report.put("windowSpanMs", count == 0 ? 0 : (latest - oldest) / 1e6);
            report.put("cpuNanosP50", percentile(sorted, .5)); report.put("cpuNanosP95", percentile(sorted, .95));
            report.put("cpuNanosP99", percentile(sorted, .99)); report.put("sampledCpuNanosTotal", totalNanos);
            report.put("sampledCpuNanosMax", maxNanos);
            String[] names = {"candidates", "nodesVisited", "partsBoundsTested", "satTests", "axesTested",
                    "hits", "contacts", "stepAttempts", "vanillaClips", "meshes", "faces", "edges", "estimatedUploadBytes", "satCpuNanos"};
            Map<String, Long> window = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) window.put(names[i], totals[i]);
            report.put("sampledWindowTotals", window);
            return report;
        }

        private static long percentile(long[] sorted, double quantile) {
            return sorted.length == 0 ? 0 : sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)];
        }
    }
}
