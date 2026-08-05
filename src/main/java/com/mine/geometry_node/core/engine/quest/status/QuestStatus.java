package com.mine.geometry_node.core.engine.quest.status;

import java.util.Locale;
import java.util.Objects;

public record QuestStatus(String id,
                          String translationKey,
                          boolean terminal,
                          boolean graphActive,
                          boolean defaultListVisibility,
                          boolean assignable,
                          int color) {
    public QuestStatus {
        id = normalizeId(id);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Quest status id cannot be empty");
        }
        translationKey = Objects.requireNonNullElse(translationKey, "").trim();
        if (translationKey.isEmpty()) {
            throw new IllegalArgumentException("Quest status translation key cannot be empty");
        }
    }

    public static String normalizeId(String id) {
        return id != null ? id.trim().toLowerCase(Locale.ROOT) : "";
    }
}
