package com.mine.geometry_node.client.model.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20C;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Scoped at the real GL draw after Iris setup; leaves all host draws unchanged. */
public final class ModelIrisShadowUv {
    private static final Map<Integer, Integer> LOCATIONS = new ConcurrentHashMap<>();
    private static boolean active;
    private static long appliedDraws, unmatchedDraws;
    private static final ModelMaskCoverageOverride.Driver DRIVER = new ModelMaskCoverageOverride.Driver() {
        public int get(int program, int location) { return GL20C.glGetUniformi(program, location); }
        public void set(int location, int value) { GL20C.glUniform1i(location, value); }
    };
    public static void linked(int program, String vertex) {
        if (ModelSundialShadowUv.patched(vertex)) LOCATIONS.put(program, -2);
        else LOCATIONS.remove(program);
    }
    public static void deleted(int program) { LOCATIONS.remove(program); }
    public static Scope scope() {
        RenderSystem.assertOnRenderThread();
        if (active) throw new IllegalStateException("Nested model shadow UV scope");
        active = true;
        return new Scope();
    }
    public static final class Scope implements AutoCloseable {
        private boolean closed;
        private Scope() {}
        public void close() { if (!closed) { active = false; closed = true; } }
    }
    public static boolean isActive() { return active; }
    public static void draw(Runnable draw) {
        if (!active) { draw.run(); return; }
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Integer location = LOCATIONS.get(program);
        if (location != null && location == -2) {
            location = GL20C.glGetUniformLocation(program, ModelSundialShadowUv.UNIFORM);
            LOCATIONS.put(program, location);
        }
        if (location == null || location < 0) { unmatchedDraws++; draw.run(); return; }
        ModelMaskCoverageOverride.draw(DRIVER, program, location, draw);
        appliedDraws++;
    }
    public static Map<String, Object> report() {
        return Map.of("appliedDraws", appliedDraws, "unmatchedDraws", unmatchedDraws,
                "scope", "Known Sundial vertex UV sentinel bypass for model shadows only; unmatched programs unchanged, pixels require verification");
    }
    private ModelIrisShadowUv() {}
}
