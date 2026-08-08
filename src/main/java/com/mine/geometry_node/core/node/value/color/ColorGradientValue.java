package com.mine.geometry_node.core.node.value.color;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor-facing color ramp data. Stops are normalized in the 0..1 domain.
 */
public record ColorGradientValue(
        List<ColorStop> stops,
        ColorMode mode,
        Interpolation interpolation,
        int selectedIndex
) {
    public static final String KEY_STOPS = "stops";
    public static final String KEY_MODE = "mode";
    public static final String KEY_INTERPOLATION = "interpolation";
    public static final String KEY_SELECTED_INDEX = "selected_index";

    public static final ColorGradientValue DEFAULT = new ColorGradientValue(
            List.of(
                    new ColorStop(0.0f, ColorValue.BLACK),
                    new ColorStop(1.0f, ColorValue.WHITE)
            ),
            ColorMode.RGB,
            Interpolation.LINEAR,
            0
    );

    public ColorGradientValue {
        List<ColorStop> normalizedStops = new ArrayList<>();
        if (stops != null) {
            for (ColorStop stop : stops) {
                if (stop != null) {
                    normalizedStops.add(stop);
                }
            }
        }
        if (normalizedStops.isEmpty()) {
            normalizedStops.addAll(DEFAULT.stops);
        }
        normalizedStops.sort(Comparator.comparingDouble(ColorStop::position));
        stops = List.copyOf(normalizedStops);
        mode = mode != null ? mode : ColorMode.RGB;
        interpolation = normalizeInterpolation(mode, interpolation);
        selectedIndex = clamp(selectedIndex, 0, Math.max(0, stops.size() - 1));
    }

    public static ColorGradientValue from(@Nullable Object value) {
        if (value instanceof ColorGradientValue gradient) {
            return gradient;
        }
        if (value instanceof Map<?, ?> map) {
            List<ColorStop> stops = stopsFrom(map.get(KEY_STOPS));
            ColorMode mode = ColorMode.from(map.get(KEY_MODE));
            Interpolation interpolation = Interpolation.from(map.get(KEY_INTERPOLATION));
            int selectedIndex = intValue(map.get(KEY_SELECTED_INDEX), 0);
            return new ColorGradientValue(stops, mode, interpolation, selectedIndex);
        }
        return DEFAULT;
    }

    public ColorValue sample(float factor) {
        float x = clamp01(factor);
        if (stops.isEmpty()) {
            return ColorValue.WHITE;
        }
        ColorStop first = stops.get(0);
        if (stops.size() == 1 || x <= first.position()) {
            return first.color();
        }
        ColorStop last = stops.get(stops.size() - 1);
        if (x >= last.position()) {
            return last.color();
        }

        for (int i = 1; i < stops.size(); i++) {
            ColorStop right = stops.get(i);
            if (x <= right.position()) {
                int leftIndex = i - 1;
                ColorStop left = stops.get(i - 1);
                float span = Math.max(0.000001f, right.position() - left.position());
                float t = clamp01((x - left.position()) / span);
                return interpolate(leftIndex, i, t);
            }
        }
        return last.color();
    }

    public ColorGradientValue withStops(List<ColorStop> newStops, int newSelectedIndex) {
        return new ColorGradientValue(newStops, mode, interpolation, newSelectedIndex);
    }

    public ColorGradientValue withMode(ColorMode newMode) {
        return new ColorGradientValue(stops, newMode, interpolation, selectedIndex);
    }

    public ColorGradientValue withInterpolation(Interpolation newInterpolation) {
        return new ColorGradientValue(stops, mode, newInterpolation, selectedIndex);
    }

    public ColorGradientValue withSelectedIndex(int newSelectedIndex) {
        return new ColorGradientValue(stops, mode, interpolation, newSelectedIndex);
    }

    public ColorGradientValue withSelectedPosition(float position) {
        if (stops.isEmpty()) {
            return this;
        }
        List<ColorStop> next = new ArrayList<>(stops);
        int originalIndex = clamp(selectedIndex, 0, next.size() - 1);
        ColorStop selected = next.get(originalIndex);
        next.set(originalIndex, new ColorStop(position, selected.color()));
        next.sort(Comparator.comparingDouble(ColorStop::position));
        int sortedIndex = next.indexOf(new ColorStop(position, selected.color()));
        return new ColorGradientValue(next, mode, interpolation, Math.max(0, sortedIndex));
    }

    public ColorGradientValue withSelectedColor(ColorValue color) {
        if (stops.isEmpty()) {
            return this;
        }
        List<ColorStop> next = new ArrayList<>(stops);
        int index = clamp(selectedIndex, 0, next.size() - 1);
        next.set(index, new ColorStop(next.get(index).position(), color != null ? color : ColorValue.WHITE));
        return new ColorGradientValue(next, mode, interpolation, index);
    }

    public ColorGradientValue addStopAt(float position) {
        float p = clamp01(position);
        List<ColorStop> next = new ArrayList<>(stops);
        ColorStop stop = new ColorStop(p, sample(p));
        next.add(stop);
        next.sort(Comparator.comparingDouble(ColorStop::position));
        return new ColorGradientValue(next, mode, interpolation, next.indexOf(stop));
    }

    public ColorGradientValue removeSelectedStop() {
        if (stops.size() <= 2) {
            return this;
        }
        List<ColorStop> next = new ArrayList<>(stops);
        int index = clamp(selectedIndex, 0, next.size() - 1);
        next.remove(index);
        return new ColorGradientValue(next, mode, interpolation, Math.min(index, next.size() - 1));
    }

    public ColorGradientValue reversed() {
        List<ColorStop> next = new ArrayList<>();
        for (ColorStop stop : stops) {
            next.add(new ColorStop(1.0f - stop.position(), stop.color()));
        }
        int nextSelected = selectedIndex;
        if (!stops.isEmpty()) {
            nextSelected = stops.size() - 1 - clamp(selectedIndex, 0, stops.size() - 1);
        }
        return new ColorGradientValue(next, mode, interpolation, nextSelected);
    }

    public ColorGradientValue evenlyDistributed() {
        if (stops.size() <= 1) {
            return this;
        }
        List<ColorStop> next = new ArrayList<>();
        float denom = stops.size() - 1;
        for (int i = 0; i < stops.size(); i++) {
            next.add(new ColorStop(i / denom, stops.get(i).color()));
        }
        return new ColorGradientValue(next, mode, interpolation, selectedIndex);
    }

    public Map<String, Object> toMap() {
        List<Map<String, Object>> stopMaps = new ArrayList<>();
        for (ColorStop stop : stops) {
            stopMaps.add(stop.toMap());
        }
        return Map.of(
                KEY_STOPS, stopMaps,
                KEY_MODE, mode.id,
                KEY_INTERPOLATION, interpolation.id,
                KEY_SELECTED_INDEX, selectedIndex
        );
    }

    private ColorValue interpolate(int leftIndex, int rightIndex, float t) {
        ColorStop left = stops.get(leftIndex);
        ColorStop right = stops.get(rightIndex);
        if (interpolation == Interpolation.CONSTANT) {
            return left.color();
        }
        if (interpolation == Interpolation.EASE) {
            t = t * t * (3.0f - 2.0f * t);
        }
        if (interpolation == Interpolation.B_SPLINE) {
            t = t * t * (3.0f - 2.0f * t);
            t = t * t * (3.0f - 2.0f * t);
        }

        return switch (mode) {
            case HSV -> interpolateHsvLike(left.color(), right.color(), t, true);
            case HSL -> interpolateHsvLike(left.color(), right.color(), t, false);
            default -> interpolateRgb(leftIndex, rightIndex, t);
        };
    }

    private ColorValue interpolateRgb(int leftIndex, int rightIndex, float t) {
        ColorValue a = stops.get(leftIndex).color();
        ColorValue b = stops.get(rightIndex).color();
        if (interpolation == Interpolation.RAW || interpolation == Interpolation.B_SPLINE) {
            ColorValue p0 = stops.get(Math.max(0, leftIndex - 1)).color();
            ColorValue p3 = stops.get(Math.min(stops.size() - 1, rightIndex + 1)).color();
            return interpolation == Interpolation.B_SPLINE
                    ? bspline(p0, a, b, p3, t)
                    : cardinal(p0, a, b, p3, t);
        }
        return new ColorValue(
                lerp(a.r(), b.r(), t),
                lerp(a.g(), b.g(), t),
                lerp(a.b(), b.b(), t),
                lerp(a.a(), b.a(), t)
        );
    }

    private static ColorValue cardinal(ColorValue p0, ColorValue p1, ColorValue p2, ColorValue p3, float t) {
        return new ColorValue(
                cardinal(p0.r(), p1.r(), p2.r(), p3.r(), t),
                cardinal(p0.g(), p1.g(), p2.g(), p3.g(), t),
                cardinal(p0.b(), p1.b(), p2.b(), p3.b(), t),
                cardinal(p0.a(), p1.a(), p2.a(), p3.a(), t)
        );
    }

    private static ColorValue bspline(ColorValue p0, ColorValue p1, ColorValue p2, ColorValue p3, float t) {
        return new ColorValue(
                bspline(p0.r(), p1.r(), p2.r(), p3.r(), t),
                bspline(p0.g(), p1.g(), p2.g(), p3.g(), t),
                bspline(p0.b(), p1.b(), p2.b(), p3.b(), t),
                bspline(p0.a(), p1.a(), p2.a(), p3.a(), t)
        );
    }

    private static float cardinal(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2.0f * p1)
                + (-p0 + p2) * t
                + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
                + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
    }

    private static float bspline(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return ((-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3
                + (3.0f * p0 - 6.0f * p1 + 3.0f * p2) * t2
                + (-3.0f * p0 + 3.0f * p2) * t
                + p0 + 4.0f * p1 + p2) / 6.0f;
    }

    private ColorValue interpolateHsvLike(ColorValue a, ColorValue b, float t, boolean hsv) {
        float[] ca = hsv ? a.toHsv() : a.toHsl();
        float[] cb = hsv ? b.toHsv() : b.toHsl();
        float hue = interpolateHue(ca[0], cb[0], t, interpolation);
        float c1 = lerp(ca[1], cb[1], t);
        float c2 = lerp(ca[2], cb[2], t);
        float alpha = lerp(a.a(), b.a(), t);
        return hsv ? ColorValue.fromHsv(hue, c1, c2, alpha) : ColorValue.fromHsl(hue, c1, c2, alpha);
    }

    private static float interpolateHue(float from, float to, float t, Interpolation interpolation) {
        float delta = to - from;
        if (interpolation == Interpolation.HUE_CLOCKWISE) {
            if (delta > 0.0f) delta -= 1.0f;
        } else if (interpolation == Interpolation.HUE_COUNTER_CLOCKWISE) {
            if (delta < 0.0f) delta += 1.0f;
        } else if (interpolation == Interpolation.HUE_FAR) {
            if (Math.abs(delta) < 0.5f) {
                delta += delta >= 0.0f ? -1.0f : 1.0f;
            }
        } else {
            if (delta > 0.5f) delta -= 1.0f;
            if (delta < -0.5f) delta += 1.0f;
        }
        return wrap01(from + delta * t);
    }

    private static Interpolation normalizeInterpolation(ColorMode mode, Interpolation interpolation) {
        Interpolation fallback = mode == ColorMode.RGB ? Interpolation.LINEAR : Interpolation.HUE_NEAR;
        Interpolation value = interpolation != null ? interpolation : fallback;
        return value.isValidFor(mode) ? value : fallback;
    }

    private static List<ColorStop> stopsFrom(Object value) {
        if (!(value instanceof List<?> list)) {
            return DEFAULT.stops;
        }
        List<ColorStop> result = new ArrayList<>();
        for (Object item : list) {
            ColorStop stop = ColorStop.from(item);
            if (stop != null) {
                result.add(stop);
            }
        }
        return result.isEmpty() ? DEFAULT.stops : result;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static float floatValue(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float wrap01(float value) {
        float wrapped = value - (float) Math.floor(value);
        return wrapped < 0.0f ? wrapped + 1.0f : wrapped;
    }

    public record ColorStop(float position, ColorValue color) {
        public ColorStop {
            position = clamp01(position);
            color = color != null ? color : ColorValue.WHITE;
        }

        @Nullable
        static ColorStop from(Object value) {
            if (value instanceof ColorStop stop) {
                return stop;
            }
            if (value instanceof Map<?, ?> map) {
                float position = floatValue(map.get("position"), 0.0f);
                ColorValue color = ColorValue.from(map.get("color"));
                if (color == null) {
                    color = ColorValue.from(map);
                }
                return new ColorStop(position, color != null ? color : ColorValue.WHITE);
            }
            return null;
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "position", position,
                    "color", Map.of(
                            "r", color.r(),
                            "g", color.g(),
                            "b", color.b(),
                            "a", color.a()
                    )
            );
        }
    }

    public enum ColorMode {
        RGB("RGB"),
        HSV("HSV"),
        HSL("HSL");

        public final String id;

        ColorMode(String id) {
            this.id = id;
        }

        public static ColorMode from(Object value) {
            if (value instanceof ColorMode mode) {
                return mode;
            }
            String text = value != null ? String.valueOf(value).trim().toUpperCase(Locale.ROOT) : "";
            return switch (text) {
                case "HSV" -> HSV;
                case "HSL" -> HSL;
                default -> RGB;
            };
        }
    }

    public enum Interpolation {
        EASE("ease", "缓动"),
        RAW("raw", "原始"),
        LINEAR("linear", "线性"),
        B_SPLINE("b_spline", "B样条"),
        CONSTANT("constant", "常值"),
        HUE_NEAR("hue_near", "近端"),
        HUE_FAR("hue_far", "远端"),
        HUE_CLOCKWISE("hue_clockwise", "顺时针"),
        HUE_COUNTER_CLOCKWISE("hue_counter_clockwise", "逆时针");

        public final String id;
        public final String displayName;

        Interpolation(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public boolean isValidFor(ColorMode mode) {
            boolean hueMode = mode == ColorMode.HSV || mode == ColorMode.HSL;
            return hueMode == isHueInterpolation();
        }

        public boolean isHueInterpolation() {
            return this == HUE_NEAR || this == HUE_FAR || this == HUE_CLOCKWISE || this == HUE_COUNTER_CLOCKWISE;
        }

        public static Interpolation from(Object value) {
            if (value instanceof Interpolation interpolation) {
                return interpolation;
            }
            String rawText = value != null ? String.valueOf(value).trim() : "";
            String text = rawText.toLowerCase(Locale.ROOT);
            for (Interpolation interpolation : values()) {
                if (interpolation.id.equals(text) || interpolation.displayName.equals(rawText)) {
                    return interpolation;
                }
            }
            return LINEAR;
        }
    }
}
