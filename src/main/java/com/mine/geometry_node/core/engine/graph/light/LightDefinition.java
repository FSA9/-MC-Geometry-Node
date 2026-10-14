package com.mine.geometry_node.core.engine.graph.light;

import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/** Immutable, validated description shared by graph execution, transport and rendering. */
public record LightDefinition(String type, Vec3 color, float intensity, float range,
                              float coneAngle, float edgeSoftness, Vec3 position, Vec3 rotation, LightConfig config) {
    public LightDefinition(String type, Vec3 color, float intensity, float range,
                           float coneAngle, float edgeSoftness, LightConfig config) {
        this(type, color, intensity, range, coneAngle, edgeSoftness, Vec3.ZERO, Vec3.ZERO, config);
    }
    public LightDefinition {
        if (!"spot".equals(type) && !"point".equals(type) && !"area".equals(type))
            throw new IllegalArgumentException("Unsupported light type: " + type);
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(config, "config");
        if (!finite(position) || !finite(rotation)) throw new IllegalArgumentException("Nonfinite light transform");
        if (!finite(color) || color.x < 0 || color.y < 0 || color.z < 0
                || color.x > 1 || color.y > 1 || color.z > 1)
            throw new IllegalArgumentException("Light color must be in [0, 1]");
        bounded(intensity, 0, 1024, "intensity");
        bounded(range, 0.01f, 32, "range");
        bounded(coneAngle, 1, 179, "cone angle");
        bounded(edgeSoftness, 0, 1, "edge softness");
    }

    public static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public LightDefinition withConfig(LightConfig value) {
        return new LightDefinition(type, color, intensity, range, coneAngle, edgeSoftness, position, rotation, value);
    }

    /** Radius enclosing every emitting point, for conservative visibility and snapshot bounds. */
    public float emitterRadius() {
        return "area".equals(type) ? 0.5f * (float) Math.hypot(config.area().width(), config.area().height())
                : config.shadow().sourceSize();
    }

    private static void bounded(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Invalid light " + name);
    }
}
