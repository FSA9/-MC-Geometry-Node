package com.mine.geometry_node.client.runtime.model.render.lod.cluster;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime policy for the CPU cluster hierarchy. Main and Shadow always consume the same
 * hierarchy; only their screen-space error budgets differ.
 */
public record ModelCpuClusterPolicy(
        boolean enabled,
        double mainTargetPixelError,
        double shadowTargetPixelError,
        int maxTraversalNodes) {
    public static final int REVISION = 1;
    public static final ModelCpuClusterPolicy DEFAULT = new ModelCpuClusterPolicy(true, 1.0, 2.0, 65_536);
    private static volatile ModelCpuClusterPolicy active = fromSystemProperties();

    public ModelCpuClusterPolicy {
        if (!Double.isFinite(mainTargetPixelError) || mainTargetPixelError <= 0.0
                || !Double.isFinite(shadowTargetPixelError) || shadowTargetPixelError <= 0.0)
            throw new IllegalArgumentException("CPU cluster pixel errors must be finite and positive");
        if (maxTraversalNodes < 1 || maxTraversalNodes > 1_000_000)
            throw new IllegalArgumentException("maxTraversalNodes must be in [1,1000000]");
    }

    public static ModelCpuClusterPolicy active() { return active; }
    public static void setActive(ModelCpuClusterPolicy policy) { active = Objects.requireNonNull(policy, "policy"); }
    public ModelCpuClusterPolicy withEnabled(boolean value) {
        return new ModelCpuClusterPolicy(value, mainTargetPixelError, shadowTargetPixelError, maxTraversalNodes);
    }
    public ModelCpuClusterPolicy withMainTargetPixelError(double value) {
        return new ModelCpuClusterPolicy(enabled, value, shadowTargetPixelError, maxTraversalNodes);
    }
    public ModelCpuClusterPolicy withShadowTargetPixelError(double value) {
        return new ModelCpuClusterPolicy(enabled, mainTargetPixelError, value, maxTraversalNodes);
    }

    public static ModelCpuClusterPolicy fromSystemProperties() {
        boolean enabled = Boolean.parseBoolean(System.getProperty("geometry_node.model.cpu_cluster.enabled",
                Boolean.toString(DEFAULT.enabled)));
        double main = positiveProperty("geometry_node.model.cpu_cluster.main_pixel_error", DEFAULT.mainTargetPixelError);
        double shadow = positiveProperty("geometry_node.model.cpu_cluster.shadow_pixel_error", DEFAULT.shadowTargetPixelError);
        int max = integerProperty("geometry_node.model.cpu_cluster.max_traversal_nodes", DEFAULT.maxTraversalNodes);
        if (max < 1 || max > 1_000_000) max = DEFAULT.maxTraversalNodes;
        return new ModelCpuClusterPolicy(enabled, main, shadow, max);
    }

    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revision", REVISION);
        result.put("enabled", enabled);
        result.put("mainTargetPixelError", mainTargetPixelError);
        result.put("shadowTargetPixelError", shadowTargetPixelError);
        result.put("maxTraversalNodes", maxTraversalNodes);
        result.put("status", enabled ? "CONFIGURED" : "DISABLED");
        result.put("implementation", "CPU cluster hierarchy traversal shared by main and Shadow; BVH retains full source geometry; BLEND retains full BSP geometry");
        result.put("gpuTraversal", "NOT_USED");
        return Map.copyOf(result);
    }

    private static double positiveProperty(String name, double fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed > 0.0 ? parsed : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    private static int integerProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; }
    }
}
