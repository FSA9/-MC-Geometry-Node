package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigEntry;

/** Mutually exclusive projections used only by the built-in settings pages. */
final class BuiltinSettingsProjection {
    static boolean isShortcut(ConfigEntry<?> entry) {
        return entry.editorType() == ConfigEntry.EditorType.KEY_BINDING
                || entry.editorType() == ConfigEntry.EditorType.SHORTCUT;
    }

    static boolean isPath(ConfigEntry<?> entry) {
        return !isShortcut(entry)
                && entry.category().isWithin(BuiltinConfigEntries.ASSET_BROWSER);
    }

    static boolean isParameter(ConfigEntry<?> entry) {
        return !isShortcut(entry) && !isPath(entry);
    }

    private BuiltinSettingsProjection() {}
}
