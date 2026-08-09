package com.mine.geometry_node.client.ui.settings.page.api;

import java.util.Objects;

/** View-free registration metadata for one settings page. */
public record SettingsPageDefinition(
        String id,
        String titleTranslationKey,
        int order,
        SettingsPageFactory factory
) {
    public SettingsPageDefinition {
        id = requireText(id, "id");
        titleTranslationKey = requireText(titleTranslationKey, "titleTranslationKey");
        factory = Objects.requireNonNull(factory, "factory");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Settings page " + name + " cannot be blank");
        }
        return normalized;
    }
}
