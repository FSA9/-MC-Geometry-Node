package com.mine.geometry_node.client.ui.components.integration.graph.properties.utils;

/**
 * Shared numeric normalization for editable graph-property values.
 */
public final class GraphPropertyNumbers {
    private GraphPropertyNumbers() {
    }

    public static double parseNonNegativeDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? Math.max(0.0, parsed) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static String format(double value) {
        // The upper bound is exclusive: Long.MAX_VALUE rounds to 2^63 as a double.
        return value >= -0x1.0p63 && value < 0x1.0p63 && value == Math.rint(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
