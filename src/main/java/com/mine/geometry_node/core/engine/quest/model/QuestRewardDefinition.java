package com.mine.geometry_node.core.engine.quest.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record QuestRewardDefinition(
        String entryId,
        RichTextValue content,
        boolean counterEnabled,
        String counterKey,
        double amount,
        QuestObjectiveDefinition.HintType hintType,
        String hintValue
) {
    public QuestRewardDefinition {
        entryId = normalizeEntryId(entryId);
        content = content != null ? content : RichTextValue.EMPTY;
        counterKey = QuestObjectiveDefinition.normalizeKey(counterKey);
        amount = Double.isFinite(amount) ? Math.max(0.0, amount) : 0.0;
        hintType = hintType != null ? hintType : QuestObjectiveDefinition.HintType.NONE;
        hintValue = hintValue != null ? hintValue.trim() : "";
        if (!counterEnabled) counterKey = "";
        if (hintType == QuestObjectiveDefinition.HintType.NONE) hintValue = "";
    }

    public static QuestRewardDefinition empty() {
        return new QuestRewardDefinition(
                UUID.randomUUID().toString(), RichTextValue.EMPTY, false, "", 1.0,
                QuestObjectiveDefinition.HintType.NONE, "");
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("entry_id", entryId);
        root.add("content", QuestDefinition.richTextToJson(content));
        root.addProperty("counter_enabled", counterEnabled);
        if (counterEnabled) root.addProperty("counter_key", counterKey);
        root.addProperty("amount", amount);
        root.addProperty("hint_type", hintType.id());
        if (hintType != QuestObjectiveDefinition.HintType.NONE && !hintValue.isEmpty()) {
            root.addProperty("hint_value", hintValue);
        }
        return root;
    }

    public static QuestRewardDefinition fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return empty();
        JsonObject root = element.getAsJsonObject();
        return new QuestRewardDefinition(
                readString(root, "entry_id", ""),
                QuestDefinition.readRichText(root.get("content")),
                readBoolean(root, "counter_enabled", false),
                readString(root, "counter_key", ""),
                readDouble(root, "amount", 1.0),
                QuestObjectiveDefinition.HintType.fromId(readString(root, "hint_type", "")),
                readString(root, "hint_value", "")
        );
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
}
