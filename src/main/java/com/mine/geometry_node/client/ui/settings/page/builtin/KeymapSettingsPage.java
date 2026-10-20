package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;

public final class KeymapSettingsPage extends ConfigEntriesSettingsPage {
    public KeymapSettingsPage(SettingsPageContext context) {
        super(context, category -> category.isWithin(BuiltinConfigEntries.SHORTCUT_GAME_VIEW),
                BuiltinSettingsProjection::isShortcut);
    }
}
