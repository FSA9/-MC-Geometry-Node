package com.mine.geometry_node.client.model.render.bvh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Render-thread A/B profiler for the shader-side model-light modules. */
public final class ModelLightingProfiler {
    enum Mode { LIGHTING, QUALITY, RESOLUTION }
    record Stage(String name, boolean localEnabled, boolean giEnabled, boolean bvh, boolean primaryGi, boolean secondaryGi,
                 boolean heldOcclusion, boolean worldOcclusion, boolean placed, boolean dynamic,
                 ModelLightingQuality quality) {
        Stage(String name, boolean bvh, boolean primaryGi, boolean secondaryGi,
              boolean heldOcclusion, boolean worldOcclusion, boolean placed, boolean dynamic,
              ModelLightingQuality quality) {
            this(name, true, true, bvh, primaryGi, secondaryGi, heldOcclusion, worldOcclusion, placed, dynamic, quality);
        }
        ModelIrisHeldLight.ModuleState state() {
            return new ModelIrisHeldLight.ModuleState(true, bvh, true, placed, dynamic,
                    primaryGi, secondaryGi, heldOcclusion, worldOcclusion);
        }
    }
    record Session(Mode mode, ModelIrisHeldLight.ModuleState originalModules, ModelLightingQuality originalQuality,
                   List<Stage> stages) {
        Session(Mode mode, ModelIrisHeldLight.ModuleState modules, ModelLightingQuality quality) {
            this(mode, modules, quality, stagePlan(mode, quality));
        }
        void apply(int index, Consumer<ModelIrisHeldLight.ModuleState> modules, Consumer<ModelLightingQuality> quality) {
            Stage stage = stages.get(index);
            modules.accept(stage.state());
            quality.accept(stage.quality());
        }
        void restore(Consumer<ModelIrisHeldLight.ModuleState> modules, Consumer<ModelLightingQuality> quality) {
            modules.accept(originalModules);
            quality.accept(originalQuality);
        }
    }

    static List<Stage> stagePlan(Mode mode, ModelLightingQuality quality) {
        if (mode != Mode.LIGHTING && quality.automatic()) quality = new ModelLightingQuality(4, 1);
        if (!quality.automatic() && (quality.areaSamples() == 0 || quality.resolutionScale() == 0))
            quality = new ModelLightingQuality(quality.areaSamples() == 0 ? 4 : quality.areaSamples(),
                    quality.resolutionScale() == 0 ? 1 : quality.resolutionScale());
        if (mode == Mode.QUALITY) return List.of(
                qualityStage("samples4", quality.withSamples(4)),
                qualityStage("samples3", quality.withSamples(3)),
                qualityStage("samples2", quality.withSamples(2)),
                qualityStage("samples1", quality.withSamples(1)),
                qualityStage("samples4Repeat", quality.withSamples(4)));
        if (mode == Mode.RESOLUTION) return List.of(
                qualityStage("resolution1", quality.withResolutionScale(1.0f)),
                qualityStage("resolution0.75", quality.withResolutionScale(0.75f)),
                qualityStage("resolution0.5", quality.withResolutionScale(0.5f)),
                qualityStage("resolution0.25", quality.withResolutionScale(0.25f)),
                qualityStage("resolution1Repeat", quality.withResolutionScale(1.0f)));
        return List.of(
                new Stage("allEnabled", true, true, true, true, true, true, true, quality),
                new Stage("localLightOff", false, true, true, true, true, true, true, true, true, quality),
                new Stage("giOcclusionOff", true, false, true, true, true, true, true, true, true, quality),
                new Stage("bvhOcclusionOff", false, true, true, true, true, true, true, quality),
                new Stage("primaryGiOcclusionOff", true, false, true, true, true, true, true, quality),
                new Stage("secondaryGiOcclusionOff", true, true, false, true, true, true, true, quality),
                new Stage("heldOcclusionOff", true, true, true, false, true, true, true, quality),
                new Stage("worldOcclusionOff", true, true, true, true, false, true, true, quality),
                new Stage("placedReconstructionOff", true, true, true, true, true, false, true, quality),
                new Stage("providerLightOff", true, true, true, true, true, true, false, quality),
                new Stage("allEnabledRepeat", true, true, true, true, true, true, true, quality));
    }
    private static Stage qualityStage(String name, ModelLightingQuality quality) {
        return new Stage(name, true, true, true, true, true, true, true, quality);
    }
    private static final int WARMUP_FRAMES = 60;
    private static boolean active;
    private static int targetSamples;
    private static int stageIndex;
    private static int warmup;
    private static long lastFrameNanos;
    private static List<Long> samples = new ArrayList<>();
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static Session session;
    private static ReceiverSettings originalReceiverSettings;
    private static Consumer<Map<String,Object>> completion;
    private static Conditions sampleConditions;
    private static boolean conditionsChanged;
    private static boolean qualityChanged;
    private static boolean modulesChanged;

