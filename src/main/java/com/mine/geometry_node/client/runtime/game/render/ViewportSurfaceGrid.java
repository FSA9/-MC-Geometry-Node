package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.util.HashMap;
import java.util.Map;

/** Terrain-only, one-block surface grid. No additional world pass or block enumeration. */
public final class ViewportSurfaceGrid {
    private static final Map<Integer, Locations> PROGRAMS = new HashMap<>();
    private static final Binding NONE = new Binding(0, -1, 0);

    private ViewportSurfaceGrid() {}

    /** Terrain positions are camera relative in both vanilla and Sodium. Only the
     * fractional camera origin is needed, keeping lines stable far from world zero. */
    public static String declarations() {
        return """
                uniform int viewportGridEnabled;
                uniform vec3 viewportGridOrigin;
                vec3 viewportSurfaceGrid(vec3 color, vec3 relativePosition) {
                    if (viewportGridEnabled == 0) return color;
                    vec3 n = abs(cross(dFdx(relativePosition), dFdy(relativePosition)));
                    if (max(n.x, max(n.y, n.z)) < 1e-12) return color;
                    vec3 p = relativePosition + viewportGridOrigin;
                    vec2 uv = n.y >= n.x && n.y >= n.z ? p.xz : (n.x >= n.z ? p.yz : p.xy);
                    vec2 footprint = max(fwidth(uv), vec2(1e-5));
                    vec2 distanceToLine = abs(fract(uv + 0.5) - 0.5);
                    vec2 lines = (1.0 - smoothstep(vec2(0.35), vec2(1.35), distanceToLine / footprint))
                               * (1.0 - smoothstep(vec2(0.12), vec2(0.45), footprint));
                    float fade = 1.0 - smoothstep(128.0, 192.0, length(relativePosition));
                    return color * (1.0 - 0.55 * max(lines.x, lines.y) * fade);
                }
                """;
    }

    public static Binding bind() {
        if (!ViewportVisualization.inEditorPass() || !ViewportVisualization.current().grid()) return NONE;
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program == 0) return NONE;
        Locations locations = PROGRAMS.computeIfAbsent(program, Locations::new);
        if (locations.enabled < 0) return NONE;
        int previous = GL20C.glGetUniformi(program, locations.enabled);
        var camera = EditViewportRuntime.camera().position();
        GL20C.glUniform3f(locations.origin, fraction(camera.x), fraction(camera.y), fraction(camera.z));
        GL20C.glUniform1i(locations.enabled, 1);
        return new Binding(program, locations.enabled, previous);
    }

    private static float fraction(double value) { return (float) (value - Math.floor(value)); }
    public static void deleted(int program) { PROGRAMS.remove(program); }

    private record Locations(int enabled, int origin) {
        Locations(int program) {
            this(GL20C.glGetUniformLocation(program, "viewportGridEnabled"),
                    GL20C.glGetUniformLocation(program, "viewportGridOrigin"));
        }
    }

    /** Nested vanilla/Sodium draw hooks restore the outer enable flag. The origin
     * is constant within a viewport pass and is overwritten before every enabled draw. */
    public static final class Binding implements AutoCloseable {
        private final int program, location, previous;
        private Binding(int program, int location, int previous) {
            this.program = program;
            this.location = location;
            this.previous = previous;
        }
        @Override public void close() {
            if (location < 0) return;
            int current = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (current != program) GL20C.glUseProgram(program);
            GL20C.glUniform1i(location, previous);
            if (current != program) GL20C.glUseProgram(current);
        }
    }
}
