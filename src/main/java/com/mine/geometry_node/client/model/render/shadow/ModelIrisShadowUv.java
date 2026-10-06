package com.mine.geometry_node.client.model.render.shadow;

import com.mine.geometry_node.client.model.render.pipeline.ModelMaskCoverageOverride;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20C;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Scoped at the real GL draw after Iris setup; leaves all host draws unchanged. */
public final class ModelIrisShadowUv {
    private static final Map<Integer, Integer> LOCATIONS = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> DEPTH_PROGRAMS = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> VOXEL_PROGRAMS = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Integer> SEUS_DEPTH_PROGRAMS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> TOLERANCE_LOCATIONS = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> SEUS_COVERAGE_PROGRAMS = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> SEUS_ORIENTED_COVERAGE_PROGRAMS = ConcurrentHashMap.newKeySet();
    private static final int[] VIEWPORT = new int[4];
    private static final ModelShadowUniformUploads UNIFORM_UPLOADS = new ModelShadowUniformUploads();
    private static final ModelShadowUniformUploads.Driver UNIFORM_DRIVER = new ModelShadowUniformUploads.Driver() {
        public void viewport(int location, int[] value) { GL20C.glUniform4f(location, value[0], value[1], value[2], value[3]); }
        public void tolerance(int location, float value) { GL20C.glUniform1f(location, value); }
    };
    private static boolean viewportKnown;
    private static boolean active;
    private static float tolerance;
    private static long appliedDraws, unmatchedDraws, seusDepthDraws;
    private static final ModelMaskCoverageOverride.Driver DRIVER = new ModelMaskCoverageOverride.Driver() {
        public int get(int program, int location) { return GL20C.glGetUniformi(program, location); }
        public void set(int location, int value) { GL20C.glUniform1i(location, value); }
    };
    public static void linked(int program, String vertex, String geometry) {
        UNIFORM_UPLOADS.remove(program);
        DEPTH_PROGRAMS.remove(program); VOXEL_PROGRAMS.remove(program); SEUS_DEPTH_PROGRAMS.remove(program);
        SEUS_COVERAGE_PROGRAMS.remove(program);
        SEUS_ORIENTED_COVERAGE_PROGRAMS.remove(program);
        TOLERANCE_LOCATIONS.remove(program);
        if (ModelSeusShadowDepth.patched(geometry)) SEUS_DEPTH_PROGRAMS.put(program, -2);
        if (geometry != null && geometry.contains("uniform float " + ModelSeusShadowDepth.TOLERANCE + ";")) TOLERANCE_LOCATIONS.put(program, -2);
        if (geometry != null && geometry.contains("flat out vec3 modelShadowBaryA;")) SEUS_COVERAGE_PROGRAMS.add(program);
        if (ModelSeusShadowDepth.patched(geometry) && geometry.contains("bool modelOriented = false;")) SEUS_ORIENTED_COVERAGE_PROGRAMS.add(program);
        if (vertex != null && vertex.contains("out vec3 geometry_node_shadowPosition;")) DEPTH_PROGRAMS.add(program);
        if (ModelSeusShadowVoxel.patched(geometry)) VOXEL_PROGRAMS.add(program);
        if (ModelSundialShadowUv.patched(vertex) || ModelSeusShadowVoxel.patched(geometry)) LOCATIONS.put(program, -2);
        else LOCATIONS.remove(program);
    }
    public static void deleted(int program) { UNIFORM_UPLOADS.remove(program); LOCATIONS.remove(program); DEPTH_PROGRAMS.remove(program); VOXEL_PROGRAMS.remove(program); SEUS_DEPTH_PROGRAMS.remove(program); SEUS_COVERAGE_PROGRAMS.remove(program); SEUS_ORIENTED_COVERAGE_PROGRAMS.remove(program); TOLERANCE_LOCATIONS.remove(program); }
    public static Scope scope() {
        RenderSystem.assertOnRenderThread();
        if (active) throw new IllegalStateException("Nested model shadow UV scope");
        active = true;
        UNIFORM_UPLOADS.clear();
        viewportKnown = false;
        tolerance = 0;
        return new Scope();
    }
    /** Set from the queued caster, not the last model visited while collecting draws. */
    public static void tolerance(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 2) throw new IllegalArgumentException("Shadow tolerance must be in [0, 2]");
        tolerance = value;
    }
    public static final class Scope implements AutoCloseable {
        private boolean closed;
        private Scope() {}
        public void close() { if (!closed) { active = false; UNIFORM_UPLOADS.clear(); closed = true; } }
    }
    public static boolean isActive() { return active; }
    public static boolean toleranceSupported() { return !TOLERANCE_LOCATIONS.isEmpty(); }
    public static void draw(Runnable draw) {
        if (!active) { draw.run(); return; }
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Integer location = LOCATIONS.get(program);
        if (location != null && location == -2) {
            location = GL20C.glGetUniformLocation(program, ModelSundialShadowUv.UNIFORM);
            LOCATIONS.put(program, location);
        }
        if (location == null || location < 0) { unmatchedDraws++; draw.run(); return; }
        Integer viewport = SEUS_DEPTH_PROGRAMS.get(program);
        if (viewport != null && viewport == -2) {
            viewport = GL20C.glGetUniformLocation(program, ModelSeusShadowDepth.VIEWPORT);
            SEUS_DEPTH_PROGRAMS.put(program, viewport);
        }
        // This uniform is only read under the scoped model flag; host draws ignore it.
        if (viewport != null && viewport >= 0) {
            if (!viewportKnown) {
                GL20C.glGetIntegerv(GL20C.GL_VIEWPORT, VIEWPORT);
                viewportKnown = true;
            }
        }
        Integer quality = TOLERANCE_LOCATIONS.get(program);
        if (quality != null && quality == -2) {
            quality = GL20C.glGetUniformLocation(program, ModelSeusShadowDepth.TOLERANCE);
            TOLERANCE_LOCATIONS.put(program, quality);
        }
        UNIFORM_UPLOADS.apply(UNIFORM_DRIVER, program, viewport == null ? -1 : viewport,
                quality == null ? -1 : quality, VIEWPORT, tolerance);
        ModelMaskCoverageOverride.draw(DRIVER, program, location, draw);
        if (viewport != null && viewport >= 0) seusDepthDraws++;
        appliedDraws++;
    }
    public static Map<String, Object> report() {
        return Map.ofEntries(Map.entry("appliedDraws", appliedDraws), Map.entry("unmatchedDraws", unmatchedDraws),
                Map.entry("sundialDepthPrograms", DEPTH_PROGRAMS.size()), Map.entry("seusVoxelGuardPrograms", VOXEL_PROGRAMS.size()),
                Map.entry("seusDepthPrograms", SEUS_DEPTH_PROGRAMS.size()),
                Map.entry("seusCoveragePrograms", SEUS_COVERAGE_PROGRAMS.size()),
                Map.entry("seusOrientedCoveragePrograms", SEUS_ORIENTED_COVERAGE_PROGRAMS.size()),
                Map.entry("seusDepthDraws", seusDepthDraws),
                Map.entry("uniformUploads", UNIFORM_UPLOADS.uploads()), Map.entry("uniformUploadsSkipped", UNIFORM_UPLOADS.skippedUploads()),
                Map.entry("scope", "Model-only Sundial depth, SEUS finite triangle coverage/depth, Sundial repeat UV and SEUS block voxel exclusion; unmatched programs unchanged, pixels require verification"));
    }
    private ModelIrisShadowUv() {}
}
