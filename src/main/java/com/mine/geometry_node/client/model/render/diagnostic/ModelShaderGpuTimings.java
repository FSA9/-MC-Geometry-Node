package com.mine.geometry_node.client.model.render.diagnostic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/** Asynchronous timestamps around Iris fullscreen draws; this is not BVH-only GPU time. */
public final class ModelShaderGpuTimings {
    private static final Tracker TRACKER = new Tracker(new OpenGlDriver(), 256);

    private ModelShaderGpuTimings() {}

    public static void poll() { TRACKER.poll(); }
    public static int begin(String pass) { return TRACKER.begin(pass); }
    public static void end(int token) { TRACKER.end(token); }
    public static Map<String, Object> report() { return TRACKER.report(); }
    public static void clearSamples() { TRACKER.clearSamples(); }
    public static void clear() { TRACKER.clear(); }
    public static void reset() { clearSamples(); }
    public static void beginCapture() { TRACKER.beginCapture(); }
    public static void endCapture() { TRACKER.endCapture(); }
    public static Map<String, Object> captureReport() { return TRACKER.captureReport(); }
    public static void releaseCapture() { TRACKER.releaseCapture(); }

    interface Driver {
        boolean supported();
        int create();
        void timestamp(int query);
        boolean available(int query);
        long result(int query);
        void delete(int query);
    }

