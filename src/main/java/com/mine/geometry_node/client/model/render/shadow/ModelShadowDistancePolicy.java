package com.mine.geometry_node.client.model.render.shadow;

import java.util.Map;

/** Model solar-shadow switch and caster distance, independent of BVH lighting. */
public final class ModelShadowDistancePolicy {
    public static final double DEFAULT_MAX_DISTANCE = 512.0;
    private static boolean enabled = true;
    private static boolean automatic = true;
    private static float manualTolerance;
    private static double maxDistance = initialDistance();

    private ModelShadowDistancePolicy() {}

    public record Decision(double distance, double quality, int tier, int updateInterval,
                           boolean beyondMax, String reason) {
        public boolean disabled() { return beyondMax || quality <= 0.0; }
    }

    public static boolean enabled() { return enabled; }
    public static boolean automatic() { return automatic; }
    public static float manualTolerance() { return manualTolerance; }
    public static double maxDistance() { return maxDistance; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void setAutomatic() { automatic = true; enabled = true; }

    public static void setTolerance(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 2)
            throw new IllegalArgumentException("shadow tolerance must be in [0,2] texels");
        manualTolerance = value;
        automatic = false;
    }

    public static float tolerance(double distance) {
        if (!automatic) return manualTolerance;
        if (!Double.isFinite(distance) || distance <= 32) return 0;
        return (float) Math.min(2, (distance - 32) / 112);
    }

    public static void setMaxDistance(double value) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException("shadow distance must be finite and >= 0");
        maxDistance = value;
    }

    public static void reset() { enabled = true; automatic = true; manualTolerance = 0; maxDistance = DEFAULT_MAX_DISTANCE; }

    public static Decision evaluate(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) distance = Double.POSITIVE_INFINITY;
        boolean beyond = distance >= maxDistance;
        boolean draw = enabled && !beyond;
        return new Decision(distance, draw ? 1.0 : 0.0, draw ? 0 : 3,
                draw ? 1 : Integer.MAX_VALUE, beyond, !enabled ? "DISABLED" : beyond ? "DISTANCE" : "NONE");
    }

    public static String tierName(int tier) { return tier == 0 ? "FULL" : "DISABLED"; }

    public static Map<String, Object> report() {
        return Map.of("enabled", enabled, "maxDistance", maxDistance,
                "automatic", automatic, "manualTolerance", manualTolerance,
                "distanceReference", "nearest point of transformed whole-model AABB to main camera",
                "updateInterval", 1);
    }

    private static double initialDistance() {
        try {
            double value = Double.parseDouble(System.getProperty("geometry_node.model.shadow_max_distance", Double.toString(DEFAULT_MAX_DISTANCE)));
            return Double.isFinite(value) && value >= 0 ? value : DEFAULT_MAX_DISTANCE;
        } catch (RuntimeException ignored) { return DEFAULT_MAX_DISTANCE; }
    }
}
