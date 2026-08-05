package com.mine.geometry_node.core.engine.quest.model;

import com.google.gson.JsonObject;

public record QuestCounterBinding(boolean enabled, String key) {
    public static final QuestCounterBinding NONE = new QuestCounterBinding(false, "");

    public QuestCounterBinding {
        key = enabled ? QuestCounterKey.normalize(key) : "";
    }

    public void writeTo(JsonObject root) {
        root.addProperty("counter_enabled", enabled);
        if (enabled) root.addProperty("counter_key", key);
    }

    public static QuestCounterBinding fromJson(JsonObject root) {
        boolean enabled = QuestDefinition.readBoolean(root, "counter_enabled", false);
        return new QuestCounterBinding(
                enabled,
                QuestDefinition.readString(root, "counter_key", ""));
    }
}
