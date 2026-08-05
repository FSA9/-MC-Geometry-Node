package com.mine.geometry_node.core.engine.quest.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public record QuestObjectiveDefinition(
        String entryId,
        RichTextValue content,
        boolean counterEnabled,
        String counterKey,
        boolean quantityEnabled,
        double targetValue,
        HintType hintType,
        String hintValue
) {
    public QuestObjectiveDefinition {
        entryId = normalizeEntryId(entryId);
        content = content != null ? content : RichTextValue.EMPTY;
        counterKey = normalizeKey(counterKey);
        quantityEnabled = quantityEnabled || counterEnabled;
        targetValue = Double.isFinite(targetValue) ? Math.max(0.0, targetValue) : 0.0;
        hintType = hintType != null ? hintType : HintType.NONE;
        hintValue = hintValue != null ? hintValue.trim() : "";
        if (!counterEnabled) counterKey = "";
        if (hintType == HintType.NONE) hintValue = "";
    }

    public static QuestObjectiveDefinition empty() {
        return new QuestObjectiveDefinition(
                UUID.randomUUID().toString(), RichTextValue.EMPTY, false, "", false, 1.0,
                HintType.NONE, "");
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("entry_id", entryId);
        root.add("content", QuestDefinition.richTextToJson(content));
        root.addProperty("counter_enabled", counterEnabled);
        if (counterEnabled) {
            root.addProperty("counter_key", counterKey);
        }
        root.addProperty("quantity_enabled", quantityEnabled);
        if (quantityEnabled) {
            root.addProperty("target_value", targetValue);
        }
        root.addProperty("hint_type", hintType.id());
        if (hintType != HintType.NONE && !hintValue.isEmpty()) {
            root.addProperty("hint_value", hintValue);
        }
        return root;
    }

    public static QuestObjectiveDefinition fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return empty();
        JsonObject root = element.getAsJsonObject();
        boolean counterEnabled = readBoolean(root, "counter_enabled", false);
        boolean quantityEnabled = readBoolean(
                root, "quantity_enabled", counterEnabled || root.has("target_value"));
        return new QuestObjectiveDefinition(
                readString(root, "entry_id", ""),
                QuestDefinition.readRichText(root.get("content")),
                counterEnabled,
                readString(root, "counter_key", ""),
                quantityEnabled,
                readDouble(root, "target_value", 1.0),
                HintType.fromId(readString(root, "hint_type", "")),
                readString(root, "hint_value", "")
        );
    }

    public static String normalizeKey(@Nullable String key) {
        if (key == null) return "";
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String normalizeEntryId(@Nullable String id) {
        String normalized = id != null ? id.trim() : "";
        return normalized.isEmpty() ? UUID.randomUUID().toString() : normalized;
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject root, String key, double fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public enum HintType {
        NONE("none"),
        ITEM_STACK("item_stack"),
        BLOCK("block"),
        ENTITY("entity");

        private final String id;

        HintType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static HintType fromId(@Nullable String id) {
            if (id != null) {
                for (HintType value : values()) {
                    if (value.id.equalsIgnoreCase(id.trim())) return value;
                }
            }
            return NONE;
        }
    }
}
