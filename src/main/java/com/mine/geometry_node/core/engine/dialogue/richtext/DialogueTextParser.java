package com.mine.geometry_node.core.engine.dialogue.richtext;

import com.google.gson.JsonParser;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Single entry point for dialogue text consumption.
 *
 * Current dialogue packets still carry strings. Those strings can be plain
 * text, vanilla Component JSON, or serialized geometry_node:rich_text JSON.
 */
public final class DialogueTextParser {

    private DialogueTextParser() {
    }

    public static DialogueRichText parse(@Nullable String value, HolderLookup.Provider registries) {
        if (value == null || value.isEmpty()) {
            return fromComponent(Component.empty());
        }

        RichTextValue richText = parseRichTextJson(value);
        if (richText != null) {
            return fromRichText(richText);
        }

        Component component = parseComponentJson(value, registries);
        if (component == null) {
            component = Component.literal(value);
        }
        return fromComponent(component);
    }

    public static DialogueRichText fromRichText(RichTextValue value) {
        RichTextValue safe = value == null ? RichTextValue.EMPTY : value;
        return new DialogueRichText(safe.plain(), toComponent(safe));
    }

    public static DialogueRichText fromComponent(Component component) {
        Component safe = sanitize(component);
        return new DialogueRichText(safe.getString(), safe);
    }

    private static @Nullable RichTextValue parseRichTextJson(String value) {
        String trimmed = value.trim();
        if (!looksLikeJsonComponent(trimmed)) {
            return null;
        }
        try {
            com.google.gson.JsonElement element = JsonParser.parseString(trimmed);
            Object unwrapped = unwrapJson(element);
            if (unwrapped instanceof Map<?, ?> map && RichTextValue.TYPE.equals(String.valueOf(map.get("type")))) {
                return RichTextValue.from(map);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
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

    private static @Nullable Component parseComponentJson(String value, HolderLookup.Provider registries) {
        String trimmed = value.trim();
        if (!looksLikeJsonComponent(trimmed)) {
            return null;
        }
        try {
            return ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(trimmed))
                    .result()
                    .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object unwrapJson(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsNumber();
            if (primitive.isString()) return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement item : element.getAsJsonArray()) {
                Object value = unwrapJson(item);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }
        if (element.isJsonObject()) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : element.getAsJsonObject().keySet()) {
                Object value = unwrapJson(element.getAsJsonObject().get(key));
                if (value != null) {
                    map.put(key, value);
                }
            }
            return map;
        }
        return null;
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

    private static boolean looksLikeJsonComponent(String value) {
        if (value.length() < 2) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
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
