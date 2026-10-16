package com.mine.geometry_node.client.config;

import java.util.Objects;

/** Stable metadata for one section in the global settings dialog. */
public record ConfigCategory(String id, String titleTranslationKey, int order,
                             ConfigCategory parent, String descriptionTranslationKey) {
    public ConfigCategory(String id, String titleTranslationKey, int order) {
        this(id, titleTranslationKey, order, null, null);
    }

    public ConfigCategory(String id, String titleTranslationKey, int order, ConfigCategory parent) {
        this(id, titleTranslationKey, order, parent, null);
    }

    public ConfigCategory {
        id = normalize(id, "id");
        titleTranslationKey = normalize(titleTranslationKey, "titleTranslationKey");
        if (descriptionTranslationKey != null) descriptionTranslationKey = normalize(descriptionTranslationKey, "descriptionTranslationKey");
        for (ConfigCategory ancestor = parent; ancestor != null; ancestor = ancestor.parent()) {
            if (id.equals(ancestor.id())) throw new IllegalArgumentException("Cyclic config category: " + id);
        }
    }

    /** Includes this category and all descendants, identified by stable registry IDs. */
    public boolean isWithin(ConfigCategory ancestor) {
        if (ancestor == null) return false;
        for (ConfigCategory current = this; current != null; current = current.parent()) {
            if (current.id().equals(ancestor.id())) return true;
        }
        return false;
    }

    private static String normalize(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Config category " + name + " cannot be blank");
        return normalized;
    }
}
