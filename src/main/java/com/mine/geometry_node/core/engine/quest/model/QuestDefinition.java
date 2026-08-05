package com.mine.geometry_node.core.engine.quest.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable authoring metadata for one quest graph.
 */
public record QuestDefinition(
        RichTextValue title,
        RichTextValue description,
        List<QuestObjectiveDefinition> objectives,
        List<QuestRewardDefinition> rewards
) {
    private static final Gson GSON = new Gson();

    public static final QuestDefinition EMPTY = new QuestDefinition(
            RichTextValue.EMPTY,
            RichTextValue.EMPTY,
            List.of(),
            List.of());

    public QuestDefinition {
        title = title != null ? title : RichTextValue.EMPTY;
        description = description != null ? description : RichTextValue.EMPTY;
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public boolean isEmpty() {
        return title.plain().isEmpty() && title.segments().isEmpty()
                && description.plain().isEmpty() && description.segments().isEmpty()
                && objectives.isEmpty()
                && rewards.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.add("title", GSON.toJsonTree(title.toMap()));
        root.add("description", GSON.toJsonTree(description.toMap()));
        JsonArray objectiveArray = new JsonArray();
        for (QuestObjectiveDefinition objective : objectives) {
            objectiveArray.add(objective.toJson());
        }
        root.add("objectives", objectiveArray);
        JsonArray rewardArray = new JsonArray();
        for (QuestRewardDefinition reward : rewards) {
            rewardArray.add(reward.toJson());
        }
        root.add("rewards", rewardArray);
        return root;
    }

    public static QuestDefinition fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return EMPTY;
        }
        JsonObject root = element.getAsJsonObject();
        return new QuestDefinition(
                readRichText(root.get("title")),
                readRichText(root.get("description")),
                readObjectives(root.get("objectives")),
                readRewards(root.get("rewards")));
    }

    static JsonElement richTextToJson(RichTextValue value) {
        return GSON.toJsonTree((value != null ? value : RichTextValue.EMPTY).toMap());
    }

    static RichTextValue readRichText(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return RichTextValue.EMPTY;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return RichTextValue.plain(element.getAsString());
        }
        return RichTextValue.from(GSON.fromJson(element, Object.class));
    }

    static String readString(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static double readDouble(JsonObject root, String key, double fallback) {
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<QuestObjectiveDefinition> readObjectives(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<QuestObjectiveDefinition> result = new ArrayList<>();
        for (JsonElement objective : element.getAsJsonArray()) {
            result.add(QuestObjectiveDefinition.fromJson(objective));
        }
        return List.copyOf(result);
    }

    private static List<QuestRewardDefinition> readRewards(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<QuestRewardDefinition> result = new ArrayList<>();
        for (JsonElement reward : element.getAsJsonArray()) {
            result.add(QuestRewardDefinition.fromJson(reward));
        }
        return List.copyOf(result);
    }
}
