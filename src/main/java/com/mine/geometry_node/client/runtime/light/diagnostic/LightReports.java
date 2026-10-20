package com.mine.geometry_node.client.runtime.light.diagnostic;

import com.mine.geometry_node.client.runtime.light.render.NodeLightFroxels;
import com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer;

import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.model.diagnostic.ModelReportContext;
import com.mine.geometry_node.client.model.diagnostic.ModelReportFiles;
import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Render-thread capture with asynchronous disk output and nonblocking GPU queries. */
public final class LightReports {
    private static final System.Logger LOGGER = System.getLogger(LightReports.class.getName());
    private static final int MAX_SAMPLES = 16384;
    private static final List<Long> intervals = new ArrayList<>(), preparation = new ArrayList<>();
    private static long start, previous, frames, requestedAt, durationNanos, drainStarted;
    private static long idleFrames;
    private static final LightReportSession SESSION = new LightReportSession(() -> {
        try {
            ModelShaderGpuTimings.endCapture();
            ModelShaderGpuTimings.releaseCapture();
        } finally {
            NodeLightPixelDiagnostics.release();
        }
    });
    private static boolean profiling;
    private static Map<String, Object> pendingSnapshot;
    private static Instant capturedAt;
    private static boolean skipDiagnosticInterval;
    private static int diagnosticFrames;
    private static final java.util.Set<String> pixelDraws=new java.util.HashSet<>();
    private static final java.util.Set<String> froxelProbeDraws=new java.util.HashSet<>();
    private static String label;
    private static Map<String, Object> startContext;
    private static final Map<String, SubmittedWork> submittedWork = new LinkedHashMap<>();
    private static final Map<String, FroxelWork> froxelWork = new LinkedHashMap<>();

    private static final class SubmittedWork {
        long draws, pixels, maxEmitterEvaluations;
        int width, height;
        boolean emitterBoundAvailable;
    }
    private static final class FroxelWork {
        long draws, froxels, emitterEvaluations;
        int width, height, slices, emitterSamples;
    }

