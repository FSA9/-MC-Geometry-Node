package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.editor.asset.repository.LocalAssetRepository;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class GraphFavoriteStore {
    List<String> pathsSnapshot() {
        return new ArrayList<>(ConfigManager.INSTANCE.getConfig().assetBrowser.favoriteGraphPaths);
    }

    boolean isFavorite(File file) {
        return file != null && ConfigManager.INSTANCE.getConfig().assetBrowser.favoriteGraphPaths.contains(pathKey(file));
    }

    void toggle(File file) {
        if (file == null || !file.isFile()
                || !AssetTypeRegistry.INSTANCE.resolve(AssetSourceKind.LOCAL, file.getName(), false)
                .supports(AssetTypeAction.FAVORITE)) return;

        String key = pathKey(file);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteGraphPaths;
            if (favorites.contains(key)) {
                favorites.remove(key);
            } else {
                favorites.add(key);
            }
        });
    }

    void updatePath(File oldFile, File newFile) {
        String oldKey = pathKey(oldFile);
        String newKey = pathKey(newFile);
        if (oldKey.equals(newKey)) return;
        if (oldFile.isDirectory() || newFile.isDirectory()) {
            updateDirectoryPath(oldFile, newFile);
            return;
        }
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteGraphPaths;
            int index = favorites.indexOf(oldKey);
            if (index < 0) return;
            favorites.set(index, newKey);
        });
    }

    void removePath(File file) {
        String key = pathKey(file);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteGraphPaths;
            favorites.removeIf(favorite -> favorite.equals(key) || favorite.startsWith(key + File.separator));
        });
    }

    String pathKey(File file) {
        return LocalAssetRepository.pathKey(file);
    }

    private void updateDirectoryPath(File oldDirectory, File newDirectory) {
        String oldPrefix = pathKey(oldDirectory);
        String newPrefix = pathKey(newDirectory);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteGraphPaths;
            for (int i = 0; i < favorites.size(); i++) {
                String favorite = favorites.get(i);
                if (favorite.equals(oldPrefix)) {
                    favorites.set(i, newPrefix);
                } else if (favorite.startsWith(oldPrefix + File.separator)) {
                    favorites.set(i, newPrefix + favorite.substring(oldPrefix.length()));
                }
            }
        });
    }
}
