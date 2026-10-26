package com.mine.geometry_node.client.runtime.model.render.lod.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Distance policy selected per mesh-bearing node instance and shared by main and shadow. */
public record ModelLodPolicy(boolean enabled, boolean automatic, int manualLevel,
                             double level1Distance, double level2Distance, double level3Distance,
                             double hysteresisFraction) {
    public static final int LEVEL_COUNT = 4;
    public static final ModelLodPolicy DEFAULT = new ModelLodPolicy(true, 0, 64, 128, 256, 0.1);
    private static final List<Double> TRIANGLE_RATIOS = List.of(1.0, 0.8, 0.6, 0.3);
    private static volatile ModelLodPolicy active = DEFAULT;

    public ModelLodPolicy(boolean automatic, int manualLevel, double level1Distance,
                          double level2Distance, double level3Distance, double hysteresisFraction) {
        this(true, automatic, manualLevel, level1Distance, level2Distance, level3Distance, hysteresisFraction);
    }

    public ModelLodPolicy {
        requireLevel(manualLevel);
        if (!Double.isFinite(level1Distance) || !Double.isFinite(level2Distance)
                || !Double.isFinite(level3Distance) || level1Distance <= 0
                || level2Distance <= level1Distance || level3Distance <= level2Distance)
            throw new IllegalArgumentException("LOD distances must be finite, positive, and increasing");
        if (!Double.isFinite(hysteresisFraction) || hysteresisFraction < 0 || hysteresisFraction >= 1)
            throw new IllegalArgumentException("LOD hysteresis must be in [0,1)");
    }

    public static ModelLodPolicy active() { return active; }
    public static void setActive(ModelLodPolicy policy) { active = Objects.requireNonNull(policy, "policy"); }

    public ModelLodPolicy withAuto() {
        return new ModelLodPolicy(true, manualLevel, level1Distance, level2Distance, level3Distance, hysteresisFraction);
    }

    public ModelLodPolicy withLevel(int level) {
        return new ModelLodPolicy(false, level, level1Distance, level2Distance, level3Distance, hysteresisFraction);
    }

    public ModelLodPolicy withEnabled(boolean enabled) {
        return new ModelLodPolicy(enabled, automatic, manualLevel, level1Distance, level2Distance,
                level3Distance, hysteresisFraction);
    }

    public ModelLodPolicy withDistances(double first, double second, double third) {
        return new ModelLodPolicy(enabled, automatic, manualLevel, first, second, third, hysteresisFraction);
    }

    public ModelLodPolicy withHysteresis(double fraction) {
        return new ModelLodPolicy(enabled, automatic, manualLevel, level1Distance, level2Distance,
                level3Distance, fraction);
    }

    /** Initial selection without history. Distance is measured to the transformed object bounds. */
    public int choose(double distanceToModelBounds) {
        if (!enabled) return 0;
        if (!automatic) return manualLevel;
        if (!validDistance(distanceToModelBounds)) return 0;
        if (distanceToModelBounds >= level3Distance) return 3;
        if (distanceToModelBounds >= level2Distance) return 2;
        return distanceToModelBounds >= level1Distance ? 1 : 0;
    }

    /** Pass -1 without history; callers retain history per object in each model instance. */
    public int choose(double distanceToModelBounds, int previousLevel) {
        if (!enabled) return 0;
        if (!automatic || previousLevel < 0 || previousLevel >= LEVEL_COUNT)
            return choose(distanceToModelBounds);
        if (!validDistance(distanceToModelBounds)) return 0;
        int level = previousLevel;
        while (level < LEVEL_COUNT - 1 && distanceToModelBounds >= threshold(level + 1) * (1 + hysteresisFraction))
            level++;
        while (level > 0 && distanceToModelBounds < threshold(level) * (1 - hysteresisFraction))
            level--;
        return level;
    }

    public static double triangleRatio(int level) {
        requireLevel(level);
        return TRIANGLE_RATIOS.get(level);
    }

    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("mode", automatic ? "AUTO" : "MANUAL");
        result.put("manualLevel", automatic ? -1 : manualLevel);
        result.put("distanceThresholds", List.of(level1Distance, level2Distance, level3Distance));
        result.put("hysteresisFraction", hysteresisFraction);
        result.put("targetTriangleRatios", TRIANGLE_RATIOS);
        return Map.copyOf(result);
    }

    private double threshold(int level) {
        return switch (level) {
            case 1 -> level1Distance;
            case 2 -> level2Distance;
            case 3 -> level3Distance;
            default -> throw new IllegalArgumentException("LOD threshold level must be in [1,3]");
        };
    }

    static void requireLevel(int level) {
        if (level < 0 || level >= LEVEL_COUNT) throw new IllegalArgumentException("LOD level must be in [0,3]");
    }

    private static boolean validDistance(double distance) { return Double.isFinite(distance) && distance >= 0; }
}