    record ReceiverSettings(boolean local, boolean gi, boolean suspended) {
        static ReceiverSettings capture() {
            var settings = ModelLightingSettings.global();
            return new ReceiverSettings(settings.localEnabled(), settings.giEnabled(), ModelLightingSettings.overridesSuspended());
        }
        void restore() {
            ModelLightingSettings.setLocalEnabled(null, local);
            ModelLightingSettings.setGiEnabled(null, gi);
            ModelLightingSettings.setOverridesSuspended(suspended);
        }
        static void apply(Stage stage) {
            ModelLightingSettings.setOverridesSuspended(true);
            ModelLightingSettings.setLocalEnabled(null, stage.localEnabled());
            ModelLightingSettings.setGiEnabled(null, stage.giEnabled());
        }
        boolean matches(Stage stage) { return suspended && local == stage.localEnabled() && gi == stage.giEnabled(); }
    }

    record Conditions(double x, double y, double z, float yaw, float pitch, int width, int height, int heldEmission,
                      ModelLightingQuality quality) {
        Conditions(double x, double y, double z, float yaw, float pitch, int width, int height, int heldEmission) {
            this(x, y, z, yaw, pitch, width, height, heldEmission, new ModelLightingQuality(4, 1.0f));
        }
        static Conditions capture() {
            var minecraft = Minecraft.getInstance();
            var camera = minecraft.gameRenderer.getMainCamera().position();
            var player = minecraft.player;
            return new Conditions(camera.x, camera.y, camera.z,
                    player == null ? 0 : player.getYRot(), player == null ? 0 : player.getXRot(),
                    minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), ModelIrisHeldLight.heldEmission(),
                    ModelLightingQuality.active());
        }
        boolean matches(Conditions other) {
            return quality.equals(other.quality) && matchesScene(other);
        }
        boolean matchesScene(Conditions other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz < 0.0001
                    && Math.abs(Math.IEEEremainder(yaw - other.yaw, 360.0)) < 0.1
                    && Math.abs(pitch - other.pitch) < 0.1 && width == other.width
                    && height == other.height && heldEmission == other.heldEmission;
        }
        Map<String,Object> report() {
            return Map.of("camera", List.of(x, y, z), "viewAngles", List.of(yaw, pitch),
                    "resolution", List.of(width, height), "heldLightEmission", heldEmission,
                    "automatic", quality.automatic(), "areaSamples", quality.areaSamples(), "resolutionScale", quality.resolutionScale());
        }
        static Conditions fromReport(Map<?,?> report) {
            var camera = (List<?>) report.get("camera");
            var angles = (List<?>) report.get("viewAngles");
            var resolution = (List<?>) report.get("resolution");
            return new Conditions(((Number) camera.get(0)).doubleValue(), ((Number) camera.get(1)).doubleValue(),
                    ((Number) camera.get(2)).doubleValue(), ((Number) angles.get(0)).floatValue(),
                    ((Number) angles.get(1)).floatValue(), ((Number) resolution.get(0)).intValue(),
                    ((Number) resolution.get(1)).intValue(), ((Number) report.get("heldLightEmission")).intValue(),
                    qualityFromReport(report));
        }
    }

    private ModelLightingProfiler() {}

    public static void start(int sampleFrames, Consumer<Map<String,Object>> finished) {
        start(Mode.LIGHTING, sampleFrames, finished);
    }

    public static void startQuality(int sampleFrames, Consumer<Map<String,Object>> finished) {
        start(Mode.QUALITY, sampleFrames, finished);
    }

    public static void startResolution(int sampleFrames, Consumer<Map<String,Object>> finished) {
        start(Mode.RESOLUTION, sampleFrames, finished);
    }

    private static void start(Mode mode, int sampleFrames, Consumer<Map<String,Object>> finished) {
        RenderSystem.assertOnRenderThread();
        if (active) throw new IllegalStateException("lighting profile already running");
        if (sampleFrames < 1 || sampleFrames > 120) throw new IllegalArgumentException("profile frame count must be 1..120");
        completion = Objects.requireNonNull(finished);
        session = new Session(mode, ModelIrisHeldLight.moduleState(), ModelLightingQuality.active());
        originalReceiverSettings = ReceiverSettings.capture();
        targetSamples = sampleFrames; stageIndex = 0; warmup = WARMUP_FRAMES;
        lastFrameNanos = System.nanoTime(); samples = new ArrayList<>(sampleFrames); results.clear(); active = true;
        try { applyStage(); }
        catch (RuntimeException | Error failure) { cancel(); throw failure; }
    }

    public static boolean active() { return active; }
    public static int stageCount() { return active ? session.stages.size() : 11; }
    public static void cancel() {
        RenderSystem.assertOnRenderThread();
        if (!active) return;
        session.restore(ModelIrisHeldLight::applyModuleState, ModelLightingQuality::setActive);
        originalReceiverSettings.restore();
        originalReceiverSettings = null;
        active = false; session = null; completion = null; samples.clear(); results.clear();
        ModelShaderGpuTimings.clearSamples();
    }
    public static String status() {
        if (!active) return "IDLE";
        return session.mode.name().toLowerCase(java.util.Locale.ROOT) + ": " + session.stages.get(stageIndex).name
                + " " + samples.size() + "/" + targetSamples + " (warmup " + warmup + ")";
    }

    /** Called once at the beginning of the model main-render callback. */
    public static void onFrameStart() {
        if (!active) return;
        long now = System.nanoTime();
        long elapsed = now - lastFrameNanos;
        lastFrameNanos = now;
        Stage expected = session.stages.get(stageIndex);
        qualityChanged |= !ModelLightingQuality.active().equals(expected.quality);
        modulesChanged |= !ModelIrisHeldLight.moduleState().equals(expected.state());
        modulesChanged |= !ReceiverSettings.capture().matches(expected);
        if (warmup > 0) {
            if (--warmup == 0) {
                ModelShaderGpuTimings.clearSamples();
                sampleConditions = Conditions.capture();
                conditionsChanged = false;
                qualityChanged |= !sampleConditions.quality.equals(session.stages.get(stageIndex).quality);
            }
            return;
        }
        if (elapsed <= 0) return;
        Conditions current = Conditions.capture();
        conditionsChanged |= !sampleConditions.matchesScene(current);
        qualityChanged |= !current.quality.equals(session.stages.get(stageIndex).quality);
        samples.add(elapsed);
        if (samples.size() >= targetSamples) finishStage();
    }

    private static void applyStage() {
        session.apply(stageIndex, ModelIrisHeldLight::applyModuleState, ModelLightingQuality::setActive);
        ReceiverSettings.apply(session.stages.get(stageIndex));
        qualityChanged = false;
        modulesChanged = false;
    }

    private static void finishStage() {
        Stage stage = session.stages.get(stageIndex);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("stage", stage.name);
        result.put("modules", modules(stage.state()));
        result.put("receiverModules", Map.of("localLight", stage.localEnabled(), "giOcclusion", stage.giEnabled(),
                "instanceOverridesSuspended", true));
        result.put("quality", stage.quality.report());
        Map<String,Object> lighting = ModelIrisHeldLight.report();
        result.put("qualityApplicability", qualityApplicability(lighting, session.mode));
        if (lighting.get("resolution") instanceof Map<?,?> resolution) result.put("resolution", Map.copyOf(resolution));
        result.put("samples", samples.size());
        result.put("timing", timing(samples));
        result.put("shaderGpu", ModelShaderGpuTimings.report());
        result.put("sampleConditions", sampleConditions.report());
        result.put("conditionsChangedDuringSamples", conditionsChanged);
        result.put("qualityChangedDuringStage", qualityChanged);
        result.put("modulesChangedDuringStage", modulesChanged);
        results.add(Map.copyOf(result));
        stageIndex++;
        if (stageIndex >= session.stages.size()) finishProfile();
        else { samples = new ArrayList<>(targetSamples); warmup = WARMUP_FRAMES; lastFrameNanos = System.nanoTime(); applyStage(); }
    }

    private static void finishProfile() {
        session.restore(ModelIrisHeldLight::applyModuleState, ModelLightingQuality::setActive);
        originalReceiverSettings.restore();
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("status", "COMPLETE");
        report.put("mode", session.mode.name().toLowerCase(java.util.Locale.ROOT));
        report.put("sampleFramesPerStage", targetSamples);
        report.put("warmupFramesPerStage", WARMUP_FRAMES);
        report.put("stages", List.copyOf(results));
        report.put("interpretation", "Frame timings cover whole render-callback intervals; shaderGpu measures complete draws, not isolated BVH functions. Module-stage differences cannot be added. Quality stages compare 4/3/2/1 samples at the starting resolution; resolution stages compare 1/0.75/0.5/0.25 scale at the starting sample count. Every quality/resolution stage preserves occlusion modules. Local-light GPU estimates sum the medians of deferred10 and bvh_local, including the reduced-resolution prepass; this is not the median of their frame-by-frame sum. Candidate sources indicate potential applicability, not actual queries or visible illumination. GI history may not converge in 60 warmup frames; inspect repeated-baseline drift.");
        report.put("comparisons", comparisons(results));
        report.put("measurementQuality", measurementQuality(results, session.mode));
        report.put("restoredModules", modules(session.originalModules));
        report.put("restoredQuality", session.originalQuality.report());
        report.put("restoredReceiverModules", Map.of("localLight", originalReceiverSettings.local(),
                "giOcclusion", originalReceiverSettings.gi(), "instanceOverridesSuspended", originalReceiverSettings.suspended()));
        report.put("receiverProfilePolicy", "Instance overrides are temporarily suspended, never removed. Local light and GI are enabled except in their dedicated off stages; settings are restored after completion or cancellation.");
        originalReceiverSettings = null;
        Consumer<Map<String,Object>> callback = completion;
        active = false; completion = null; session = null; samples = new ArrayList<>(); results.clear();
        callback.accept(Map.copyOf(report));
    }

    private static Map<String,Object> modules(ModelIrisHeldLight.ModuleState state) {
        return Map.of("lighting", state.lighting(), "bvhOcclusion", state.bvh(), "heldLight", state.held(),
                "placedReconstruction", state.placed(), "providerLight", state.dynamic(),
                "primaryGiOcclusion", state.primaryGi(), "secondaryGiOcclusion", state.secondaryGi(),
                "heldOcclusion", state.heldOcclusion(), "worldOcclusion", state.worldOcclusion());
    }

    static List<Map<String,Object>> comparisons(List<Map<String,Object>> stages) {
        if (stages.isEmpty()) return List.of();
        double baseline = ((Number) ((Map<?,?>) stages.getFirst().get("timing")).get("frameTimeMsAverage")).doubleValue();
        List<Map<String,Object>> comparisons = new ArrayList<>();
        for (int i = 1; i < stages.size(); i++) {
            Map<String,Object> stage = stages.get(i);
            double elapsed = ((Number) ((Map<?,?>) stage.get("timing")).get("frameTimeMsAverage")).doubleValue();
            Map<String,Object> comparison = new LinkedHashMap<>();
            comparison.put("stage", stage.get("stage"));
            comparison.put("frameTimeMsSavedVsBaseline", baseline - elapsed);
            double firstGpu = localLightGpuNanos(stages.getFirst());
            double currentGpu = localLightGpuNanos(stage);
            if (Double.isFinite(firstGpu) && Double.isFinite(currentGpu)) {
                comparison.put("localLightGpuMsSavedVsBaseline", (firstGpu - currentGpu) / 1_000_000.0);
                comparison.put("localLightGpuScope", "sum_of_pass_medians:deferred10+bvh_local");
            }
            comparisons.add(Map.copyOf(comparison));
        }
        return List.copyOf(comparisons);
    }

    static Map<String,Object> measurementQuality(List<Map<String,Object>> stages) {
        return measurementQuality(stages, Mode.LIGHTING);
    }

    static Map<String,Object> qualityApplicability(Map<String,Object> lighting) {
        return qualityApplicability(lighting, Mode.QUALITY);
    }

    static Map<String,Object> qualityApplicability(Map<String,Object> lighting, Mode mode) {
        int programs = ((Number) lighting.getOrDefault("placedAreaQualityPrograms", 0)).intValue();
        int instances = ((Number) lighting.getOrDefault("activeInstances", 0)).intValue();
        boolean ready = Boolean.TRUE.equals(lighting.get("seusPlacedDirectReady"));
        Map<?,?> placed = lighting.get("placedLight") instanceof Map<?,?> value ? value : Map.of();
        int sources = placed.get("sourcesIncludingOverlap") instanceof Number value ? value.intValue() : 0;
        int heldEmission = lighting.get("heldLightEmission") instanceof Number value ? value.intValue() : 0;
        float requested = lighting.get("quality") instanceof Map<?,?> value
                ? qualityFromReport(value).resolutionScale() : 1.0f;
        boolean hasSource = sources > 0 || mode == Mode.RESOLUTION && heldEmission > 0;
        return Map.of("programs", programs, "pairedProgramsReady", ready, "activeInstances", instances,
                "placedSourcesIncludingOverlap", sources, "heldLightEmission", heldEmission,
                "supported", programs > 0, "potentiallyActive", requested > 0 && programs > 0 && ready && instances > 0 && hasSource);
    }

    private static ModelLightingQuality qualityFromReport(Map<?,?> values) {
        int samples = values.get("areaSamples") instanceof Number value ? value.intValue() : 4;
        float scale = values.get("resolutionScale") instanceof Number value ? value.floatValue() : 1.0f;
        return new ModelLightingQuality(Boolean.TRUE.equals(values.get("automatic")), samples, scale);
    }

    static Map<String,Object> measurementQuality(List<Map<String,Object>> stages, Mode mode) {
        if (stages.isEmpty()) return Map.of("status", "NO_SAMPLES");
        Map<String,Object> first = stages.getFirst(), last = stages.getLast();
        double baseline = ((Number) ((Map<?,?>) first.get("timing")).get("frameTimeMsAverage")).doubleValue();
        double repeat = ((Number) ((Map<?,?>) last.get("timing")).get("frameTimeMsAverage")).doubleValue();
        boolean qualityMode = mode != Mode.LIGHTING;
        String repeatName = switch (mode) {
            case LIGHTING -> "allEnabledRepeat";
            case QUALITY -> "samples4Repeat";
            case RESOLUTION -> "resolution1Repeat";
        };
        boolean hasRepeat = repeatName.equals(last.get("stage"));
        double drift = hasRepeat && baseline > 0 ? (repeat / baseline - 1.0) * 100.0 : 0.0;
        Conditions originalConditions = first.get("sampleConditions") instanceof Map<?,?> values ? Conditions.fromReport(values) : null;
        boolean changed = stages.stream().anyMatch(stage -> Boolean.TRUE.equals(stage.get("conditionsChangedDuringSamples"))
                || originalConditions != null && (!(stage.get("sampleConditions") instanceof Map<?,?> values)
                || !originalConditions.matchesScene(Conditions.fromReport(values))));
        boolean changedQuality = stages.stream().anyMatch(stage -> Boolean.TRUE.equals(stage.get("qualityChangedDuringStage"))
                || stage.get("sampleConditions") instanceof Map<?,?> conditions
                && stage.get("quality") instanceof Map<?,?> quality
                && !qualityFromReport(conditions).equals(qualityFromReport(quality)))
                || mode == Mode.LIGHTING && originalConditions != null && stages.stream().anyMatch(stage ->
                stage.get("sampleConditions") instanceof Map<?,?> values
                        && !originalConditions.quality.equals(Conditions.fromReport(values).quality));
        boolean changedModules = stages.stream().anyMatch(stage -> Boolean.TRUE.equals(stage.get("modulesChangedDuringStage")));
        boolean unsupported = qualityMode && stages.stream().anyMatch(stage ->
                !(stage.get("qualityApplicability") instanceof Map<?,?> value) || !Boolean.TRUE.equals(value.get("supported")));
        boolean inactive = qualityMode && stages.stream().anyMatch(stage ->
                !(stage.get("qualityApplicability") instanceof Map<?,?> value) || !Boolean.TRUE.equals(value.get("potentiallyActive")));
        boolean resolutionFallback = qualityMode && stages.stream().anyMatch(stage -> !requestedResolutionApplied(stage));
        double gpuDrift = 0.0;
        String dominantPass = "";
        long longestPass = 0;
        if (hasRepeat && first.get("shaderGpu") instanceof Map<?,?> firstGpu
                && last.get("shaderGpu") instanceof Map<?,?> lastGpu
                && firstGpu.get("passes") instanceof List<?> firstPasses && lastGpu.get("passes") instanceof List<?> lastPasses) {
            for (Object entry : firstPasses) {
                if (!(entry instanceof Map<?,?> pass) || !(pass.get("gpuNanosP50") instanceof Number nanos)
                        || nanos.longValue() <= longestPass) continue;
                for (Object repeated : lastPasses) {
                    if (!(repeated instanceof Map<?,?> repeatedPass) || !Objects.equals(pass.get("name"), repeatedPass.get("name"))
                            || !(repeatedPass.get("gpuNanosP50") instanceof Number repeatedNanos)) continue;
                    longestPass = nanos.longValue();
                    dominantPass = Objects.toString(pass.get("name"));
                    gpuDrift = (repeatedNanos.doubleValue() / longestPass - 1.0) * 100.0;
                }
            }
        }
        boolean gpuUnavailable = qualityMode && stages.stream().anyMatch(stage -> !Double.isFinite(localLightGpuNanos(stage)));
        boolean gpuTooFew = qualityMode && stages.stream().anyMatch(ModelLightingProfiler::localLightGpuTooFew);
        double areaGpuDrift = 0.0;
        if (qualityMode && hasRepeat && !gpuUnavailable) {
            double firstArea = localLightGpuNanos(first);
            double lastArea = localLightGpuNanos(last);
            areaGpuDrift = (lastArea / firstArea - 1.0) * 100.0;
        }
        boolean tooFew = gpuTooFew || stages.stream().anyMatch(stage -> ((Number) stage.getOrDefault("samples", 0)).intValue() < 20);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("status", changedQuality ? "QUALITY_CHANGED" : changedModules ? "MODULES_CHANGED"
                : changed ? "SCENE_CHANGED" : unsupported ? "QUALITY_UNSUPPORTED" : inactive ? "QUALITY_INACTIVE"
                : resolutionFallback ? "RESOLUTION_FALLBACK"
                : gpuUnavailable ? "GPU_TIMING_UNAVAILABLE"
                : !hasRepeat ? "MISSING_REPEAT"
                : Math.abs(drift) > 10.0 || Math.abs(gpuDrift) > 10.0 || Math.abs(areaGpuDrift) > 10.0
                ? "BASELINE_DRIFT" : tooFew ? "LOW_SAMPLE_COUNT" : "STABLE");
        result.put("baselineFrameTimeDriftPercent", drift);
        if (!dominantPass.isEmpty()) {
            result.put("dominantGpuPass", dominantPass);
            result.put("baselineGpuTimeDriftPercent", gpuDrift);
        }
        result.put("conditionsChanged", changed);
        result.put("qualityChanged", changedQuality);
        result.put("modulesChanged", changedModules);
        if (qualityMode) {
            result.put("qualityPotentiallyApplicable", !unsupported && !inactive);
            result.put("requestedResolutionApplied", !resolutionFallback);
            result.put("qualityGpuScope", "sum_of_pass_medians:deferred10+bvh_local");
            result.put("qualityGpuTimingAvailable", !gpuUnavailable);
            result.put("qualityGpuLowSampleCount", gpuTooFew);
            if (!gpuUnavailable && hasRepeat) result.put("qualityGpuTimeDriftPercent", areaGpuDrift);
        }
        result.put("lowSampleCount", tooFew);
        return Map.copyOf(result);
    }

    private static Map<?,?> gpuPass(Map<String,Object> stage, String name) {
        if (stage.get("shaderGpu") instanceof Map<?,?> timings && timings.get("passes") instanceof List<?> passes) {
            for (Object entry : passes) if (entry instanceof Map<?,?> pass && name.equals(pass.get("name"))) return pass;
        }
        return Map.of();
    }

    static boolean requestedResolutionApplied(Map<String,Object> stage) {
        if (!(stage.get("quality") instanceof Map<?,?> quality)) return true;
        float requested = qualityFromReport(quality).resolutionScale();
        if (!(stage.get("resolution") instanceof Map<?,?> resolution)) return requested == 1.0f;
        if (!(resolution.get("requestedScale") instanceof Number requestedValue)
                || !(resolution.get("effectiveScale") instanceof Number effective)) return false;
        return Math.abs(requestedValue.floatValue() - requested) < 0.00001f
                && Math.abs(effective.floatValue() - requested) < 0.00001f;
    }

    private static boolean reducedResolutionActive(Map<String,Object> stage) {
        if (!(stage.get("resolution") instanceof Map<?,?> resolution)
                || !(resolution.get("effectiveScale") instanceof Number scale)) return false;
        return scale.floatValue() > 0.0f && scale.floatValue() < 1.0f;
    }

    static double localLightGpuNanos(Map<String,Object> stage) {
        Map<?,?> resolve = gpuPass(stage, "deferred10"), prepass = gpuPass(stage, "bvh_local");
        if (!(resolve.get("gpuNanosP50") instanceof Number resolveNanos) || resolveNanos.longValue() <= 0) return Double.NaN;
        if (reducedResolutionActive(stage) && (!(prepass.get("gpuNanosP50") instanceof Number nanos) || nanos.longValue() <= 0))
            return Double.NaN;
        return resolveNanos.doubleValue() + (prepass.get("gpuNanosP50") instanceof Number nanos ? nanos.doubleValue() : 0.0);
    }

    private static boolean localLightGpuTooFew(Map<String,Object> stage) {
        Map<?,?> resolve = gpuPass(stage, "deferred10"), prepass = gpuPass(stage, "bvh_local");
        if (!(resolve.get("windowSamples") instanceof Number count) || count.intValue() < 20) return true;
        return (reducedResolutionActive(stage) || !prepass.isEmpty())
                && (!(prepass.get("windowSamples") instanceof Number prepassCount) || prepassCount.intValue() < 20);
    }

    static Map<String,Object> timing(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        long total = 0, max = 0;
        for (long value : sorted) { total = Math.addExact(total, value); max = Math.max(max, value); }
        double average = sorted.length == 0 ? 0.0 : total / (double) sorted.length;
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("frameTimeNanosAverage", average);
        out.put("frameTimeMsAverage", average / 1_000_000.0);
        out.put("fpsAverage", average <= 0 ? 0.0 : 1_000_000_000.0 / average);
        out.put("frameTimeNanosP50", percentile(sorted, .50));
        out.put("frameTimeNanosP95", percentile(sorted, .95));
        out.put("frameTimeNanosP99", percentile(sorted, .99));
        out.put("frameTimeNanosMax", max);
        return Map.copyOf(out);
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(sorted.length * p) - 1))];
    }
}
