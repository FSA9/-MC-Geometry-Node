package com.mine.geometry_node.core.engine.system.scene;

import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.UUID;

/** Immutable authored data. Render resources and graph-owned runtime objects never enter this record. */
public record SceneObject(UUID id, String typeId, String name, Vec3 position, Vec3 color,
                          float intensity, float range, boolean enabled, long revision) {
    public static final String POINT_LIGHT = "point_light";
    public SceneObject {
        Objects.requireNonNull(id);
        Objects.requireNonNull(typeId);
        Objects.requireNonNull(name);
        if (typeId.isBlank() || typeId.length() > 64) throw new IllegalArgumentException("Invalid scene object type ID");
        if (name.isBlank() || name.length() > 96) throw new IllegalArgumentException("Name must contain 1–96 characters");
        if (revision < 0) throw new IllegalArgumentException("Invalid object revision");
        if (!LightDefinition.finite(position) || Math.abs(position.x) > 30_000_000
                || Math.abs(position.z) > 30_000_000 || Math.abs(position.y) > 30_000_000)
            throw new IllegalArgumentException("Invalid scene position");
        // Use the same validated light contract as transport and the real renderer.
        new LightDefinition("point", color, intensity, range, 60, 0.25f, position, Vec3.ZERO, LightConfig.DEFAULT);
    }
    public static SceneObject pointLight(UUID id, Vec3 position) {
        return new SceneObject(id, POINT_LIGHT, "Point Light", position, new Vec3(1, 1, 1), 3, 16, true, 0);
    }
    public SceneObject withPosition(Vec3 value) {
        return new SceneObject(id, typeId, name, value, color, intensity, range, enabled, revision);
    }
    public SceneObject withProperties(String name, Vec3 color, float intensity, float range, boolean enabled) {
        return new SceneObject(id, typeId, name, position, color, intensity, range, enabled, revision);
    }
    public SceneObject withRevision(long value) {
        return new SceneObject(id, typeId, name, position, color, intensity, range, enabled, value);
    }
    public boolean sameContent(SceneObject other) {
        return other != null && withRevision(0).equals(other.withRevision(0));
    }
}
