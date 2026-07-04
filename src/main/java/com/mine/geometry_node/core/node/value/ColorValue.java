package com.mine.geometry_node.core.node.value;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Linear editor-facing color value. Channels are normalized to 0..1.
 */
public record ColorValue(float r, float g, float b, float a) {
    public static final ColorValue WHITE = new ColorValue(1.0f, 1.0f, 1.0f, 1.0f);
    public static final ColorValue BLACK = new ColorValue(0.0f, 0.0f, 0.0f, 1.0f);

    public ColorValue {
        r = clamp01(r);
        g = clamp01(g);
        b = clamp01(b);
        a = clamp01(a);
    }

    public static ColorValue rgb(float r, float g, float b, float a) {
        return new ColorValue(r, g, b, a);
    }

    public static ColorValue fromHsv(float h, float s, float v, float a) {
        h = wrap01(h);
        s = clamp01(s);
        v = clamp01(v);

        float sector = h * 6.0f;
        int i = (int) Math.floor(sector);
        float f = sector - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        return switch (Math.floorMod(i, 6)) {
            case 0 -> new ColorValue(v, t, p, a);
            case 1 -> new ColorValue(q, v, p, a);
            case 2 -> new ColorValue(p, v, t, a);
            case 3 -> new ColorValue(p, q, v, a);
            case 4 -> new ColorValue(t, p, v, a);
            default -> new ColorValue(v, p, q, a);
        };
    }

    public static ColorValue fromHsl(float h, float s, float l, float a) {
        h = wrap01(h);
        s = clamp01(s);
        l = clamp01(l);

        if (s == 0.0f) {
            return new ColorValue(l, l, l, a);
        }

        float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
        float p = 2.0f * l - q;
        return new ColorValue(
                hueToRgb(p, q, h + 1.0f / 3.0f),
                hueToRgb(p, q, h),
                hueToRgb(p, q, h - 1.0f / 3.0f),
                a
        );
    }

    public static ColorValue fromArgb(int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new ColorValue(r, g, b, a);
    }

    @Nullable
    public static ColorValue from(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ColorValue color) {
            return color;
        }
        if (value instanceof Number number) {
            return fromArgb(number.intValue());
        }
        if (value instanceof Map<?, ?> map) {
            Number r = number(map, "r", "red");
            Number g = number(map, "g", "green");
            Number b = number(map, "b", "blue");
            Number a = number(map, "a", "alpha");
            if (r != null && g != null && b != null) {
                return new ColorValue(r.floatValue(), g.floatValue(), b.floatValue(), a != null ? a.floatValue() : 1.0f);
            }
        }
        return null;
    }

    public int toArgb() {
        int ai = channelToByte(a);
        int ri = channelToByte(r);
        int gi = channelToByte(g);
        int bi = channelToByte(b);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public float[] toHsv() {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0.0f;
        if (delta != 0.0f) {
            if (max == r) {
                h = ((g - b) / delta) % 6.0f;
            } else if (max == g) {
                h = (b - r) / delta + 2.0f;
            } else {
                h = (r - g) / delta + 4.0f;
            }
            h /= 6.0f;
            if (h < 0.0f) {
                h += 1.0f;
            }
        }

        float s = max == 0.0f ? 0.0f : delta / max;
        return new float[]{clamp01(h), clamp01(s), clamp01(max)};
    }

    public float[] toHsl() {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float l = (max + min) * 0.5f;

        float h = 0.0f;
        float s = 0.0f;
        if (delta != 0.0f) {
            s = delta / (1.0f - Math.abs(2.0f * l - 1.0f));
            if (max == r) {
                h = ((g - b) / delta) % 6.0f;
            } else if (max == g) {
                h = (b - r) / delta + 2.0f;
            } else {
                h = (r - g) / delta + 4.0f;
            }
            h /= 6.0f;
            if (h < 0.0f) {
                h += 1.0f;
            }
        }

        return new float[]{clamp01(h), clamp01(s), clamp01(l)};
    }

    private static Number number(Map<?, ?> map, String primary, String fallback) {
        Object value = map.containsKey(primary) ? map.get(primary) : map.get(fallback);
        return value instanceof Number number ? number : null;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }

    private static int channelToByte(float value) {
        return Math.round(clamp01(value) * 255.0f) & 0xFF;
    }

    private static float wrap01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float wrapped = value - (float) Math.floor(value);
        return wrapped < 0.0f ? wrapped + 1.0f : wrapped;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
