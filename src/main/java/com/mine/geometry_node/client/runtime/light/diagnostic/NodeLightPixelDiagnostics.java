package com.mine.geometry_node.client.runtime.light.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

/** Report-only fullscreen replays with discard predicates and nonblocking sample queries. */
public final class NodeLightPixelDiagnostics {
    private static final int MAX_PENDING = 128;
    private static final List<Pending> pending = new ArrayList<>();
    private static final Map<String, Counts> groups = new LinkedHashMap<>();
    private static boolean capturing, drawing;
    private static long failures;
    private static String failure = "";

    private NodeLightPixelDiagnostics() {}

    static void beginCapture() {
        release();
        groups.clear();
        failures = 0;
        failure = "";
        NodeLightReceiverCostDiagnostics.reset();
        capturing = true;
    }

    static void reset() { beginCapture(); }
    static void endCapture() { capturing = false; }
    public static boolean isDrawing() { return drawing; }
    static int pending() { return pending.size(); }

    public static void draw(int program, int pass, int light, int width, int height, Runnable draw) {
        if (!capturing || drawing) return;
        boolean surface = GL20C.glGetUniformLocation(program, "nodeSurfaceRects[0]") >= 0;
        boolean volume = GL20C.glGetUniformLocation(program, "nodeFroxelReady") >= 0;
        int[] modes = pass != 0 ? new int[0] : surface
                ? (volume ? new int[]{1, 2, 4} : new int[]{2, 4}) : new int[]{1};
        try {
            int location = GL20C.glGetUniformLocation(program, "nodeVolumeProbeMode");
            String unavailable = location < 0 ? "shader_uninstrumented" : queryUnavailable();
            if (!unavailable.isEmpty()) {
                for (int mode : modes) group(program, pass, light, width, height, mode).skip(unavailable);
                return;
            }
            int previousMode = GL20C.glGetUniformi(program, location);
            int buffers = GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
            boolean depth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
            boolean stencil = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
            boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
            byte[][] masks = new byte[buffers][4];
            try (var stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                for (int i = 0; i < buffers; i++) {
                    mask.clear();
                    GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK, i, mask);
                    mask.get(masks[i]);
                }
            }
            drawing = true;
            try {
                for (int i = 0; i < buffers; i++) GL30C.glColorMaski(i, false, false, false, false);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDepthMask(false);
                for (int mode : modes) {
                    Counts counts = group(program, pass, light, width, height, mode);
                    if (pending.size() >= MAX_PENDING) { counts.skip("query_capacity"); continue; }
                    GL20C.glUniform1i(location, mode);
                    replay(counts, draw);
                }
                if (pass == 0) NodeLightReceiverCostDiagnostics.draw(location, width, height, draw);
            } finally {
                GL20C.glUniform1i(location, previousMode);
                for (int i = 0; i < buffers; i++)
                    GL30C.glColorMaski(i, masks[i][0] != 0, masks[i][1] != 0, masks[i][2] != 0, masks[i][3] != 0);
                if (depth) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                if (stencil) GL11C.glEnable(GL11C.GL_STENCIL_TEST); else GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDepthMask(depthMask);
                drawing = false;
            }
        } catch (RuntimeException error) {
            drawing = false;
            failed(error);
        }
    }

    private static String queryUnavailable() {
        if (GL11C.glGetInteger(GL13C.GL_SAMPLES) > 1) return "multisampled_framebuffer";
        if (GL15C.glGetQueryi(GL15C.GL_SAMPLES_PASSED, GL15C.GL_CURRENT_QUERY) != 0
                || GL15C.glGetQueryi(GL33C.GL_ANY_SAMPLES_PASSED, GL15C.GL_CURRENT_QUERY) != 0)
            return "occlusion_query_active";
        var capabilities = GL.getCapabilities();
        if ((capabilities.OpenGL43 || capabilities.GL_ARB_ES3_compatibility)
                && GL15C.glGetQueryi(GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE, GL15C.GL_CURRENT_QUERY) != 0)
            return "occlusion_query_active";
        if (GL15C.glGetQueryi(GL15C.GL_SAMPLES_PASSED, GL15C.GL_QUERY_COUNTER_BITS) <= 0)
            return "sample_queries_unsupported";
        return "";
    }

    private static void replay(Counts counts, Runnable draw) {
        int query = GL15C.glGenQueries();
        if (query == 0) { counts.skip("query_allocation_failed"); return; }
        boolean open = false, queued = false;
        try {
            GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED, query);
            open = true;
            draw.run();
            GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);
            open = false;
            pending.add(new Pending(query, counts));
            counts.submitted++;
            queued = true;
        } catch (RuntimeException error) {
            counts.skip("replay_failed");
            throw error;
        } finally {
            if (open) GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);
            if (!queued) GL15C.glDeleteQueries(query);
        }
    }

    static void poll() {
        for (int i = 0; i < pending.size();) {
            Pending item = pending.get(i);
            try {
                if (GL15C.glGetQueryObjecti(item.query, GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) { i++; continue; }
                long pixels = GL33C.glGetQueryObjecti64(item.query, GL15C.GL_QUERY_RESULT);
                item.counts.completed++;
                item.counts.pixels += pixels;
                item.counts.maximum = Math.max(item.counts.maximum, pixels);
            } catch (RuntimeException error) {
                item.counts.discarded++;
                failed(error);
            }
            try { GL15C.glDeleteQueries(item.query); } catch (RuntimeException error) { failed(error); }
            pending.remove(i);
        }
    }

    static void release() {
        capturing = false;
        for (Pending item : pending) {
            item.counts.discarded++;
            try { GL15C.glDeleteQueries(item.query); } catch (RuntimeException error) { failed(error); }
        }
        pending.clear();
        NodeLightReceiverCostDiagnostics.release();
    }

    private static Counts group(int program, int pass, int light, int width, int height, int mode) {
        String key = "program=" + program + ":pass=" + pass + ":light=" + light + ":mode=" + mode
                + ":viewport=" + width + "x" + height;
        return groups.computeIfAbsent(key, ignored -> new Counts(program, pass, light, width, height, mode));
    }

    private static void failed(RuntimeException error) {
        failures++;
        failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    static Map<String, Object> report() {
        var result = new LinkedHashMap<String, Object>();
        long completed = groups.values().stream().mapToLong(counts -> counts.completed).sum();
        result.put("available", completed > 0);
        result.put("status", capturing ? "CAPTURING" : !pending.isEmpty() ? "DRAINING"
                : completed > 0 ? "COMPLETE" : failures > 0 ? "FAILED" : groups.isEmpty() ? "NOT_SAMPLED" : "SKIPPED");
        result.put("submittedQueries", groups.values().stream().mapToLong(counts -> counts.submitted).sum());
        result.put("completedQueries", completed);
        result.put("discardedQueries", groups.values().stream().mapToLong(counts -> counts.discarded).sum());
        result.put("pendingQueries", pending.size());
        result.put("maxPendingQueries", MAX_PENDING);
        result.put("failures", failures);
        result.put("receiverCostDiagnostics", NodeLightReceiverCostDiagnostics.report());
        if (!failure.isEmpty()) result.put("lastFailure", failure);
        var rows = new ArrayList<Map<String, Object>>();
        for (Counts counts : groups.values()) {
            var row = new LinkedHashMap<String, Object>();
            row.put("program", counts.program); row.put("pass", counts.pass); row.put("light", counts.light);
            row.put("mode", switch (counts.mode) {
                case 2 -> "surfaceReconstructionAttemptPixels";
                case 4 -> "surfaceReconstructionFallbackPixels";
                default -> "volumeRequestedPixels";
            });
            row.put("viewportWidth", counts.width); row.put("viewportHeight", counts.height);
            row.put("submittedQueries", counts.submitted); row.put("completedQueries", counts.completed);
            row.put("discardedQueries", counts.discarded);
            row.put("pendingQueries", counts.submitted - counts.completed - counts.discarded);
            row.put("skippedQueries", Map.copyOf(counts.skipped));
            row.put("pixelsTotal", counts.pixels); row.put("pixelsMaxPerDraw", counts.maximum);
            row.put("pixelsMeanPerDraw", counts.completed == 0 ? 0.0 : (double) counts.pixels / counts.completed);
            row.put("viewportFractionMean", counts.completed == 0 ? 0.0
                    : (double) counts.pixels / counts.completed / ((long) counts.width * counts.height));
            rows.add(row);
        }
        result.put("groups", rows);
        result.put("scope", "Profile-only single-sample receiver replays with all color/depth/stencil writes disabled. Counts are unique pixels requesting volume evaluation, attempting surface-atlas reconstruction, or requiring direct surface fallback after reconstruction rejection. Predicates combine all lights: fallback/attempt is a pixel fraction, not a per-light rejection rate. Counts are not froxels, emitter evaluations, ray queries or proof of nonzero lighting. Query results drain asynchronously. Compare groups only for matching program/pass/light and viewport; different diagnostic draws may see different temporal jitter.");
        return result;
    }

    private record Pending(int query, Counts counts) {}

    private static final class Counts {
        final int program, pass, light, width, height, mode;
        final Map<String, Long> skipped = new LinkedHashMap<>();
        long submitted, completed, discarded, pixels, maximum;
        Counts(int program, int pass, int light, int width, int height, int mode) {
            this.program = program; this.pass = pass; this.light = light;
            this.width = width; this.height = height; this.mode = mode;
        }
        void skip(String reason) { skipped.merge(reason, 1L, Long::sum); }
    }

}
