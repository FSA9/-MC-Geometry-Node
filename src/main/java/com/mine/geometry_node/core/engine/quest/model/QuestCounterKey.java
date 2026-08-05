package com.mine.geometry_node.core.engine.quest.model;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

public final class QuestCounterKey {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_.-]+");

    private QuestCounterKey() {
    }

    /** Returns the canonical key, or an empty string when the input is invalid. */
    public static String normalize(@Nullable String key) {
        if (key == null) return "";
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return VALID_KEY.matcher(normalized).matches() ? normalized : "";
    }

    public static boolean isValid(@Nullable String key) {
        return !normalize(key).isEmpty();
    }
}
