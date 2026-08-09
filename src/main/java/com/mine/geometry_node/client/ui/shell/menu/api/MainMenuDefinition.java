package com.mine.geometry_node.client.ui.shell.menu.api;

import java.util.Objects;

public record MainMenuDefinition(String id, String labelTranslationKey, int order) {
    public MainMenuDefinition {
        id = requireText(id, "id");
        labelTranslationKey = requireText(labelTranslationKey, "labelTranslationKey");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }
}
