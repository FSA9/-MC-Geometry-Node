package com.mine.geometry_node.client.runtime.dialogue;

import com.mine.geometry_node.core.engine.system.dialogue.richtext.DialogueRichText;
import com.mine.geometry_node.core.engine.system.dialogue.richtext.DialogueTextParser;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueText;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.StyleSpan;
import icyllis.modernui.text.style.StrikethroughSpan;
import icyllis.modernui.text.style.UnderlineSpan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Converts normalized dialogue text to the best format supported by ModernUI.
 */
public final class ModernDialogueText {

    private ModernDialogueText() {
    }

    public static DialogueRichText parse(DialogueText value) {
        return DialogueTextParser.parse(value);
    }

    public static CharSequence display(DialogueRichText text) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendComponent(builder, text.component(), Style.EMPTY);
        return builder.length() == 0 ? text.plainText() : builder;
    }

    public static CharSequence display(RichTextValue value) {
        RichTextValue safe = value == null ? RichTextValue.EMPTY : value;
        if (safe.segments().isEmpty()) {
            return safe.plain();
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (RichTextValue.Segment segment : safe.segments()) {
            int start = builder.length();
            if (RichTextValue.KIND_LATEX.equals(segment.kind())) {
                builder.append(latexLabel(segment));
                builder.setSpan(new ForegroundColorSpan(0xFF88D7FF), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.ITALIC), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.append(segment.text());
                applyStyle(builder, start, builder.length(), segment.style());
            }
        }
        return builder.length() == 0 ? safe.plain() : builder;
    }

    public static String plain(DialogueText value) {
        return parse(value).plainText();
    }

    private static String latexLabel(RichTextValue.Segment segment) {
        String source = segment.source();
        if (source == null || source.isBlank()) {
            return "";
        }
        return "block".equals(segment.display()) ? "$$" + source + "$$" : "\\(" + source + "\\)";
    }

    private static void appendComponent(SpannableStringBuilder builder, Component component, Style parentStyle) {
        component.visit((style, value) -> {
            int start = builder.length();
            builder.append(value);
            applyStyle(builder, start, builder.length(), style);
            return Optional.empty();
        }, parentStyle);
    }

    private static void applyStyle(SpannableStringBuilder builder, int start, int end, Style style) {
        if (start >= end || style.isEmpty()) {
            return;
        }
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        TextColor color = style.getColor();
        if (color != null) {
            builder.setSpan(new ForegroundColorSpan(0xFF000000 | color.getValue()), start, end, flags);
        }
        if (style.isBold() && style.isItalic()) {
            builder.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, flags);
        } else if (style.isBold()) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        } else if (style.isItalic()) {
            builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
        }
        if (style.isUnderlined()) {
            builder.setSpan(new UnderlineSpan(), start, end, flags);
        }
        if (style.isStrikethrough()) {
            builder.setSpan(new StrikethroughSpan(), start, end, flags);
        }
    }

    private static void applyStyle(SpannableStringBuilder builder, int start, int end, java.util.Map<String, Object> style) {
        if (start >= end || style == null || style.isEmpty()) {
            return;
        }
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        Integer color = parseColor(style.get("color"));
        if (color != null) {
            builder.setSpan(new ForegroundColorSpan(0xFF000000 | (color & 0xFFFFFF)), start, end, flags);
        }
        boolean bold = truthy(style.get("bold"));
        boolean italic = truthy(style.get("italic"));
        if (bold && italic) {
            builder.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, flags);
        } else if (bold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        } else if (italic) {
            builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
        }
        if (truthy(style.get("underlined")) || truthy(style.get("underline"))) {
            builder.setSpan(new UnderlineSpan(), start, end, flags);
        }
        if (truthy(style.get("strikethrough"))) {
            builder.setSpan(new StrikethroughSpan(), start, end, flags);
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    private static Integer parseColor(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!(value instanceof String string) || string.isBlank()) {
            return null;
        }
        return switch (string.trim()) {
            case "black" -> 0x000000;
            case "dark_blue" -> 0x0000AA;
            case "dark_green" -> 0x00AA00;
            case "dark_aqua" -> 0x00AAAA;
            case "dark_red" -> 0xAA0000;
            case "dark_purple" -> 0xAA00AA;
            case "gold" -> 0xFFAA00;
            case "gray" -> 0xAAAAAA;
            case "dark_gray" -> 0x555555;
            case "blue" -> 0x5555FF;
            case "green" -> 0x55FF55;
            case "aqua" -> 0x55FFFF;
            case "red" -> 0xFF5555;
            case "light_purple" -> 0xFF55FF;
            case "yellow" -> 0xFFFF55;
            case "white" -> 0xFFFFFF;
            default -> parseHexColor(string.trim());
        };
    }

    private static Integer parseHexColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Integer.parseUnsignedInt(hex, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
