package com.mine.geometry_node.core.engine.dialogue.richtext;

import com.mine.geometry_node.core.node.value.RichTextValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits dialogue body text on one empty line (two line endings) without
 * discarding rich-text spans. A single line break remains in the same round.
 */
public final class DialogueRoundParser {
    private static final Pattern ROUND_BREAK = Pattern.compile("(?:\\r?\\n[ \\t]*){2,}");

    private DialogueRoundParser() {
    }

    public static List<RichTextValue> split(RichTextValue value) {
        RichTextValue safe = value == null ? RichTextValue.EMPTY : value;
        boolean segmentsMatchPlain = segmentsMatchPlain(safe);
        Matcher matcher = ROUND_BREAK.matcher(safe.plain());
        if (!matcher.find()) {
            return List.of(slice(safe, 0, safe.plain().length(), segmentsMatchPlain));
        }

        List<RichTextValue> rounds = new ArrayList<>();
        int start = 0;
        do {
            addRound(rounds, slice(safe, start, matcher.start(), segmentsMatchPlain));
            start = matcher.end();
        } while (matcher.find());
        addRound(rounds, slice(safe, start, safe.plain().length(), segmentsMatchPlain));
        return rounds.isEmpty() ? List.of(RichTextValue.EMPTY) : List.copyOf(rounds);
    }

    private static void addRound(List<RichTextValue> rounds, RichTextValue round) {
        if (!round.plain().isBlank()) {
            rounds.add(round);
        }
    }

    private static RichTextValue slice(RichTextValue value, int start, int end, boolean segmentsMatchPlain) {
        int contentStart = start;
        int contentEnd = end;
        while (contentStart < contentEnd && isBoundaryWhitespace(value.plain().charAt(contentStart))) {
            contentStart++;
        }
        while (contentEnd > contentStart && isBoundaryWhitespace(value.plain().charAt(contentEnd - 1))) {
            contentEnd--;
        }

        String plain = value.plain().substring(contentStart, contentEnd);
        if (plain.isEmpty()) {
            return RichTextValue.EMPTY;
        }
        if (!segmentsMatchPlain || value.segments().isEmpty()) {
            return RichTextValue.plain(plain);
        }

        List<RichTextValue.Segment> segments = new ArrayList<>();
        int segmentStart = 0;
        for (RichTextValue.Segment segment : value.segments()) {
            String segmentPlain = segment.plainText();
            int segmentEnd = segmentStart + segmentPlain.length();
            int overlapStart = Math.max(contentStart, segmentStart);
            int overlapEnd = Math.min(contentEnd, segmentEnd);
            if (overlapStart < overlapEnd) {
                int localStart = overlapStart - segmentStart;
                int localEnd = overlapEnd - segmentStart;
                String part = segmentPlain.substring(localStart, localEnd);
                if (RichTextValue.KIND_LATEX.equals(segment.kind())
                        && localStart == 0 && localEnd == segmentPlain.length()) {
                    segments.add(RichTextValue.Segment.latex(part, segment.display()));
                } else {
                    segments.add(RichTextValue.Segment.text(part, segment.style()));
                }
            }
            segmentStart = segmentEnd;
        }
        return segments.isEmpty() ? RichTextValue.plain(plain) : new RichTextValue(plain, segments);
    }

    private static boolean isBoundaryWhitespace(char value) {
        return value == '\r' || value == '\n' || value == ' ' || value == '\t';
    }

    private static boolean segmentsMatchPlain(RichTextValue value) {
        if (value.segments().isEmpty()) {
            return true;
        }
        StringBuilder joined = new StringBuilder(value.plain().length());
        for (RichTextValue.Segment segment : value.segments()) {
            joined.append(segment.plainText());
        }
        return value.plain().contentEquals(joined);
    }
}
