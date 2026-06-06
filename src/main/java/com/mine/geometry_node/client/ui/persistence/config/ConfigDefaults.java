package com.mine.geometry_node.client.ui.persistence.config;

import com.mine.geometry_node.client.ui.persistence.PathUtils;

import java.io.File;

final class ConfigDefaults {
    private ConfigDefaults() {
    }

    static AppConfig create() {
        AppConfig config = new AppConfig();
        addQuickAccessPath(config, PathUtils.getLocalDraftsDir().getAbsolutePath());

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                addQuickAccessPath(config, root.getAbsolutePath());
            }
        }
        return config;
    }

    private static void addQuickAccessPath(AppConfig config, String path) {
        if (path == null || path.isBlank()) return;
        if (!config.assetBrowser.quickAccessPaths.contains(path)) {
            config.assetBrowser.quickAccessPaths.add(path);
        }
    }
}
