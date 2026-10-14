package com.mine.geometry_node.core.node.value.light;

import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.UUID;

/** Immutable, independently composable light settings. Distances are in blocks. */
public record LightConfig(Transform transform, Shadow shadow, Volume volume, Projection projection,
                          Quality quality, Lifecycle lifecycle, boolean sourceVisible, Area area) {
    public LightConfig(Transform transform, Shadow shadow, Volume volume, Projection projection,
                       Quality quality, Lifecycle lifecycle, boolean sourceVisible) {
        this(transform, shadow, volume, projection, quality, lifecycle, sourceVisible, Area.DEFAULT);
    }
    public LightConfig(Transform transform, Shadow shadow, Volume volume, Projection projection,
                       Quality quality, Lifecycle lifecycle) {
        this(transform, shadow, volume, projection, quality, lifecycle, false);
    }
    public static final LightConfig DEFAULT = new LightConfig(Transform.DEFAULT, Shadow.DEFAULT,
            Volume.DEFAULT, Projection.DEFAULT, Quality.DEFAULT, Lifecycle.DEFAULT);
    public LightConfig {
        Objects.requireNonNull(transform); Objects.requireNonNull(shadow); Objects.requireNonNull(volume);
        Objects.requireNonNull(projection); Objects.requireNonNull(quality); Objects.requireNonNull(lifecycle);
        // Saved configurations predating rectangular emitters have no area component.
        if (area == null) area = Area.DEFAULT;
    }
    public LightConfig withTransform(Transform v) { return new LightConfig(v, shadow, volume, projection, quality, lifecycle, sourceVisible, area); }
    public LightConfig withShadow(Shadow v) { return new LightConfig(transform, v, volume, projection, quality, lifecycle, sourceVisible, area); }
    public LightConfig withVolume(Volume v) { return new LightConfig(transform, shadow, v, projection, quality, lifecycle, sourceVisible, area); }
    public LightConfig withProjection(Projection v) { return new LightConfig(transform, shadow, volume, v, quality, lifecycle, sourceVisible, area); }
    public LightConfig withQuality(Quality v) { return new LightConfig(transform, shadow, volume, projection, v, lifecycle, sourceVisible, area); }
    public LightConfig withLifecycle(Lifecycle v) { return new LightConfig(transform, shadow, volume, projection, quality, v, sourceVisible, area); }
    public LightConfig withSourceVisible(boolean v) { return new LightConfig(transform, shadow, volume, projection, quality, lifecycle, v, area); }
    public LightConfig withArea(Area v) { return new LightConfig(transform, shadow, volume, projection, quality, lifecycle, sourceVisible, v); }

    public record Area(float width, float height) {
        public static final Area DEFAULT = new Area(1, 1);
        public Area { Checks.range(width, 0.01f, 8, "area width"); Checks.range(height, 0.01f, 8, "area height"); }
    }

    public record Transform(UUID entityId, Vec3 position, Vec3 rotation, boolean followView, boolean eyeAnchor) {
        public static final Transform DEFAULT = new Transform(null, Vec3.ZERO, Vec3.ZERO, true, true);
        public Transform { Checks.vector(position); Checks.vector(rotation); }
    }
    public record Shadow(boolean enabled, float sourceSize) {
        public static final Shadow DEFAULT = new Shadow(true, 0.02f);
        public Shadow { Checks.range(sourceSize, 0, 4, "sourceSize"); }
    }
    public record Volume(String mode, float strength, float density, float noiseStrength,
                         float noiseScale, Vec3 velocity, int seed) {
        public static final Volume DEFAULT = new Volume("off", 1, 0.02f, 0.5f, 1, Vec3.ZERO, 0);
        public Volume {
            if (!"off".equals(mode) && !"uniform".equals(mode) && !"noise".equals(mode))
                throw new IllegalArgumentException("Invalid volume mode: " + mode);
            Checks.range(strength, 0, 100, "strength"); Checks.range(density, 0, 10, "density");
            Checks.range(noiseStrength, 0, 1, "noiseStrength"); Checks.range(noiseScale, 0.001f, 1024, "noiseScale"); Checks.vector(velocity);
        }
    }
    public record Projection(String path, boolean colored, float rotation, float scale) {
        public static final Projection DEFAULT = new Projection("", false, 0, 1);
        public Projection {
            Objects.requireNonNull(path);
            path = path.trim();
            if (path.length() > 1024) throw new IllegalArgumentException("Projection path exceeds 1024 characters");
            if (!Float.isFinite(rotation)) throw new IllegalArgumentException("Nonfinite projection rotation");
            Checks.range(scale, 0.001f, 1024, "scale");
        }
    }
    public record Quality(float shadowResolution, int shadowSamples, int volumeSamples, float volumeResolution,
                          int volumeEmitterSamples) {
        public Quality(float shadowResolution, int shadowSamples, int volumeSamples, float volumeResolution) {
            this(shadowResolution, shadowSamples, volumeSamples, volumeResolution, Math.max(4, shadowSamples));
        }
        public static final Quality DEFAULT = new Quality(1, 4, 24, 0.5f);
        public Quality {
            Checks.range(shadowResolution, 0.1f, 1, "shadowResolution"); Checks.range(shadowSamples, 1, 16, "shadowSamples");
            Checks.range(volumeSamples, 4, 64, "volumeSamples"); Checks.range(volumeResolution, 0.1f, 1, "volumeResolution");
            Checks.range(volumeEmitterSamples, 4, 16, "volumeEmitterSamples");
            shadowSamples = Math.max(4, shadowSamples);
        }
    }
    public record Lifecycle(int durationTicks, boolean enabled) {
        public static final Lifecycle DEFAULT = new Lifecycle(0, true);
        public Lifecycle { if (durationTicks < 0) throw new IllegalArgumentException("Negative duration"); }
    }
    // Nested defaults must validate without triggering initialization of LightConfig.DEFAULT.
    private static final class Checks {
        private static void vector(Vec3 v) {
            Objects.requireNonNull(v);
            if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z))
                throw new IllegalArgumentException("Nonfinite light vector");
        }
        private static void range(float v, float min, float max, String name) {
            if (!Float.isFinite(v) || v < min || v > max)
                throw new IllegalArgumentException(name + " must be in " + min + ".." + max);
        }
    }
}
