package com.mine.geometry_node.client.ui.persistence.config;

import java.util.Objects;

/** Stable metadata for one section in the global settings dialog. */
public record ConfigCategory(String id, String titleTranslationKey, int order) {
    public ConfigCategory {
        id = normalize(id, "id");
        titleTranslationKey = normalize(titleTranslationKey, "titleTranslationKey");
    }

    private static String normalize(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Config category " + name + " cannot be blank");
        return normalized;
    }
}
