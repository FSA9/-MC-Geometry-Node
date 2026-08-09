package com.mine.geometry_node.client.ui.settings.page.api;

@FunctionalInterface
public interface SettingsPageFactory {
    SettingsPage create(SettingsPageContext context);
}
