package com.mine.geometry_node.core.engine.system.faction.model;

import com.mine.geometry_node.core.node.value.RichTextValue;
import java.util.*;

/** Shared limits and validation at persistence, API and network boundaries. */
public final class FactionRules {
    public static final int MAX_FACTIONS = 256, MAX_RANKS = 256, MAX_ENTITY_REFERENCES = 1024;
    public static final int MAX_NAME_LENGTH = 256, MAX_DESCRIPTION_LENGTH = 4096, MAX_SEGMENTS = 256;
    public static final int MAX_DOCUMENT_BYTES = 768 * 1024;
    private static final Set<String> STYLES = Set.of("bold", "italic", "underlined", "strikethrough", "color");
    private FactionRules() {}

    public static int score(int score) {
        if (score < -100 || score > 100) throw new IllegalArgumentException("Faction score must be between -100 and 100");
        return score;
    }

    /** Comparison only: preserve authored text and styles; do not fold internal whitespace or Unicode forms. */
    public static String nameKey(RichTextValue name) {
        return Objects.requireNonNull(name, "name").plain().strip().toLowerCase(Locale.ROOT);
    }

    public static RichTextValue text(RichTextValue value, boolean description) {
        Objects.requireNonNull(value, "text");
        int limit = description ? MAX_DESCRIPTION_LENGTH : MAX_NAME_LENGTH;
        if (!RichTextValue.TYPE.equals(value.type()) || value.version() != RichTextValue.VERSION
                || value.plain().length() > limit || value.segments().size() > MAX_SEGMENTS)
            throw new IllegalArgumentException("Invalid faction rich text");
        StringBuilder plain = new StringBuilder();
        for (var segment : value.segments()) {
            if (!RichTextValue.KIND_TEXT.equals(segment.kind()) || !segment.source().isEmpty()
                    || !"inline".equals(segment.display()) || !STYLES.containsAll(segment.style().keySet()))
                throw new IllegalArgumentException("Unsupported faction text segment");
            plain.append(segment.text());
            if (plain.length() > limit) throw new IllegalArgumentException("Faction text is too long");
            for (var style : segment.style().entrySet()) {
                if (style.getKey().equals("color")) {
                    if (!(style.getValue() instanceof String color) || !color.matches("#[0-9a-fA-F]{6}"))
                        throw new IllegalArgumentException("Invalid faction text color");
                } else if (!(style.getValue() instanceof Boolean)) throw new IllegalArgumentException("Invalid faction text style");
            }
        }
        if (!value.segments().isEmpty() && !plain.toString().equals(value.plain()))
            throw new IllegalArgumentException("Faction text projection differs from segments");
        return value;
    }

    public static Map<UUID, Integer> relations(Map<UUID, Integer> source, int maximum) {
        Objects.requireNonNull(source, "relations");
        if (source.size() > maximum) throw new IllegalArgumentException("Too many faction relations");
        Map<UUID, Integer> result = new LinkedHashMap<>();
        source.forEach((id, value) -> {
            Objects.requireNonNull(id, "faction id");
            if (score(Objects.requireNonNull(value, "score")) != 0) result.put(id, value);
        });
        return Collections.unmodifiableMap(result);
    }
}
