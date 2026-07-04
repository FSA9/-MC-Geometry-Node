package com.mine.geometry_node.client.ui.viewport.node.UIHints;

import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import java.util.Locale;

/**
 * Shared numeric input rules for node inline controls.
 */
public record NumericInputSpec(
        PortType type,
        Double min,
        Double max,
        double step,
        int decimals,
        boolean showArrows
) {
    private static final double DEFAULT_FLOAT_STEP = 0.001d;
    private static final int DEFAULT_FLOAT_DECIMALS = 3;

    public static boolean supports(PortType type) {
        return type == PortType.INTEGER || type == PortType.FLOAT;
    }

    public static NumericInputSpec from(PortRow row, PortType type) {
        double defaultStep = type == PortType.INTEGER ? 1.0d : DEFAULT_FLOAT_STEP;
        int defaultDecimals = type == PortType.INTEGER ? 0 : DEFAULT_FLOAT_DECIMALS;
        double step = Math.abs(hintNumber(row, PortMetaKeys.NUMERIC_STEP, defaultStep).doubleValue());
        if (step <= 0.0d || Double.isNaN(step) || Double.isInfinite(step)) {
            step = defaultStep;
        }

        int decimals = hintInt(row, PortMetaKeys.NUMERIC_DECIMALS, defaultDecimals);
        decimals = Math.max(0, Math.min(6, decimals));

        return new NumericInputSpec(
                type,
                hintDouble(row, PortMetaKeys.NUMERIC_MIN),
                hintDouble(row, PortMetaKeys.NUMERIC_MAX),
                step,
                decimals,
                supports(type)
        );
    }

    public NumericInputSpec withArrows(boolean showArrows) {
        return new NumericInputSpec(type, min, max, step, decimals, showArrows);
    }

    public boolean hasRange() {
        return min != null && max != null && max > min;
    }

    public double valueOrDefault(Object value, Object fallback) {
        Double parsed = parseDouble(value);
        if (parsed == null) {
            parsed = parseDouble(fallback);
        }
        return clamp(parsed != null ? parsed : 0.0d);
    }

    public Object parseManual(String text) {
        Object parsed = UIHintValueBinder.parseText(text, type);
        if (parsed == null) {
            return null;
        }
        return coerce(clamp(numberValue(parsed)));
    }

    public Object coerceDragged(double value) {
        double clamped = clamp(value);
        if (type == PortType.INTEGER) {
            return (int) Math.round(clamped);
        }
        return (float) roundToDecimals(clamped, decimals);
    }

    public Object stepValue(double value, int direction) {
        double delta = direction >= 0 ? step : -step;
        return coerceDragged(value + delta);
    }

    public String display(Object value) {
        if (value == null) {
            return "";
        }
        if (type == PortType.INTEGER && value instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        return value.toString();
    }

    public String displayDragged(Object value) {
        if (value == null) {
            return "";
        }
        if (type == PortType.INTEGER && value instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        if (value instanceof Number number) {
            return String.format(Locale.US, "%." + decimals + "f", number.doubleValue());
        }
        return value.toString();
    }

    public float progress(Object value) {
        if (!hasRange()) {
            return 0.0f;
        }
        double current = valueOrDefault(value, null);
        return (float) ((current - min) / (max - min));
    }

    private Object coerce(double value) {
        if (type == PortType.INTEGER) {
            return (int) Math.round(value);
        }
        return (float) value;
    }

    private double clamp(double value) {
        double result = value;
        if (min != null) {
            result = Math.max(min, result);
        }
        if (max != null) {
            result = Math.min(max, result);
        }
        return result;
    }

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    private static double roundToDecimals(double value, int decimals) {
        double factor = Math.pow(10.0d, decimals);
        return Math.round(value * factor) / factor;
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Double hintDouble(PortRow row, com.mine.geometry_node.core.node.meta.MetaKey<Number> key) {
        Number value = hintNumber(row, key, null);
        return value != null ? value.doubleValue() : null;
    }

    private static Number hintNumber(PortRow row, com.mine.geometry_node.core.node.meta.MetaKey<Number> key, Number fallback) {
        if (row == null || row.hintParams() == null) {
            return fallback;
        }
        Object value = row.hintParams().get(key);
        return value instanceof Number number ? number : fallback;
    }

    private static int hintInt(PortRow row, com.mine.geometry_node.core.node.meta.MetaKey<Integer> key, int fallback) {
        if (row == null || row.hintParams() == null) {
            return fallback;
        }
        Object value = row.hintParams().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
