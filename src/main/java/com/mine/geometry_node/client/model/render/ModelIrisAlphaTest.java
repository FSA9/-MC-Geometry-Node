package com.mine.geometry_node.client.model.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20C;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.concurrent.ConcurrentHashMap;

/** Optional, scoped per-material MASK threshold applied after Iris has set up its program. */
public final class ModelIrisAlphaTest {
    private static final class Program {
        final String name, outcomeKey;
        final ModelIrisAlphaSource.Profile profile;
        final boolean coveragePatched;
        int[] locations;
        ModelIrisAlphaSource.Comparison[] comparisons;
        float[] values, previous;
        float liveFloor;
        int coverageLocation = -1;
        Program(int id, String name, ModelIrisAlphaSource.Profile profile, boolean coveragePatched) {
            this.name = name; this.outcomeKey = name + "#" + id;
            this.profile = profile; this.coveragePatched = coveragePatched;
        }
        void resolve(int id) {
            var thresholds = profile.thresholds();
            int[] resolved = new int[thresholds.size()];
            ModelIrisAlphaSource.Comparison[] resolvedComparisons = new ModelIrisAlphaSource.Comparison[resolved.length];
            int count = 0;
            for (var threshold : thresholds) {
                int location = GL20C.glGetUniformLocation(id, threshold.uniformName());
                if (location < 0) continue;
                resolved[count] = location; resolvedComparisons[count++] = threshold.comparison();
                liveFloor = Math.max(liveFloor, threshold.minimumAlpha());
            }
            locations = java.util.Arrays.copyOf(resolved, count);
            comparisons = java.util.Arrays.copyOf(resolvedComparisons, count);
            values = new float[count]; previous = new float[count];
            if (coveragePatched) coverageLocation = GL20C.glGetUniformLocation(id, ModelSundialMaskCoverage.UNIFORM);
        }
    }
    private static final Map<Integer, Program> PROGRAMS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Long>> OUTCOMES = new ConcurrentHashMap<>();
    private static Scope active;
    private static final Map<RenderType, ModelIrisAlphaPolicy> RENDER_TYPES = new WeakHashMap<>();
    private static long appliedDraws, degradedDraws, floorLimitedDraws;
    private static long maskCoverageApplied, maskCoverageUnsupported;
    private static String lastDegradation = "";
    private static final ModelAlphaUniformOverride.Driver GL = new ModelAlphaUniformOverride.Driver() {
        public float get(int program, int location) { return GL20C.glGetUniformf(program, location); }
        public void set(int location, float value) { GL20C.glUniform1f(location, value); }
    };

    private static final ModelMaskCoverageOverride.Driver COVERAGE_GL = new ModelMaskCoverageOverride.Driver() {
        public int get(int program, int location) { return GL20C.glGetUniformi(program, location); }
        public void set(int location, int value) { GL20C.glUniform1i(location, value); }
    };

    /** Called by the optional Iris link observer; the program has just linked. */
    public static void linked(int program, String name, String fragment) {
        ModelIrisAlphaSource.Profile profile = ModelIrisAlphaSource.profile(fragment);
        // Do not force a sync point in Iris's parallel shader-linking stage.
        // Resolve the location once, only when this program actually draws a scoped material.
        PROGRAMS.put(program, new Program(program, name, profile, (ModelSundialMaskCoverage.patched(fragment) || ModelSundialShadowCoverage.patchedFragment(fragment))));
    }

    public static void deleted(int program) { PROGRAMS.remove(program); }

    /** Render-thread-only weak metadata: disposed assets are not retained by compatibility routing. */
    public static void registerRenderType(RenderType type, ModelIrisAlphaPolicy policy) {
        RenderSystem.assertOnRenderThread();
        if (policy.needsOverride()) RENDER_TYPES.put(type, policy); else RENDER_TYPES.remove(type);
    }

    /** Open in the existing RenderType.draw wrapper, after collector emission and before draw. */
    public static Scope forRenderType(RenderType type) { return scope(RENDER_TYPES.get(type)); }

    public static Scope mask(float cutoff) { return scope(ModelIrisAlphaPolicy.mask(cutoff)); }
    public static Scope blend() { return scope(ModelIrisAlphaPolicy.blend()); }

