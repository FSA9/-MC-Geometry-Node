package com.mine.geometry_node.client.ui.shell.menu.api;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public record MainMenuItemDefinition(
        String id,
        MainMenuItemType type,
        String labelTranslationKey,
        int order,
        Runnable action,
        BooleanSupplier enabled,
        BooleanSupplier checked,
        Supplier<String> shortcutText
) {
    private static final BooleanSupplier TRUE = () -> true;
    private static final BooleanSupplier FALSE = () -> false;
    private static final Supplier<String> NO_SHORTCUT = () -> "";

    public MainMenuItemDefinition {
        id = requireText(id, "id");
        type = Objects.requireNonNull(type, "type");
        if (type == MainMenuItemType.SEPARATOR) {
            labelTranslationKey = "";
            action = null;
            enabled = FALSE;
            checked = FALSE;
            shortcutText = NO_SHORTCUT;
        } else {
            labelTranslationKey = requireText(labelTranslationKey, "labelTranslationKey");
            action = Objects.requireNonNull(action, "action");
            enabled = enabled == null ? TRUE : enabled;
            checked = checked == null ? FALSE : checked;
            shortcutText = shortcutText == null ? NO_SHORTCUT : shortcutText;
        }
    }

    public static MainMenuItemDefinition command(
            String id, String labelTranslationKey, int order, Runnable action,
            BooleanSupplier enabled, Supplier<String> shortcutText) {
        return new MainMenuItemDefinition(id, MainMenuItemType.COMMAND, labelTranslationKey,
                order, action, enabled, FALSE, shortcutText);
    }

    public static MainMenuItemDefinition check(
            String id, String labelTranslationKey, int order, Runnable action,
            BooleanSupplier enabled, BooleanSupplier checked, Supplier<String> shortcutText) {
        return new MainMenuItemDefinition(id, MainMenuItemType.CHECK, labelTranslationKey,
                order, action, enabled, checked, shortcutText);
    }

    public static MainMenuItemDefinition separator(String id, int order) {
        return new MainMenuItemDefinition(id, MainMenuItemType.SEPARATOR, "", order,
                null, FALSE, FALSE, NO_SHORTCUT);
    }

    public boolean isEnabled() {
        return type != MainMenuItemType.SEPARATOR && enabled.getAsBoolean();
    }

    public boolean isChecked() {
        return type == MainMenuItemType.CHECK && checked.getAsBoolean();
    }

    public String resolvedShortcutText() {
        String value = shortcutText.get();
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }
}
