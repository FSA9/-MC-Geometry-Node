package com.mine.geometry_node.core.node.value;

import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [富文本协议载体]
 * 后端值模型只保留纯文本降级值和结构化片段，不绑定具体 UI 渲染格式。
 */
public record RichTextValue(
        String type,
        int version,
        String plain,
        List<Segment> segments
) {
    private static final Gson GSON = new Gson();

    public static final String TYPE = "geometry_node:rich_text";
    public static final int VERSION = 1;
    public static final String KIND_TEXT = "text";
    public static final String KIND_LATEX = "latex";
    public static final RichTextValue EMPTY = new RichTextValue(TYPE, VERSION, "", List.of());

    public RichTextValue {
        type = type == null || type.isBlank() ? TYPE : type;
        version = Math.max(1, version);
        plain = plain == null ? "" : plain;
        segments = normalizeSegments(segments);
    }

    public RichTextValue(String plain, List<Segment> segments) {
        this(TYPE, VERSION, plain, segments);
    }

    public static RichTextValue plain(@Nullable String plain) {
        String safePlain = plain == null ? "" : plain;
        return safePlain.isEmpty() ? EMPTY : new RichTextValue(safePlain, List.of());
    }

    public static RichTextValue from(@Nullable Object value) {
        if (value == null) {
            return EMPTY;
        }
        if (value instanceof RichTextValue richText) {
            return richText;
        }
        if (value instanceof String text) {
            return plain(text);
        }
        if (value instanceof Map<?, ?> map) {
            RichTextValue parsed = fromMap(map);
            return parsed != null ? parsed : plain(String.valueOf(value));
        }
        return plain(String.valueOf(value));
    }

    @Nullable
    private static RichTextValue fromMap(Map<?, ?> map) {
        Object typeValue = firstPresent(map, "type", "kind");
        if (typeValue != null && !TYPE.equals(String.valueOf(typeValue))) {
            return null;
        }

        Object segmentsValue = firstPresent(map, "segments", "runs");
        List<Segment> parsedSegments = parseSegments(segmentsValue);

        Object plainValue = firstPresent(map, "plain", "text", "value");
        String parsedPlain = plainValue == null ? null : String.valueOf(plainValue);
        if (parsedPlain == null && !parsedSegments.isEmpty()) {
            parsedPlain = joinPlain(parsedSegments);
        }

        if (parsedPlain != null || !parsedSegments.isEmpty()) {
            int parsedVersion = parseInt(firstPresent(map, "version"), VERSION);
            return new RichTextValue(TYPE, parsedVersion, parsedPlain == null ? "" : parsedPlain, parsedSegments);
        }
        return null;
    }

    private static List<Segment> parseSegments(@Nullable Object value) {
        if (!(value instanceof List<?> rawSegments)) {
            return List.of();
        }

        List<Segment> parsed = new ArrayList<>();
        for (Object rawSegment : rawSegments) {
            Segment segment = Segment.from(rawSegment);
            if (segment != null) {
                parsed.add(segment);
            }
        }
        return parsed;
    }

    @Nullable
    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String joinPlain(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.plainText());
        }
        return builder.toString();
    }

    private static List<Segment> normalizeSegments(@Nullable List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return List.copyOf(segments);
    }

    @Override
    public String toString() {
        return plain;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", TYPE);
        map.put("version", version);
        map.put("plain", plain);
        List<Map<String, Object>> segmentMaps = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            segmentMaps.add(segment.toMap());
        }
        map.put("segments", segmentMaps);
        return map;
    }

    public String toJsonString() {
        return GSON.toJson(toMap());
    }

    private static int parseInt(@Nullable Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public record Segment(
            String kind,
            String text,
            String source,
            String display,
            Map<String, Object> style
    ) {
        public static Segment text(String text, Map<String, Object> style) {
            return new Segment(KIND_TEXT, text, "", "", style);
        }

        public static Segment latex(String source, String display) {
            return new Segment(KIND_LATEX, "", source, display, Map.of());
        }

        public Segment {
            kind = kind == null || kind.isBlank() ? KIND_TEXT : kind;
            text = text == null ? "" : text;
            source = source == null ? "" : source;
            display = display == null || display.isBlank() ? "inline" : display;
            style = normalizeStyle(style);
        }

        @Nullable
        public static Segment from(@Nullable Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Segment segment) {
                return segment;
            }
            if (value instanceof String text) {
                return Segment.text(text, Map.of());
            }
            if (value instanceof Map<?, ?> map) {
                Object kindValue = firstPresent(map, "kind", "type");
                String kind = kindValue == null ? KIND_TEXT : String.valueOf(kindValue);
                if (KIND_LATEX.equals(kind)) {
                    Object sourceValue = firstPresent(map, "source", "latex", "text", "value");
                    if (sourceValue == null) {
                        return null;
                    }
                    Object displayValue = firstPresent(map, "display", "mode");
                    return Segment.latex(String.valueOf(sourceValue), displayValue == null ? "inline" : String.valueOf(displayValue));
                }

                Object textValue = firstPresent(map, "text", "plain", "value");
                if (textValue == null) {
                    return null;
                }

                Map<String, Object> style = parseStyle(map);
                return Segment.text(String.valueOf(textValue), style);
            }
            return Segment.text(String.valueOf(value), Map.of());
        }

        public String plainText() {
            if (KIND_LATEX.equals(kind)) {
                return source;
            }
            return text;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", kind);
            if (KIND_LATEX.equals(kind)) {
                map.put("source", source);
                map.put("display", display);
            } else {
                map.put("text", text);
                if (!style.isEmpty()) {
                    map.put("style", style);
                }
            }
            return map;
        }

        private static Map<String, Object> parseStyle(Map<?, ?> map) {
            Object styleValue = firstPresent(map, "style", "styles", "format");
            if (styleValue instanceof Map<?, ?> styleMap) {
                return copyStringKeyMap(styleMap);
            }

            Map<String, Object> inlineStyle = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String keyString)) {
                    continue;
                }
                if (!"kind".equals(keyString) && !"type".equals(keyString)
                        && !"text".equals(keyString) && !"plain".equals(keyString) && !"value".equals(keyString)) {
                    inlineStyle.put(keyString, entry.getValue());
                }
            }
            return inlineStyle;
        }

        private static Map<String, Object> copyStringKeyMap(Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copied.put(key, entry.getValue());
                }
            }
            return copied;
        }

        private static Map<String, Object> normalizeStyle(@Nullable Map<String, Object> style) {
            if (style == null || style.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(style);
        }
    }
}