    static final class OpenGlDriver implements Driver {
        public boolean supported() {
            var capabilities = GL.getCapabilities();
            return (capabilities.OpenGL33 || capabilities.GL_ARB_timer_query)
                    && GL15C.glGetQueryi(GL33C.GL_TIMESTAMP, GL15C.GL_QUERY_COUNTER_BITS) > 0;
        }
        public int create() { return GL15C.glGenQueries(); }
        public void timestamp(int query) { GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP); }
        public boolean available(int query) { return GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0; }
        public long result(int query) { return GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT); }
        public void delete(int query) { GL15C.glDeleteQueries(query); }
    }

    static final class Tracker {
        private static final int MAX_PASSES = 64;
        private final Driver driver;
        private final Slot[] slots;
        private final Map<String, Samples> passes = new LinkedHashMap<>();
        private int read, write, pending, active = -1;
        private long epoch, dropped, discarded, failures;
        private boolean initialized, supported;
        private String failure = "";
        private long captureGeneration;
        private Capture capture;

        Tracker(Driver driver, int capacity) {
            if (capacity < 1 || capacity > 256) throw new IllegalArgumentException("invalid query capacity");
            this.driver = driver;
            slots = new Slot[capacity];
        }

        int begin(String pass) {
            if (pass == null || pass.isBlank()) return -1;
            if (!initialize()) {
                captureDropped(pass);
                return -1;
            }
            if (active >= 0 || pending == slots.length || !passes.containsKey(pass) && passes.size() >= MAX_PASSES) {
                dropped++;
                captureDropped(pass);
                return -1;
            }
            try {
                Slot slot = slots[write];
                if (slot == null) slots[write] = slot = new Slot();
                if (slot.start == 0) slot.start = driver.create();
                if (slot.end == 0) slot.end = driver.create();
                if (slot.start == 0 || slot.end == 0) throw new IllegalStateException("GPU query allocation failed");
                slot.pass = pass;
                slot.epoch = epoch;
                slot.captureGeneration = capture != null && capture.accepting ? capture.generation : 0;
                slot.sampledAt = System.nanoTime();
                passes.computeIfAbsent(pass, ignored -> new Samples());
                driver.timestamp(slot.start);
                active = write;
                if (slot.captureGeneration != 0) {
                    capture.submitted++;
                    capture.pending++;
                    capture.pass(pass).submitted++;
                }
                return active;
            } catch (RuntimeException error) {
                captureDropped(pass);
                disable(error);
                return -1;
            }
        }

        void end(int token) {
            if (token < 0 || token != active || !supported) return;
            try {
                driver.timestamp(slots[token].end);
                active = -1;
                pending++;
                write = (write + 1) % slots.length;
            } catch (RuntimeException error) {
                disable(error);
            }
        }

        void poll() {
            if (!initialized || !supported) return;
            try {
                while (pending > 0) {
                    Slot slot = slots[read];
                    // Never request a result until the GPU has completed the closing timestamp.
                    if (!driver.available(slot.end)) break;
                    boolean captured = capture != null && slot.captureGeneration == capture.generation;
                    if (slot.epoch == epoch || captured) {
                        long elapsed = driver.result(slot.end) - driver.result(slot.start);
                        if (slot.epoch == epoch && elapsed >= 0)
                            passes.computeIfAbsent(slot.pass, ignored -> new Samples()).add(elapsed, slot.sampledAt);
                        else discarded++;
                        if (captured) {
                            capture.pending--;
                            var pass = capture.pass(slot.pass);
                            if (elapsed >= 0) {
                                capture.completed++;
                                pass.samples.add(elapsed, slot.sampledAt);
                            } else {
                                capture.discarded++;
                                pass.discarded++;
                            }
                        }
                    } else {
                        discarded++;
                    }
                    slot.pass = null;
                    pending--;
                    read = (read + 1) % slots.length;
                }
            } catch (RuntimeException error) {
                disable(error);
            }
        }

        private boolean initialize() {
            if (!initialized) {
                initialized = true;
                try { supported = driver.supported(); }
                catch (RuntimeException error) { disable(error); }
            }
            return supported;
        }

        void clearSamples() {
            epoch++;
            passes.clear();
            dropped = discarded = 0;
        }

        void clear() {
            terminateCapture("RESET", "GPU timing resources were cleared during capture.");
            releaseQueries();
            clearSamples();
            initialized = supported = false;
            failure = "";
            failures = 0;
        }

        private void disable(RuntimeException error) {
            failures++;
            failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            terminateCapture("FAILED", failure);
            supported = false;
            releaseQueries();
        }

        private void releaseQueries() {
            for (int i = 0; i < slots.length; i++) {
                Slot slot = slots[i];
                if (slot == null) continue;
                if (slot.start != 0) try { driver.delete(slot.start); } catch (RuntimeException ignored) {}
                if (slot.end != 0) try { driver.delete(slot.end); } catch (RuntimeException ignored) {}
                slots[i] = null;
            }
            read = write = pending = 0;
            active = -1;
        }

        void beginCapture() {
            if (capture != null) throw new IllegalStateException("Release the previous GPU timing capture first");
            capture = new Capture(++captureGeneration);
        }

        void endCapture() {
            if (capture != null) capture.accepting = false;
        }

        void releaseCapture() { capture = null; }

        private void captureDropped(String pass) {
            if (capture == null || !capture.accepting) return;
            capture.dropped++;
            if (capture.passes.containsKey(pass) || capture.passes.size() < MAX_PASSES)
                capture.pass(pass).dropped++;
        }

        private void terminateCapture(String status, String reason) {
            if (capture == null || !capture.accepting && capture.pending == 0) return;
            capture.accepting = false;
            capture.status = status;
            capture.failure = reason;
            capture.discarded += capture.pending;
            capture.pending = 0;
            for (var pass : capture.passes.values())
                pass.discarded = pass.submitted - pass.samples.count;
        }

        Map<String, Object> captureReport() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", initialized && supported);
            result.put("status", capture == null ? "NOT_CAPTURING" : !capture.status.isEmpty() ? capture.status
                    : !failure.isEmpty() ? "FAILED" : !initialized ? "NOT_SAMPLED" : !supported ? "UNSUPPORTED"
                    : capture.accepting ? "CAPTURING" : capture.pending > 0 ? "DRAINING" : "COMPLETE");
            result.put("scope", "Only queries begun inside this capture window; completed results drain asynchronously after endCapture. Global timing resets do not change capture ownership. Samples are per draw, not per frame.");
            result.put("pendingQueries", capture == null ? 0 : capture.pending);
            result.put("maxPendingQueries", slots.length);
            result.put("submittedQueries", capture == null ? 0 : capture.submitted);
            result.put("completedQueries", capture == null ? 0 : capture.completed);
            result.put("droppedSamples", capture == null ? 0 : capture.dropped);
            result.put("discardedSamples", capture == null ? 0 : capture.discarded);
            if (capture != null && !capture.failure.isEmpty()) result.put("lastFailure", capture.failure);
            List<Map<String, Object>> measured = new ArrayList<>();
            if (capture != null) for (var entry : capture.passes.entrySet()) {
                var values = entry.getValue();
                Map<String, Object> pass = new LinkedHashMap<>();
                pass.put("name", entry.getKey());
                if (values.samples.count > 0) pass.putAll(values.samples.report());
                else { pass.put("samples", 0L); pass.put("gpuNanosTotal", 0L); }
                pass.put("submittedQueries", values.submitted);
                pass.put("completedQueries", values.samples.count);
                pass.put("pendingQueries", values.submitted - values.samples.count - values.discarded);
                pass.put("droppedSamples", values.dropped);
                pass.put("discardedSamples", values.discarded);
                measured.add(pass);
            }
            result.put("passes", measured);
            return result;
        }

        Map<String, Object> report() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", initialized && supported);
            result.put("status", !failure.isEmpty() ? "FAILED" : !initialized ? "NOT_SAMPLED" : supported ? "ACTIVE" : "UNSUPPORTED");
            result.put("scope", "GPU timestamps: pack passes include original shaderpack; node_light_volume is atlas integration; node_light_native_composite includes reconstruction and full-resolution fallback; node_light_native_terrain includes original terrain rendering, not isolated lighting. Samples are per draw, not per frame.");
            result.put("pendingQueries", pending);
            result.put("maxPendingQueries", slots.length);
            result.put("droppedSamples", dropped);
            result.put("discardedSamples", discarded);
            result.put("failures", failures);
            if (!failure.isEmpty()) result.put("lastFailure", failure);
            List<Map<String, Object>> measured = new ArrayList<>();
            for (var entry : passes.entrySet()) if (entry.getValue().count > 0) {
                Map<String, Object> pass = new LinkedHashMap<>();
                pass.put("name", entry.getKey());
                pass.putAll(entry.getValue().report());
                measured.add(pass);
            }
            result.put("passes", measured);
            return result;
        }
    }

    private static final class Slot {
        int start, end;
        long epoch, sampledAt, captureGeneration;
        String pass;
    }

    private static final class Capture {
        final long generation;
        final Map<String, CapturePass> passes = new LinkedHashMap<>();
        long submitted, completed, dropped, discarded;
        int pending;
        boolean accepting = true;
        String status = "", failure = "";

        Capture(long generation) { this.generation = generation; }
        CapturePass pass(String name) { return passes.computeIfAbsent(name, ignored -> new CapturePass()); }
    }

    private static final class CapturePass {
        final Samples samples = new Samples();
        long submitted, dropped, discarded;
    }

    private static final class Samples {
        private final long[] window = new long[128];
        private final long[] sampledAt = new long[128];
        private int next, size;
        private long count, last, maximum, total, lastSampledAt;

        void add(long nanos, long timestamp) {
            window[next] = nanos;
            sampledAt[next] = timestamp;
            lastSampledAt = timestamp;
            next = (next + 1) % window.length;
            size = Math.min(size + 1, window.length);
            count++;
            last = nanos;
            maximum = Math.max(maximum, nanos);
            total += nanos;
        }

        Map<String, Object> report() {
            long[] sorted = Arrays.copyOf(window, size);
            Arrays.sort(sorted);
            return Map.of("samples", count, "windowSamples", size, "gpuNanosLast", last,
                    "gpuNanosP50", percentile(sorted, .50), "gpuNanosP95", percentile(sorted, .95),
                    "gpuNanosP99", percentile(sorted, .99), "gpuNanosMax", maximum, "gpuNanosTotal", total,
                    "sampleAgeMs", Math.max(0, System.nanoTime() - lastSampledAt) / 1_000_000.0,
                    "windowSpanMs", Math.max(0, lastSampledAt - sampledAt[size == window.length ? next : 0]) / 1_000_000.0);
        }

        private static long percentile(long[] sorted, double percentile) {
            return sorted[Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1)];
        }
    }
}
