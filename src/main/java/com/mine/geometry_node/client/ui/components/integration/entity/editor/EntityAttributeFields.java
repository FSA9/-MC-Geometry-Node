package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client presentation metadata, advertised by the server; never discovers or changes capabilities. */
final class EntityAttributeFields {
    static final List<String> CATEGORIES = List.of("general", "life", "defense", "attack", "movement", "survival", "appearance", "behavior");
    private static final String LABEL_PREFIX = "geometry_node.entity_editor.attributes.";

    private EntityAttributeFields() {}

    enum Kind { NUMBER, BOOLEAN, NAME }

    record Field(String key, String labelKey, String category, Kind kind, double min, double max, boolean percentage) {
        String display(Number value) {
            return percentage ? new BigDecimal(value.toString()).movePointRight(2)
                    .stripTrailingZeros().toPlainString() : String.valueOf(value);
        }

        Number parse(String raw, Number previous) {
            double value = percentage ? new BigDecimal(raw.trim()).movePointLeft(2).doubleValue()
                    : Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException();
            if (previous instanceof Float) {
                float converted = (float) value;
                if (!Float.isFinite(converted) || converted < min || converted > max) throw new IllegalArgumentException();
                return converted;
            }
            return value;
        }
    }

    static List<Field> from(Map<String, Object> snapshot, Map<String, Object> attributes) {
        if (!(snapshot.get("attributeFields") instanceof List<?> descriptors)) return legacy(attributes);
        List<Field> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object entry : descriptors) {
            if (!(entry instanceof Map<?, ?> descriptor)
                    || !(descriptor.get("key") instanceof String key) || key.isBlank()
                    || !(descriptor.get("labelKey") instanceof String label) || label.isBlank()
                    || !(descriptor.get("category") instanceof String category) || !CATEGORIES.contains(category)
                    || !(descriptor.get("kind") instanceof String kind)) continue;
            Kind type = switch (kind) {
                case "number" -> Kind.NUMBER;
                case "boolean" -> Kind.BOOLEAN;
                case "name" -> Kind.NAME;
                default -> null;
            };
            Object value = attributes.get(key);
            if (type == null || !attributes.containsKey(key)
                    || type == Kind.NUMBER && !(value instanceof Number)
                    || type == Kind.BOOLEAN && !(value instanceof Boolean)
                    || type == Kind.NAME && !key.equals("name")) continue;
            double min = descriptor.get("min") instanceof Number number ? number.doubleValue() : Double.NEGATIVE_INFINITY;
            double max = descriptor.get("max") instanceof Number number ? number.doubleValue() : Double.POSITIVE_INFINITY;
            if (Double.isNaN(min) || Double.isNaN(max) || min > max || !seen.add(key)) continue;
            fields.add(new Field(key, label, category, type, min, max, Boolean.TRUE.equals(descriptor.get("percentage"))));
        }
        return List.copyOf(fields);
    }

    /** Older/custom adapters retain their existing fields without inventing native capabilities. */
    private static List<Field> legacy(Map<String, Object> attributes) {
        List<Field> fields = new ArrayList<>();
        addLegacy(fields, attributes, "life", Kind.NUMBER, "health", "maxHealth", "absorption");
        addLegacy(fields, attributes, "attack", Kind.NUMBER, "attack");
        addLegacy(fields, attributes, "movement", Kind.NUMBER, "speed");
        addLegacy(fields, attributes, "appearance", Kind.NAME, "name");
        addLegacy(fields, attributes, "appearance", Kind.BOOLEAN, "nameVisible");
        addLegacy(fields, attributes, "behavior", Kind.BOOLEAN, "noAI", "persistent", "silent", "invulnerable", "gravity");
        return List.copyOf(fields);
    }

    private static void addLegacy(List<Field> fields, Map<String, Object> attributes, String category, Kind kind, String... keys) {
        for (String key : keys) {
            if (!attributes.containsKey(key) || kind == Kind.NUMBER && !(attributes.get(key) instanceof Number)
                    || kind == Kind.BOOLEAN && !(attributes.get(key) instanceof Boolean)) continue;
            double min = key.equals("health") || key.equals("maxHealth") ? Float.MIN_VALUE : 0;
            fields.add(new Field(key, LABEL_PREFIX + key, category, kind, min, Double.POSITIVE_INFINITY, false));
        }
    }
}
