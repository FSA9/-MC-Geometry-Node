package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigCategory;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;

public final class ParameterSettingsPage extends ConfigEntriesSettingsPage {
    public ParameterSettingsPage(SettingsPageContext context) {
        super(context, ParameterSettingsPage::retainCategory,
                entry -> !isShortcut(entry) && !isPathSetting(entry));
    }

    private static boolean isShortcut(ConfigEntry<?> entry) {
        return entry.editorType() == ConfigEntry.EditorType.KEY_BINDING
                || entry.editorType() == ConfigEntry.EditorType.SHORTCUT;
    }

    private static boolean isPathSetting(ConfigEntry<?> entry) {
        return entry.category().id().equals(BuiltinConfigEntries.ASSET_BROWSER.id());
    }

    private static boolean retainCategory(ConfigCategory category) {
        return category.id().equals(BuiltinConfigEntries.VIEWPORT.id())
                || category.id().equals(BuiltinConfigEntries.NODE.id());
    }
}
