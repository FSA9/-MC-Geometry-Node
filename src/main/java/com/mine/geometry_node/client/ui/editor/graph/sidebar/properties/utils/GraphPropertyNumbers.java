package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils;

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
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
