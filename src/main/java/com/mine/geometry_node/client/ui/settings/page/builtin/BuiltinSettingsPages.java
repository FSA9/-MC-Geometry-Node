package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageDefinition;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageRegistry;

public final class BuiltinSettingsPages {
    public static final String KEYMAP_PAGE_ID = "keymap";
    public static final String PARAMETERS_PAGE_ID = "parameters";
    public static final String PATHS_PAGE_ID = "paths";

    private BuiltinSettingsPages() {
    }

    public static void register(SettingsPageRegistry registry) {
        registry.register(new SettingsPageDefinition(
                KEYMAP_PAGE_ID,
                "geometry_node.settings.page.keymap",
                100,
                KeymapSettingsPage::new
        ));
        registry.register(new SettingsPageDefinition(
                PARAMETERS_PAGE_ID,
                "geometry_node.settings.page.parameters",
                200,
                ParameterSettingsPage::new
        ));
        registry.register(new SettingsPageDefinition(
                PATHS_PAGE_ID,
                "geometry_node.settings.page.paths",
                300,
                PathSettingsPage::new
        ));
    }
}
