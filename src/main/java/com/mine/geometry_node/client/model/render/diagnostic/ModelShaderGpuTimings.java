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

        Tracker(Driver driver, int capacity) {
            if (capacity < 1 || capacity > 256) throw new IllegalArgumentException("invalid query capacity");
            this.driver = driver;
            slots = new Slot[capacity];
        }

        int begin(String pass) {
            if (pass == null || pass.isBlank()) return -1;
            if (!initialize()) return -1;
            if (active >= 0 || pending == slots.length || !passes.containsKey(pass) && passes.size() >= MAX_PASSES) {
                dropped++;
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
                slot.sampledAt = System.nanoTime();
                passes.computeIfAbsent(pass, ignored -> new Samples());
                driver.timestamp(slot.start);
                active = write;
                return active;
            } catch (RuntimeException error) {
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
                    if (slot.epoch == epoch) {
                        long elapsed = driver.result(slot.end) - driver.result(slot.start);
                        if (elapsed >= 0) passes.computeIfAbsent(slot.pass, ignored -> new Samples()).add(elapsed, slot.sampledAt);
                        else discarded++;
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
            releaseQueries();
            clearSamples();
            initialized = supported = false;
            failure = "";
            failures = 0;
        }

        private void disable(RuntimeException error) {
            failures++;
            failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
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

        Map<String, Object> report() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", initialized && supported);
            result.put("status", !failure.isEmpty() ? "FAILED" : !initialized ? "NOT_SAMPLED" : supported ? "ACTIVE" : "UNSUPPORTED");
            result.put("scope", "Iris fullscreen draw GPU timestamps; includes original shaderpack and injected lighting, excludes pass setup/mipmaps/compute; not BVH-only time");
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
        long epoch, sampledAt;
        String pass;
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
