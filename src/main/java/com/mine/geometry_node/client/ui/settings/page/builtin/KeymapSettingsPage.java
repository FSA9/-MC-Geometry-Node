package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;

public final class KeymapSettingsPage extends ConfigEntriesSettingsPage {
    public KeymapSettingsPage(SettingsPageContext context) {
        super(context, KeymapSettingsPage::isShortcut);
    }

    private static boolean isShortcut(ConfigEntry<?> entry) {
        return entry.editorType() == ConfigEntry.EditorType.KEY_BINDING
                || entry.editorType() == ConfigEntry.EditorType.SHORTCUT;
    }
}
