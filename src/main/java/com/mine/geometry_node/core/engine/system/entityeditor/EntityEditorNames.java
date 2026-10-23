package com.mine.geometry_node.core.engine.system.entityeditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.system.dialogue.richtext.DialogueTextParser;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;

import java.util.*;

/** Name values use the existing dictionary/rich-text transport, without a second packet codec.
 * A snapshot is a standard rich-text map plus the complete vanilla Component JSON in "component".
 * Its projection is editable by the shared rich-text editor; untouched values retain the original
 * translation, inherited styles and events. Completed rich edits deliberately replace that Component.
 * Legacy adapters may continue advertising/accepting String names.
 */
public final class EntityEditorNames {
    private static final int MAX_LENGTH = 256;
    private static final int MAX_COMPONENT_JSON = 16_384;
    private static final Set<String> COMPONENT_KEYS = Set.of("type", "version", "plain", "segments", "component");
    private static final Set<String> STYLE_KEYS = Set.of("bold", "italic", "underlined", "strikethrough", "color");

    private EntityEditorNames() {}

    public static Object snapshot(Component component, HolderLookup.Provider registries) {
        if (component == null) return RichTextValue.EMPTY;
        Map<String, Object> value = new LinkedHashMap<>(project(component).toMap());
        value.put("component", ComponentSerialization.CODEC.encodeStart(ops(registries), component).getOrThrow().toString());
        return Collections.unmodifiableMap(value);
    }

    /** Only a rich carrier advertises rich editing. String-only extension adapters keep their contract. */
    public static boolean supportsRichText(Object value) {
        return value instanceof RichTextValue || value instanceof Map<?, ?> map && map.keySet().equals(COMPONENT_KEYS);
    }

    /** An in-flight save may replace the authored rich value with its authoritative Component carrier. */
    public static boolean sameAfterConfirmation(Object authored, Object confirmed) {
        if (EntityEditorChanges.same(authored, confirmed)) return true;
        return authored instanceof RichTextValue && confirmed instanceof Map<?, ?>
                && EntityEditorChanges.same(snapshot(decode(authored, null), null), confirmed);
    }

    public static void validate(Object value) {
        if (value instanceof String text) { validatePlain(text); return; }
        RichTextValue rich;
        if (value instanceof RichTextValue text) rich = text;
        else if (value instanceof Map<?, ?> map && map.keySet().equals(COMPONENT_KEYS)
                && map.get("component") instanceof String json && json.length() <= MAX_COMPONENT_JSON) {
            rich = RichTextValue.from(map);
            Map<String, Object> projection = new LinkedHashMap<>(EntityEditorChanges.dictionary(map));
            projection.remove("component");
            if (!EntityEditorChanges.same(projection, rich.toMap())) throw new IllegalArgumentException("Malformed name projection");
        } else throw new IllegalArgumentException("Expected a name value");
        if (!RichTextValue.TYPE.equals(rich.type()) || rich.version() != RichTextValue.VERSION)
            throw new IllegalArgumentException("Unsupported rich name version");
        validatePlain(rich.plain());
        if (rich.segments().size() > MAX_LENGTH) throw new IllegalArgumentException("Too many name segments");
        StringBuilder plain = new StringBuilder();
        for (RichTextValue.Segment segment : rich.segments()) {
            if (!RichTextValue.KIND_TEXT.equals(segment.kind()) || !segment.source().isEmpty()
                    || !"inline".equals(segment.display()) || !STYLE_KEYS.containsAll(segment.style().keySet()))
                throw new IllegalArgumentException("Unsupported name segment");
            validatePlain(segment.text()); plain.append(segment.text());
            for (var entry : segment.style().entrySet()) {
                if (entry.getKey().equals("color")) {
                    if (!(entry.getValue() instanceof String color) || !color.matches("#[0-9a-fA-F]{6}"))
                        throw new IllegalArgumentException("Invalid name color");
                } else if (!(entry.getValue() instanceof Boolean)) throw new IllegalArgumentException("Invalid name style");
            }
        }
        if (!rich.segments().isEmpty() && !plain.toString().equals(rich.plain()))
            throw new IllegalArgumentException("Name projection does not match its segments");
    }

    /** Validate before mutation; null means clearing the vanilla custom name. */
    public static Component component(Object value, HolderLookup.Provider registries) {
        validate(value);
        Component component = decode(value, registries);
        if (value instanceof Map<?, ?>) {
            validatePlain(component.getString());
            if (!editable(project(component)).equals(editable(RichTextValue.from(value))))
                throw new IllegalArgumentException("Component does not match its name projection");
        }
        return component;
    }

    /** Application follows validation; rollback must also decode pre-existing names outside edit limits. */
    static Component decode(Object value, HolderLookup.Provider registries) {
        if (value instanceof Map<?, ?> map) {
            try { return ComponentSerialization.CODEC.parse(ops(registries), JsonParser.parseString((String) map.get("component"))).getOrThrow(); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid custom name Component", invalid); }
        }
        RichTextValue rich = RichTextValue.from(value);
        if (rich.plain().isEmpty()) return null;
        return DialogueTextParser.fromRichText(rich).component();
    }

    /** Canonical visible runs let an unchanged overlay save preserve Component-only properties. */
    public static RichTextValue editable(RichTextValue value) {
        List<RichTextValue.Segment> runs = new ArrayList<>();
        List<RichTextValue.Segment> source = value.segments().isEmpty()
                ? List.of(RichTextValue.Segment.text(value.plain(), Map.of())) : value.segments();
        for (RichTextValue.Segment segment : source) {
            if (segment.text().isEmpty()) continue;
            Map<String, Object> style = new LinkedHashMap<>(segment.style());
            style.values().removeIf(Boolean.FALSE::equals);
            if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
                var previous = runs.removeLast();
                runs.add(RichTextValue.Segment.text(previous.text() + segment.text(), style));
            } else runs.add(RichTextValue.Segment.text(segment.text(), style));
        }
        return new RichTextValue(value.plain(), runs);
    }

    private static RichTextValue project(Component component) {
        List<RichTextValue.Segment> runs = new ArrayList<>();
        component.visit((style, text) -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (style.getColor() != null) attributes.put("color", String.format(Locale.ROOT, "#%06x", style.getColor().getValue()));
            if (style.isBold()) attributes.put("bold", true);
            if (style.isItalic()) attributes.put("italic", true);
            if (style.isUnderlined()) attributes.put("underlined", true);
            if (style.isStrikethrough()) attributes.put("strikethrough", true);
            runs.add(RichTextValue.Segment.text(text, attributes));
            return Optional.empty();
        }, Style.EMPTY);
        return editable(new RichTextValue(component.getString(), runs));
    }

    private static DynamicOps<JsonElement> ops(HolderLookup.Provider registries) {
        return registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);
    }

    private static void validatePlain(String text) {
        if (text.length() > MAX_LENGTH || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid name");
    }
}
