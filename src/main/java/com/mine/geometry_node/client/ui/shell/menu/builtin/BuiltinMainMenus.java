package com.mine.geometry_node.client.ui.shell.menu.builtin;

import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuRegistry;

public final class BuiltinMainMenus {
    public static final String FILE_MENU_ID = "file";
    public static final String SETTINGS_ITEM_ID = "settings";

    private BuiltinMainMenus() {
    }

    public static void register(MainMenuRegistry registry, MainUiServices services) {
        registry.registerMenu(new MainMenuDefinition(
                FILE_MENU_ID,
                "geometry_node.main_menu.file",
                100
        ));
        registry.registerItem(FILE_MENU_ID, MainMenuItemDefinition.command(
                SETTINGS_ITEM_ID,
                "geometry_node.main_menu.settings",
                100,
                services::openSettings,
                services::canOpenSettings,
                () -> ""
        ));
    }
}
