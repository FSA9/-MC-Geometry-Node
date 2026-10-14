package com.mine.geometry_node.client.runtime.light;

import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.model.diagnostic.ModelReportContext;
import com.mine.geometry_node.client.model.diagnostic.ModelReportFiles;
import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
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
    private static final int MAX_SAMPLES = 16384;
    private static final List<Long> intervals = new ArrayList<>(), preparation = new ArrayList<>();
    private static long start, previous, frames, requestedAt, durationNanos, drainStarted;
    private static boolean saving;
    private static boolean profiling;
    private static Object captureWorld;
    private static Map<String, Object> pendingSnapshot;
    private static Instant capturedAt;
    private static boolean skipDiagnosticInterval;
    private static int diagnosticFrames;
    private static final java.util.Set<String> diagnosticDraws=new java.util.HashSet<>();
    private static final java.util.Set<String> pixelDraws=new java.util.HashSet<>();
    private static String label;
    private static Map<String, Object> startContext;
    private static final Map<String, SubmittedWork> submittedWork = new LinkedHashMap<>();

    private static final class SubmittedWork {
        long draws, pixels, maxEmitterEvaluations;
        int width, height;
        boolean emitterBoundAvailable;
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

    public static boolean capturing() { return start != 0; }

    static boolean probeVolume(int program,int pass,int light) {
        if(!capturing() || !profiling || light!=0 || (pass!=2 && pass!=3))return false;
        long phase = Math.min(1, (System.nanoTime() - start) / Math.max(1, durationNanos / 2));
        if(!diagnosticDraws.add(phase+":"+program+":"+pass))return false;
        if(!skipDiagnosticInterval)diagnosticFrames++;
        skipDiagnosticInterval=true;
        return true;
    }

    static boolean probePixels(int program, int pass, int light) {
        if (!capturing() || !profiling || (pass != 0 && pass != 2 && pass != 3)) return false;
        long phase = Math.min(1, (System.nanoTime() - start) / Math.max(1, durationNanos / 2));
        if (!pixelDraws.add(phase + ":" + program + ":" + pass + ":" + light)) return false;
        if (!skipDiagnosticInterval) diagnosticFrames++;
        skipDiagnosticInterval = true;
        return true;
    }

    static void submitted(int program, int pass, int lightIndex, int width, int height) {
        if (!capturing()) return;
        String key = (pass == 1 ? "surfaceAtlas" : pass == 2 ? "volumeAtlas"
                : pass == 3 ? "volumeSegmentAtlas" : pass == 4 ? "volumeSegmentDepth" : "receiver")
                + ":program=" + program + ":light=" + lightIndex;
        var work = submittedWork.computeIfAbsent(key, ignored -> new SubmittedWork());
        long pixels = (long) width * height;
        work.draws++; work.pixels += pixels; work.width = width; work.height = height;
        if (lightIndex >= 0 && lightIndex < NodeLightRenderer.lights().size()) {
            work.emitterBoundAvailable = true;
            var definition = NodeLightRenderer.lights().get(lightIndex).definition();
            var config = definition.config();
            boolean volumePass = pass == 2 || pass == 3;
            int emitters = definition.emitterRadius() <= 0 ? 1 :
                    (volumePass ? config.quality().volumeEmitterSamples() : config.quality().shadowSamples());
            boolean enabled = pass != 4 && (!volumePass || !config.volume().mode().equals("off")
                    && config.volume().density() > 0 && config.volume().strength() > 0);
            if (enabled) work.maxEmitterEvaluations += pixels * emitters * (volumePass ? Math.clamp(config.quality().volumeSamples(), 4, 64) : 1);
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
        return Map.of("passes", entries, "scope", "CPU-observed successful fullscreen submissions during capture; viewport pixels are raster workload bounds, not visible beam pixels or actual integrations. Emitter bounds exclude receiver fallback. Program IDs and light indices are transient. No GPU pixel/readback counters.");
    }

    private static int begin(String name, int seconds, boolean probes) {
        if (capturing() || requestedAt != 0 || pendingSnapshot != null || saving) { message("busy"); return 0; }
        if (Minecraft.getInstance().level == null) { message("world_required"); return 0; }
        label = name;
        profiling = probes;
        durationNanos = seconds * 1_000_000_000L;
        captureWorld = Minecraft.getInstance().level;
        intervals.clear(); preparation.clear(); frames = previous = 0;
        submittedWork.clear();
        diagnosticDraws.clear();diagnosticFrames=0;skipDiagnosticInterval=false;
        pixelDraws.clear();
        requestedAt = System.nanoTime();
        message(probes ? "profile_queued" : "queued", seconds);
        return 1;
    }

    static void frame(long prepareNanos) {
        long now = System.nanoTime();
        if (pendingSnapshot != null) { finishPending(now); return; }
        if (requestedAt != 0) {
            if (Minecraft.getInstance().level != captureWorld || now - requestedAt > 30_000_000_000L) {
                requestedAt = 0; message("cancelled"); return;
            }
            if (Minecraft.getInstance().screen != null) return;
            requestedAt = 0;
            startContext = ModelReportContext.capture(Instant.now());
            ModelShaderGpuTimings.beginCapture();
            NodeLightPixelDiagnostics.beginCapture();
            start = now;
        }
        if (!capturing()) return;
        if (Minecraft.getInstance().level != captureWorld) {
            start = 0; ModelShaderGpuTimings.endCapture(); ModelShaderGpuTimings.releaseCapture();
            NodeLightPixelDiagnostics.release();
            message("cancelled"); return;
        }
        NodeLightPixelDiagnostics.poll();
        if (previous != 0 && !skipDiagnosticInterval && intervals.size() < MAX_SAMPLES) intervals.add(now - previous);
        skipDiagnosticInterval=false;
        if (now - start < durationNanos) {
            frames++;
            if (preparation.size() < MAX_SAMPLES) preparation.add(prepareNanos);
            previous = now;
            return;
        }
        long elapsed = now - start;
        start = 0;
        ModelShaderGpuTimings.endCapture();
        NodeLightPixelDiagnostics.endCapture();
        try {
            capturedAt = Instant.now();
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("schemaVersion", 6);
            snapshot.put("mode", profiling ? "occlusion_profile" : "performance_report");
            snapshot.put("implementation", Implementation.FINGERPRINT);
            snapshot.put("label", label);
            snapshot.put("start", startContext);
            snapshot.put("end", ModelReportContext.capture(capturedAt));
            snapshot.put("windowSeconds", elapsed / 1e9);
            snapshot.put("requestedSeconds", durationNanos / 1e9);
            snapshot.put("renderedFrames", frames);
            snapshot.put("frameIntervals", distribution(intervals));
            snapshot.put("lightPreparationCpu", distribution(preparation));
            snapshot.put("lights", NodeLightRenderer.report());
            snapshot.put("submittedWork", submittedReport());
            snapshot.put("measurementCapabilities", Map.of(
                    "gpuPassTimingInstrumented", true,
                    "gpuPassTimeAvailability", "See gpu.available/status and pending/dropped counters; unsupported queries provide no timings.",
                    "submittedViewportPixelsMeasured", true,
                    "visibleBeamPixelsMeasured", false,
                    "fallbackPixelCountingInstrumented", true,
                    "pixelCountAvailability", "See volumePixelDiagnostics; only /light profile replays eligible single-sample receivers.",
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
            ModelShaderGpuTimings.releaseCapture(); pendingSnapshot = null;
            NodeLightPixelDiagnostics.release();
            saving = false; message("failed", error.toString());
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
            snapshot.put("volumeOcclusionProbes",Map.of("passes",probes,"excludedDiagnosticFrameIntervals",diagnosticFrames,
                    "scope",profiling ? "Profile-only comparisons in each half of the capture, first active light, each eligible receiver and front/back pass. Each removal has an adjacent _reference draw after warmup. Empty entity/model variants are skipped. no_world retains tile coverage and camera-path absorption. Uniforms and atlas are restored. At most two samples per receiver/variant; paired deltas remain noisy and non-additive. All ordinary timings in this profile may be affected by diagnostic work and must not be used as a baseline. Native and supported shaderpack atlas paths; units are per draw."
                            : "Disabled in performance reports. Use /light profile for separate occlusion comparisons."));
            snapshot.put("gpuMeanMsPerCapturedFrame", cost);
            snapshot.put("receiverCostProbes", Map.of("passes", receiverProbes,
                    "scope", "Profile-only isolated-output comparisons. Each disabled draw follows its own normal reference. integration isolates full-resolution volume marches, volume removes reconstruction and marches, source removes emitter evaluation in this receiver only (not separate native source geometry). Original shaderpack work remains. Interpret paired differences, not sums or exact percentages; inspect samples and GPU collection status."));
            double volumeMs=0;
            for(String key:List.of("node_light_volume","node_light_volume_segment","node_light_volume_segment_depth"))
                if(cost.get(key) instanceof Number value)volumeMs+=value.doubleValue();
            var ranked=new ArrayList<Map<String,Object>>();
            cost.forEach((key,value)->{if(value instanceof Number n)ranked.add(Map.of("pass",key,"meanMs",n.doubleValue()));});
            ranked.sort(java.util.Comparator.comparingDouble((Map<String,Object> row)->((Number)row.get("meanMs")).doubleValue()).reversed());
            snapshot.put("bottleneckSummary",Map.of("rankedGpuPasses",ranked,"isolatedVolumePassesMeanMs",volumeMs,
                    "scope","Foreground + transparent background + metadata. Receiver fallback remains inside composite, not included in isolatedVolumePassesMeanMs. Compare this sum, not composite alone."));
            snapshot.put("gpuAttribution", Map.of(
                    "node_light_volume", "Isolated volume-atlas draw; includes depth reconstruction and ray integration.",
                    "node_light_volume_segment", "Reduced-resolution volume behind transparent surfaces; separate from foreground integration.",
                    "node_light_volume_segment_depth", "Transparent interval depth metadata only; no lighting integration.",
                    "node_light_surface", "Isolated reduced-resolution surface-atlas draw.",
                    "node_light_native_snapshot", "Native color and depth framebuffer copy.",
                    "node_light_native_composite", "Native full-resolution reconstruction, fallback integration, and color composition.",
                    "node_light_native_terrain", "Native terrain including host shading; sampled only during report capture.",
                    "shaderpackPasses", "Named deferred/composite passes include original shaderpack work and injected lighting; cannot isolate added light cost.",
                    "unmeasured", "Actual march steps, traced rays, and per-light GPU cost are not measured. Profile pixel queries count predicates at reconstruction and geometric clipping, not actual loop iterations. CPU submission counts are upper bounds."));
            snapshot.put("scope", "Capture begins after closing the UI, at a light-preparation boundary. renderedFrames counts frames whose draws belong to the capture, excluding the closing boundary. Preparation CPU samples belong to those frames. GPU draws are attributed at query submission; results drain asynchronously after the window, with a two-second timeout. Inspect gpuCollection, dropped/discarded samples and per-pass query counts. Frame intervals include all game work and frame limits. Composite includes fallback. No synchronous GPU wait.");
            var directory = Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_nodes/reports/light");
            Instant reportTime = capturedAt;
            ModelShaderGpuTimings.releaseCapture();
            NodeLightPixelDiagnostics.release();
            pendingSnapshot = null;
            saving = true;
            CompletableFuture.runAsync(() -> {
                try {
                    var path = ModelReportFiles.write(directory, reportTime, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
                    Minecraft.getInstance().execute(() -> { saving = false; message("saved", path.toString()); });
                } catch (Exception error) {
                    Minecraft.getInstance().execute(() -> { saving = false; message("failed", error.toString()); });
                }
            });
        } catch (RuntimeException error) {
            ModelShaderGpuTimings.releaseCapture(); pendingSnapshot = null;
            NodeLightPixelDiagnostics.release();
            saving = false; message("failed", error.toString());
        }
    }

    private static final class Implementation {
        static final String FINGERPRINT = fingerprint();
        private static String fingerprint() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                for (String resource : List.of("/assets/geometry_node/shaders/include/node_lights.glsl",
                        "/com/mine/geometry_node/client/runtime/light/LightReports.class",
                        "/com/mine/geometry_node/client/runtime/light/NodeLightShader.class",
                        "/com/mine/geometry_node/client/runtime/light/NodeLightTargets.class",
                        "/com/mine/geometry_node/client/runtime/light/NodeLightPixelDiagnostics.class",
                        "/com/mine/geometry_node/client/runtime/light/NodeLightReceiverCostDiagnostics.class")) {
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