    private LightReports() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("light")
                .then(LightReports.<S>captureCommand("report", false))
                .then(LightReports.<S>captureCommand("profile", true)));
    }

    private static <S> LiteralArgumentBuilder<S> captureCommand(String command, boolean probes) {
        return LiteralArgumentBuilder.<S>literal(command)
                .executes(c -> begin("", 1, probes))
                .then(LiteralArgumentBuilder.<S>literal("duration")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("seconds", IntegerArgumentType.integer(1, 30))
                                .executes(c -> begin("", IntegerArgumentType.getInteger(c, "seconds"), probes))
                                .then(RequiredArgumentBuilder.<S, String>argument("label", StringArgumentType.greedyString())
                                        .executes(c -> begin(StringArgumentType.getString(c, "label"),
                                                IntegerArgumentType.getInteger(c, "seconds"), probes)))))
                .then(RequiredArgumentBuilder.<S, String>argument("label", StringArgumentType.greedyString())
                        .executes(c -> begin(StringArgumentType.getString(c, "label"), 1, probes)));
    }

    public static boolean capturing() { return SESSION.capturing(); }

    /** Called at existing render-thread logout, shutdown and resource-reload boundaries. */
    public static void cancel() {
        RenderSystem.assertOnRenderThread();
        cancelCapture(SESSION);
    }

    /** Internal cancellation boundary; the public entry verifies render-thread ownership first. */
    static void cancelCapture(LightReportSession session) {
        try { session.cancel(); }
        catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to release light report diagnostics", failure);
        }
        finally { clearCapture(); }
    }

    private static void clearCapture() {
        start = previous = frames = requestedAt = durationNanos = drainStarted = idleFrames = 0;
        pendingSnapshot = null;
        capturedAt = null;
        startContext = null;
        label = null;
        profiling = skipDiagnosticInterval = false;
        diagnosticFrames = 0;
        intervals.clear(); preparation.clear();
        submittedWork.clear(); froxelWork.clear();
        pixelDraws.clear(); froxelProbeDraws.clear();
    }

    /** Cancellation must also progress while the workspace suppresses world rendering. */
    public static void tick() {
        RenderSystem.assertOnRenderThread();
        if (SESSION.worldChanged(Minecraft.getInstance().level)
                || (requestedAt != 0 && System.nanoTime() - requestedAt > 30_000_000_000L)) {
            cancel();
            message("cancelled");
        }
    }

    public static boolean probeFroxel(int program, int light) {
        if (!capturing() || !profiling || light != 0) return false;
        long phase = Math.min(1, (System.nanoTime() - start) / Math.max(1, durationNanos / 2));
        if (!froxelProbeDraws.add(phase + ":" + program + ":" + light)) return false;
        if (!skipDiagnosticInterval) diagnosticFrames++;
        skipDiagnosticInterval = true;
        return true;
    }

    public static boolean probePixels(int program, int pass, int light) {
        if (!capturing() || !profiling || pass != 0) return false;
        long phase = Math.min(1, (System.nanoTime() - start) / Math.max(1, durationNanos / 2));
        if (!pixelDraws.add(phase + ":" + program + ":" + pass + ":" + light)) return false;
        if (!skipDiagnosticInterval) diagnosticFrames++;
        skipDiagnosticInterval = true;
        return true;
    }

    public static void submittedFroxel(int program, String stage, int light, int width, int height,
                               int slices, int emitterSamples) {
        if (!capturing()) return;
        String key = stage + ":program=" + program + ":light=" + light;
        var work = froxelWork.computeIfAbsent(key, ignored -> new FroxelWork());
        long cells = (long) width * height * slices;
        work.draws++; work.froxels += cells; work.width = width; work.height = height;
        work.slices = slices; work.emitterSamples = emitterSamples;
        work.emitterEvaluations += cells * emitterSamples;
    }

    public static void submitted(int program, int pass, int lightIndex, int width, int height) {
        if (!capturing()) return;
        String key = (pass == 1 ? "surfaceAtlas" : "receiver")
                + ":program=" + program + ":light=" + lightIndex;
        var work = submittedWork.computeIfAbsent(key, ignored -> new SubmittedWork());
        long pixels = (long) width * height;
        work.draws++; work.pixels += pixels; work.width = width; work.height = height;
        if (pass == 1 && lightIndex >= 0 && lightIndex < NodeLightRenderer.lights().size()) {
            work.emitterBoundAvailable = true;
            var definition = NodeLightRenderer.lights().get(lightIndex).definition();
            var config = definition.config();
            int emitters = definition.emitterRadius() <= 0 ? 1 : config.quality().shadowSamples();
            work.maxEmitterEvaluations += pixels * emitters;
        }
    }

    private static Map<String, Object> submittedReport() {
        var entries = new LinkedHashMap<String, Object>();
        submittedWork.forEach((key, work) -> entries.put(key, Map.of(
                "draws", work.draws, "submittedViewportPixels", work.pixels,
                "viewportPixelsPerCapturedFrame", (double) work.pixels / frames,
                "lastViewportWidth", work.width, "lastViewportHeight", work.height,
                "emitterUpperBoundAvailable", work.emitterBoundAvailable,
                "configuredEmitterEvaluationUpperBound", work.emitterBoundAvailable ? work.maxEmitterEvaluations : "unmeasured")));
        var grids = new LinkedHashMap<String, Object>();
        froxelWork.forEach((key, work) -> grids.put(key, Map.of(
                "draws", work.draws, "submittedFroxels", work.froxels,
                "froxelsPerCapturedFrame", (double) work.froxels / frames,
                "lastGridWidth", work.width, "lastGridHeight", work.height, "lastDepthSlices", work.slices,
                "emitterSamplesPerFroxel", work.emitterSamples,
                "configuredEmitterEvaluationUpperBound", work.emitterEvaluations)));
        return Map.of("passes", entries, "froxelPasses", grids,
                "scope", "CPU-observed successful submissions during capture. Froxel dimensions describe actual submitted grids, not visible scattering or traced rays. Injection emitter counts are upper bounds; integration and history have zero emitter samples and reuse cached data. Viewport pixels describe surface and receiver raster bounds. Program IDs and light indices are transient.");
    }

    private static int begin(String name, int seconds, boolean probes) {
        RenderSystem.assertOnRenderThread();
        if (SESSION.busy()) { message("busy"); return 0; }
        if (Minecraft.getInstance().level == null) { message("world_required"); return 0; }
        SESSION.queue(Minecraft.getInstance().level);
        label = name;
        profiling = probes;
        durationNanos = seconds * 1_000_000_000L;
        intervals.clear(); preparation.clear(); frames = previous = idleFrames = 0;
        submittedWork.clear();
        froxelWork.clear();
        diagnosticFrames=0;skipDiagnosticInterval=false;
        pixelDraws.clear();
        froxelProbeDraws.clear();
        requestedAt = System.nanoTime();
        message(probes ? "profile_queued" : "queued", seconds);
        return 1;
    }

    public static void frame(long prepareNanos) {
        RenderSystem.assertOnRenderThread();
        try { captureFrame(prepareNanos); }
        catch (RuntimeException error) {
            cancel();
            message("failed", error.toString());
        }
    }

    private static void captureFrame(long prepareNanos) {
        tick();
        long now = System.nanoTime();
        if (pendingSnapshot != null) { finishPending(now); return; }
        if (requestedAt != 0) {
            if (Minecraft.getInstance().screen != null) return;
            requestedAt = 0;
            SESSION.start();
            startContext = ModelReportContext.capture(Instant.now());
            ModelShaderGpuTimings.beginCapture();
            NodeLightPixelDiagnostics.beginCapture();
            start = now;
        }
        if (!capturing()) return;
        NodeLightPixelDiagnostics.poll();
        if (previous != 0 && !skipDiagnosticInterval && intervals.size() < MAX_SAMPLES) intervals.add(now - previous);
        skipDiagnosticInterval=false;
        if (now - start < durationNanos) {
            frames++;
            if (!NodeLightRenderer.active()) idleFrames++;
            if (preparation.size() < MAX_SAMPLES) preparation.add(prepareNanos);
            previous = now;
            return;
        }
        long elapsed = now - start;
        start = 0;
        SESSION.drain();
        ModelShaderGpuTimings.endCapture();
        NodeLightPixelDiagnostics.endCapture();
        try {
            capturedAt = Instant.now();
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("schemaVersion", 8);
            snapshot.put("mode", profiling ? "occlusion_profile" : "performance_report");
            snapshot.put("implementation", Implementation.FINGERPRINT);
            snapshot.put("label", label);
            snapshot.put("start", startContext);
            snapshot.put("end", ModelReportContext.capture(capturedAt));
            snapshot.put("windowSeconds", elapsed / 1e9);
            snapshot.put("requestedSeconds", durationNanos / 1e9);
            snapshot.put("renderedFrames", frames);
            var window=Minecraft.getInstance().getWindow();
            snapshot.put("lightActivity",Map.of("framesWithoutLights",idleFrames,
                    "framesWithLights",frames-idleFrames,
                    "viewportPixels",(long)window.getWidth()*window.getHeight(),
                    "scope","Frame counts cover this capture. Draw/build/reuse counters inside resource reports are lifetime totals. No-light captures skip node-light draws, but named shaderpack pass timings still include host shading and compiled receiver code; they are not isolated node-light costs."));
            snapshot.put("frameIntervals", distribution(intervals));
            snapshot.put("lightPreparationCpu", distribution(preparation));
            snapshot.put("lights", NodeLightRenderer.report());
            snapshot.put("submittedWork", submittedReport());
            snapshot.put("froxelVolume", NodeLightFroxels.report());
            snapshot.put("measurementCapabilities", Map.of(
                    "gpuPassTimingInstrumented", true,
                    "gpuPassTimeAvailability", "See gpu.available/status and pending/dropped counters; unsupported queries provide no timings.",
                    "submittedViewportPixelsMeasured", true,
                    "visibleBeamPixelsMeasured", false,
                    "fallbackPixelCountingInstrumented", true,
                    "pixelCountAvailability", "See volumePixelDiagnostics; profile counts volume requests and surface-atlas reconstruction attempts/fallbacks. Counts combine all lights per pixel. Legacy march fallback probes are not applicable to froxel lookup.",
                    "rayCountsMeasured", false,
                    "perLightGpuTimeMeasured", false,
                    "shaderpackAddedLightCostIsolated", false));
            var shared = new LinkedHashMap<>(ModelIrisHeldLight.report());
            shared.remove("nodeLights");
            snapshot.put("sharedOcclusion", shared);
            snapshot.put("samplingQuality", Map.of("frameIntervalSamples", intervals.size(),
                    "shortWindow", intervals.size() < 30,
                    "diagnosticWorkIncluded", profiling,
                    "note", profiling ? "Diagnostic replay changes GPU load; frame/pass times are not a performance baseline."
                            : "Short captures locate major costs; use longer captures to compare small changes or rare stalls."));
            pendingSnapshot = snapshot;
            drainStarted = now;
            finishPending(now);
        } catch (RuntimeException error) {
            cancel(); message("failed", error.toString());
        }
    }

    private static void finishPending(long now) {
        try {
            ModelShaderGpuTimings.poll();
            NodeLightPixelDiagnostics.poll();
            var gpu = ModelShaderGpuTimings.captureReport();
            int pending = gpu.get("pendingQueries") instanceof Number n ? n.intValue() : 0;
            int pixelPending = NodeLightPixelDiagnostics.pending();
            if ((pending > 0 || pixelPending > 0) && now - drainStarted < 2_000_000_000L) return;
            var snapshot = pendingSnapshot;
            snapshot.put("gpuCollection", Map.of("complete", pending == 0 && pixelPending == 0,
                    "drainSeconds", (now - drainStarted) / 1e9, "pendingAtSave", pending,
                    "pixelQueriesPendingAtSave", pixelPending));
            snapshot.put("gpu", gpu);
            snapshot.put("volumePixelDiagnostics", NodeLightPixelDiagnostics.report());
            var cost = new LinkedHashMap<String, Object>();
            var probes=new LinkedHashMap<String,Object>();
            var receiverProbes=new LinkedHashMap<String,Object>();
            if (gpu.get("passes") instanceof List<?> passes) for (Object item : passes) {
                if (item instanceof Map<?, ?> pass && pass.get("gpuNanosTotal") instanceof Number total) {
                    String name=String.valueOf(pass.get("name"));
                    if(name.startsWith("node_light_probe_")) {
                        long samples=pass.get("samples") instanceof Number n?n.longValue():0;
                        (name.startsWith("node_light_probe_receiver_") ? receiverProbes : probes)
                                .put(name,Map.of("samples",samples,"meanMsPerDraw",samples>0?total.doubleValue()/1e6/samples:0));
                    } else cost.put(name,total.doubleValue()/1e6/frames);
                }
            }
            snapshot.put("volumeOcclusionProbes",Map.of("status", !profiling ? "DISABLED_PERFORMANCE_REPORT"
                            : probes.isEmpty() ? "NOT_SAMPLED" : "SAMPLED",
                    "passes",probes,"excludedDiagnosticFrameIntervals",diagnosticFrames,
                    "scope", "Profile-only independent froxel-injection comparisons for the first light in each half of the capture. Each world/entity/model/all-occlusion removal has an adjacent normal reference. Empty entity/model cases may be skipped. Only injection changes; receiver and depth-integration work are not measured by these probes. Empty results do not imply zero cost. At most two samples per variant; paired differences remain noisy and non-additive. Profile workload is not a frame-rate baseline. Legacy pack-program volume replays are disabled."));
            snapshot.put("gpuMeanMsPerCapturedFrame", cost);
            snapshot.put("receiverCostProbes", Map.of("passes", receiverProbes,
                    "scope", "Profile-only isolated-output comparisons. Each disabled draw follows its own normal reference. volume removes froxel lookup and composition; source removes emitter evaluation in this receiver only (not separate native source geometry); surface removes receiver direct lighting/reconstruction; surface_fallback removes direct reevaluation only when atlas reconstruction rejects. Surface-atlas prepasses, froxel injection/integration/history and original shaderpack work remain. Legacy integration mode 16 is not applicable. Interpret paired differences, not sums or exact percentages; inspect samples and GPU collection status."));
            double volumeMs=0;
            for(String key:List.of("node_light_froxel_inject","node_light_froxel_integrate","node_light_froxel_history"))
                if(cost.get(key) instanceof Number value)volumeMs+=value.doubleValue();
            var ranked=new ArrayList<Map<String,Object>>();
            cost.forEach((key,value)->{if(value instanceof Number n)ranked.add(Map.of("pass",key,"meanMs",n.doubleValue()));});
            ranked.sort(java.util.Comparator.comparingDouble((Map<String,Object> row)->((Number)row.get("meanMs")).doubleValue()).reversed());
            snapshot.put("bottleneckSummary",Map.of("rankedGpuPasses",ranked,"isolatedVolumePassesMeanMs",volumeMs,
                    "scope","Froxel injection + depth integration + history. Receiver volume lookup/composition remains inside composite and is not included in isolatedVolumePassesMeanMs. Compare this sum alongside composite, not composite alone."));
            snapshot.put("gpuAttribution", Map.of(
                    "node_light_froxel_inject", "Per-froxel scattering/lighting evaluation including world, entity and model occlusion.",
                    "node_light_froxel_integrate", "Depth integration of cached froxel scattering; no repeated emitter occlusion queries.",
                    "node_light_froxel_history", "Reserved for a separately submitted history stage; its absence does not imply temporal reprojection. Unchanged-grid reuse is described by froxelVolume.",
                    "node_light_surface", "Isolated reduced-resolution surface-atlas draw.",
                    "node_light_native_snapshot", "Native color and depth framebuffer copy.",
                    "node_light_native_composite", "Native full-resolution froxel interval lookup, visible emitters and color composition.",
                    "node_light_native_terrain", "Native terrain including host shading; sampled only during report capture.",
                    "shaderpackPasses", "Named deferred/composite passes include original shaderpack work and injected lighting; cannot isolate added light cost.",
                    "unmeasured", "Actual occlusion rays, traversal visits, accepted history and per-light GPU cost are not measured. Receiver pixel queries do not measure froxel coverage. CPU submission counts are upper bounds."));
            snapshot.put("scope", "Capture begins after closing the UI, at a light-preparation boundary. renderedFrames counts frames whose draws belong to the capture, excluding the closing boundary. Preparation CPU samples belong to those frames. GPU draws are attributed at query submission; results drain asynchronously after the window, with a two-second timeout. Inspect gpuCollection, dropped/discarded samples and per-pass query counts. Frame intervals include all game work and frame limits. Composite includes froxel lookup and visible emitters. No synchronous GPU wait.");
            var directory = Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_nodes/reports/light");
            Instant reportTime = capturedAt;
            long generation = SESSION.save();
            clearCapture();
            CompletableFuture.runAsync(() -> {
                try {
                    var path = ModelReportFiles.write(directory, reportTime, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
                    Minecraft.getInstance().execute(() -> completeSave(generation, "saved", path.toString()));
                } catch (Exception error) {
                    Minecraft.getInstance().execute(() -> completeSave(generation, "failed", error.toString()));
                }
            });
        } catch (RuntimeException error) {
            cancel(); message("failed", error.toString());
        }
    }

    private static void completeSave(long generation, String key, String detail) {
        RenderSystem.assertOnRenderThread();
        if (SESSION.complete(generation)) message(key, detail);
    }

    private static final class Implementation {
        static final String FINGERPRINT = fingerprint();
        private static String fingerprint() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                for (String resource : List.of("/assets/geometry_node/shaders/include/node_lights.glsl",
                        "/assets/geometry_node/shaders/include/node_light_froxels.glsl",
                        "/assets/geometry_node/shaders/include/node_light_material.glsl",
                        "/com/mine/geometry_node/client/runtime/light/diagnostic/LightReports.class",
                        "/com/mine/geometry_node/client/runtime/light/diagnostic/LightReportSession.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/NodeLightShader.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightRenderer.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightCookies.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/NodeLightPrograms.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/NodeLightProgramCache.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NativeVolumeLights.class",
                        "/com/mine/geometry_node/client/model/render/bvh/ModelPlacedLightField.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightEntityShadows.class",
                        "/com/mine/geometry_node/client/runtime/light/store/ClientLightStore.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightTargets.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightFroxels.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightFroxels$Bindings.class",
                        "/com/mine/geometry_node/client/runtime/light/render/NodeLightFroxelQuality.class",
                        "/com/mine/geometry_node/client/runtime/light/diagnostic/NodeLightPixelDiagnostics.class",
                        "/com/mine/geometry_node/client/runtime/light/diagnostic/NodeLightReceiverCostDiagnostics.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/adapter/BlissLightShaderAdapter.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/adapter/SeusLightShaderAdapter.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/adapter/KappaLightShaderAdapter.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/adapter/PhotonLightShaderAdapter.class",
                        "/com/mine/geometry_node/client/runtime/light/shader/adapter/SundialLightShaderAdapter.class")) {
                    try (var stream = LightReports.class.getResourceAsStream(resource)) {
                        if (stream == null) return "unavailable: " + resource;
                        digest.update(stream.readAllBytes());
                    }
                }
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (Exception error) { return "unavailable: " + error.getClass().getSimpleName(); }
        }
    }

    private static Map<String, Object> distribution(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        if (sorted.length == 0) return Map.of("samples", 0);
        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        return Map.of("samples", sorted.length, "meanMs", mean,
                "p50Ms", sorted[(sorted.length - 1) / 2] / 1e6,
                "p95Ms", sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                "maxMs", sorted[sorted.length - 1] / 1e6);
    }

    private static void message(String key, Object... arguments) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.translatable("geometry_node.light.report." + key, arguments));
    }
}
