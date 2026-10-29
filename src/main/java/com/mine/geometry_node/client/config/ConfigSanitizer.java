package com.mine.geometry_node.client.config;

import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Repairs in-memory configuration and applies each registered entry's validation rules. */
final class ConfigSanitizer {
    private static final float RIGHT_SIDEBAR_WEIGHT_MIN = 0.08f;
    private static final float RIGHT_SIDEBAR_WEIGHT_MAX = 0.75f;

    private ConfigSanitizer() {}

    static Result sanitize(AppConfig config) {
        if (config == null) return new Result(AppConfig.defaults(), true);
        AppConfig defaults = AppConfig.defaults();
        boolean changed = false;
        if (config.assetBrowser == null) { config.assetBrowser = defaults.assetBrowser; changed = true; }
        if (config.viewport == null) { config.viewport = defaults.viewport; changed = true; }
        if (config.gameView == null) { config.gameView = defaults.gameView; changed = true; }
        if (config.node == null) { config.node = defaults.node; changed = true; }
        if (config.keyBindings == null) { config.keyBindings = defaults.keyBindings; changed = true; }
        if (config.keyBindings.global == null) { config.keyBindings.global = defaults.keyBindings.global; changed = true; }
        if (config.keyBindings.viewport == null) { config.keyBindings.viewport = defaults.keyBindings.viewport; changed = true; }
        if (config.keyBindings.gameView == null) { config.keyBindings.gameView = defaults.keyBindings.gameView; changed = true; }
        if (config.keyBindings.shopEditor == null) { config.keyBindings.shopEditor = defaults.keyBindings.shopEditor; changed = true; }

        if (config.assetBrowser.favoriteAssetPaths == null) {
            config.assetBrowser.favoriteAssetPaths = defaults.assetBrowser.favoriteAssetPaths;
            changed = true;
        } else {
            NormalizeList favorites = normalizeFavoriteAssetPaths(config.assetBrowser.favoriteAssetPaths);
            config.assetBrowser.favoriteAssetPaths = favorites.value;
            changed |= favorites.changed;
        }
        Map<String, List<String>> remoteShortcuts = normalizeRemoteQuickAccess(config.assetBrowser.remoteQuickAccessPaths);
        changed |= !Objects.equals(remoteShortcuts, config.assetBrowser.remoteQuickAccessPaths);
        config.assetBrowser.remoteQuickAccessPaths = remoteShortcuts;
        if (!validSidebarWeight(config.assetBrowser.rightSidebarWeight)) {
            config.assetBrowser.rightSidebarWeight = defaults.assetBrowser.rightSidebarWeight;
            changed = true;
        }
        if (!validSidebarWeight(config.viewport.rightSidebarWeight)) {
            config.viewport.rightSidebarWeight = defaults.viewport.rightSidebarWeight;
            changed = true;
        }
        if (!isSidebarTabId(config.assetBrowser.rightSidebarTab)) {
            config.assetBrowser.rightSidebarTab = defaults.assetBrowser.rightSidebarTab;
            changed = true;
        }
        if (!isSidebarTabId(config.viewport.rightSidebarTab)) {
            config.viewport.rightSidebarTab = defaults.viewport.rightSidebarTab;
            changed = true;
        }
        changed |= ConfigRegistry.INSTANCE.normalize(config);
        return new Result(config, changed);
    }

    private static boolean validSidebarWeight(float weight) {
        return Float.isFinite(weight) && weight >= RIGHT_SIDEBAR_WEIGHT_MIN && weight <= RIGHT_SIDEBAR_WEIGHT_MAX;
    }

    private static Map<String, List<String>> normalizeRemoteQuickAccess(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((server, paths) -> {
            if (server == null || server.isBlank() || paths == null) return;
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String path : paths) {
                try {
                    AssetRepositoryPath address = AssetRepositoryPath.parse(path, false);
                    normalized.add(address.serialize());
                } catch (IllegalArgumentException | NullPointerException invalid) {
                    // Invalid user configuration must not affect other server namespaces.
                }
            }
            if (!normalized.isEmpty()) result.put(server, new ArrayList<>(normalized));
        });
        return result;
    }

    private static boolean isSidebarTabId(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') return false;
        }
        return true;
    }

    private static NormalizeList normalizeFavoriteAssetPaths(List<String> source) {
        List<String> normalized = new ArrayList<>();
        boolean changed = false;
        for (String path : source) {
            if (path == null || path.isBlank()) {
                changed = true;
                continue;
            }

            File file;
            try {
                file = AssetBrowserPathPolicy.resolveConfigPath(path);
            } catch (java.io.UncheckedIOException migrationFailure) {
                // A conflicting migration must not erase existing favorites during config repair.
                file = new File(path);
            }
            if (!file.exists() || !file.isFile()) {
                changed = true;
                continue;
            }

            String normalizedPath;
            try {
                normalizedPath = file.getCanonicalPath();
            } catch (Exception ignored) {
                normalizedPath = file.getAbsolutePath();
            }

            if (normalized.contains(normalizedPath)) {
                changed = true;
                continue;
            }
            normalized.add(normalizedPath);
            changed |= !normalizedPath.equals(path);
        }
        changed |= normalized.size() != source.size();
        return new NormalizeList(normalized, changed);
    }

    record Result(AppConfig config, boolean changed) {
    }

    private record NormalizeList(List<String> value, boolean changed) {
    }

}
