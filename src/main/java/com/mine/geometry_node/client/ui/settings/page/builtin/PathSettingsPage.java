package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;

/** Settings whose ownership belongs to configured asset and filesystem paths. */
public final class PathSettingsPage extends ConfigEntriesSettingsPage {
    public PathSettingsPage(SettingsPageContext context) {
        super(context, PathSettingsPage::isPathSetting);
    }

    private static boolean isPathSetting(ConfigEntry<?> entry) {
        return entry.category().id().equals(BuiltinConfigEntries.ASSET_BROWSER.id());
    }
}
