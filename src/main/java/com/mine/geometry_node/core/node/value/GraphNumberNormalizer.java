package com.mine.geometry_node.core.node.value;

/** Converts arbitrary {@link Number} implementations into graph-supported numeric values. */
public final class GraphNumberNormalizer {
    private GraphNumberNormalizer() {
    }

    public static Number normalize(Number value) {
        if (value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short) {
            return value.intValue();
        }

        String text = value.toString();
        try {
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return Double.parseDouble(text);
            }
            long integral = Long.parseLong(text);
            if (integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE) {
                return (int) integral;
            }
            return integral;
        } catch (NumberFormatException ignored) {
            return value.doubleValue();
        }
    }
}