    /** A null result intentionally means no material override (safe in try-with-resources). */
    public static Scope scope(ModelIrisAlphaPolicy policy) {
        RenderSystem.assertOnRenderThread();
        if (policy == null || !policy.needsOverride()) return null;
        if (active != null) throw new IllegalStateException("Nested model alpha-test scopes");
        Scope scope = new Scope(policy);
        active = scope;
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final ModelIrisAlphaPolicy policy;
        private boolean closed;
        private Scope(ModelIrisAlphaPolicy policy) { this.policy = policy; }
        @Override public void close() {
            if (closed) return;
            if (active != this) throw new IllegalStateException("Model alpha scope closed out of order");
            active = null;
            closed = true;
        }
    }

    /** Runs at the real GL indexed draw, after host/Iris program and uniforms have been applied. */
    public static boolean isActive() { return active != null; }

    public static void draw(Runnable draw) {
        Scope scope = active;
        if (scope == null) { draw.run(); return; }
        int programId = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Program program = PROGRAMS.get(programId);
        if (program != null && program.locations == null) program.resolve(programId);
        if (program == null || program.profile.comparison() == ModelIrisAlphaSource.Comparison.UNSUPPORTED) {
            degradedDraws++;
            lastDegradation = program == null ? "Iris program source was not observed"
                    : program.profile.reason();
            outcome(programId, program, lastDegradation);
            draw.run();
            return;
        }
        if (program.locations.length == 0) {
            degradedDraws++;
            lastDegradation = "All recognized alpha uniforms are inactive";
            outcome(programId, program, lastDegradation);
            draw.run();
            return;
        }
        for (int i = 0; i < program.values.length; i++)
            program.values[i] = scope.policy.threshold(program.comparisons[i]);
        Program selected = program;
        Runnable coveredDraw = draw;
        if (scope.policy.mode() == ModelIrisAlphaPolicy.Mode.MASK) {
            if (program.coverageLocation >= 0) {
                coveredDraw = () -> {
                    ModelMaskCoverageOverride.draw(COVERAGE_GL, programId, selected.coverageLocation, draw);
                    maskCoverageApplied++;
                    outcome(programId, selected, "MASK_COVERAGE_APPLIED");
                };
            } else {
                maskCoverageUnsupported++;
                outcome(programId, program, "MASK_COVERAGE_UNSUPPORTED: not a recognized Sundial solid handoff or paired shadow atlas");
            }
        }
        ModelAlphaUniformOverride.draw(GL, programId, program.locations, program.values, program.previous, coveredDraw);
        float requested = scope.policy.mode() == ModelIrisAlphaPolicy.Mode.MASK ? scope.policy.cutoff() : 0;
        if (requested >= program.liveFloor) {
            appliedDraws++;
            outcome(programId, program, scope.policy.mode() == ModelIrisAlphaPolicy.Mode.MASK ? "APPLIED_MASK" : "APPLIED_BLEND");
        } else {
            degradedDraws++;
            floorLimitedDraws++;
            lastDegradation = "Pack alpha floor " + program.liveFloor + " exceeds " + scope.policy.mode() + " threshold " + requested;
            outcome(programId, program, lastDegradation);
        }
    }

    private static void outcome(int id, Program program, String reason) {
        String key = program == null ? "unobserved#" + id : program.outcomeKey;
        // Bound diagnostic history across repeated shader reloads without retaining GPU resources.
        if (OUTCOMES.size() >= 256 && !OUTCOMES.containsKey(key)) OUTCOMES.clear();
        OUTCOMES.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).merge(reason, 1L, Long::sum);
    }

    public static Map<String, Object> report() {
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("status", degradedDraws > 0 ? "DEGRADED" : appliedDraws > 0 ? "IRIS_OUTPUT_ALPHA_ROUTED_UNVERIFIED" : "UNTESTED");
        report.put("observedPrograms", PROGRAMS.size()); report.put("programOutcomes", snapshotOutcomes());
        report.put("appliedDraws", appliedDraws); report.put("degradedDraws", degradedDraws);
        report.put("floorLimitedDraws", floorLimitedDraws); report.put("lastDegradation", lastDegradation);
        report.put("maskCoverageApplied", maskCoverageApplied); report.put("maskCoverageUnsupported", maskCoverageUnsupported);
        report.put("scope", "Recognized Iris/core thresholds; known Sundial solid MASK coverage and paired opaque shadow atlas; fixed pack floors preserved/degraded");
        return report;
    }

    private static Map<String, Map<String, Long>> snapshotOutcomes() {
        Map<String, Map<String, Long>> snapshot = new java.util.TreeMap<>();
        OUTCOMES.forEach((name, counters) -> snapshot.put(name, Map.copyOf(counters)));
        return snapshot;
    }

    private ModelIrisAlphaTest() {}
}
