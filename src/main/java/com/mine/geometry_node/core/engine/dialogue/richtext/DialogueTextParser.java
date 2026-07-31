package com.mine.geometry_node.core.engine.dialogue.richtext;

import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Single entry point for dialogue text consumption.
 *
 * Dialogue text is explicitly tagged before it reaches this parser.
 */
public final class DialogueTextParser {

    private DialogueTextParser() {
    }

    public static DialogueRichText parse(@Nullable DialogueText value) {
        return switch (value == null ? DialogueText.EMPTY : value) {
            case DialogueText.Plain plain -> fromComponent(Component.literal(plain.value()));
            case DialogueText.ComponentText component -> fromComponent(component.value());
            case DialogueText.Rich rich -> fromRichText(rich.value());
        };
    }

    public static DialogueRichText fromRichText(RichTextValue value) {
        RichTextValue safe = value == null ? RichTextValue.EMPTY : value;
        return new DialogueRichText(safe.plain(), toComponent(safe));
    }

    public static DialogueRichText fromComponent(Component component) {
        Component safe = sanitize(component);
        return new DialogueRichText(safe.getString(), safe);
    }

    private static Component toComponent(RichTextValue value) {
        MutableComponent result = Component.empty();
        if (value.segments().isEmpty()) {
            return Component.literal(value.plain());
        }

        for (RichTextValue.Segment segment : value.segments()) {
            if (RichTextValue.KIND_LATEX.equals(segment.kind())) {
                result.append(Component.literal(latexFallback(segment)));
                continue;
            }
            result.append(Component.literal(segment.text()).withStyle(styleFromMap(segment.style())));
        }
        return result;
    }

    private static String latexFallback(RichTextValue.Segment segment) {
        String source = segment.source();
        if (source == null || source.isBlank()) {
            return "";
        }
        return "block".equals(segment.display()) ? "$$" + source + "$$" : "\\(" + source + "\\)";
    }

    private static Style styleFromMap(Map<String, Object> styleMap) {
        Style style = Style.EMPTY;
        if (styleMap == null || styleMap.isEmpty()) {
            return style;
        }

        Integer color = parseColor(styleMap.get("color"));
        if (color != null) {
            style = style.withColor(TextColor.fromRgb(color & 0xFFFFFF));
        }
        if (truthy(styleMap.get("bold"))) {
            style = style.withBold(true);
        }
        if (truthy(styleMap.get("italic"))) {
            style = style.withItalic(true);
        }
        if (truthy(styleMap.get("underlined")) || truthy(styleMap.get("underline"))) {
            style = style.withUnderlined(true);
        }
        if (truthy(styleMap.get("strikethrough"))) {
            style = style.withStrikethrough(true);
        }
        return style;
    }

    private static boolean truthy(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    private static @Nullable Integer parseColor(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!(value instanceof String string) || string.isBlank()) {
            return null;
        }
        String color = string.trim();
        return switch (color) {
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
            default -> parseHexColor(color);
        };
    }

    private static @Nullable Integer parseHexColor(String color) {
        String hex = color.startsWith("#") ? color.substring(1) : color;
        try {
            return Integer.parseUnsignedInt(hex, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Component sanitize(Component component) {
        MutableComponent result = component.plainCopy().withStyle(sanitize(component.getStyle()));
        for (Component sibling : component.getSiblings()) {
            result.append(sanitize(sibling));
        }
        return result;
    }

    private static Style sanitize(Style style) {
        return style
                .withClickEvent(null)
                .withHoverEvent(null)
                .withInsertion(null);
    }
}
