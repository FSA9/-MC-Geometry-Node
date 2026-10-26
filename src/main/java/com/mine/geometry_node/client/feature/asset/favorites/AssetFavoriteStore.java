package com.mine.geometry_node.client.feature.asset.favorites;

import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.feature.asset.repository.LocalAssetRepository;
import com.mine.geometry_node.client.config.ConfigManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class AssetFavoriteStore {
    public List<String> pathsSnapshot() {
        return new ArrayList<>(ConfigManager.INSTANCE.getConfig().assetBrowser.favoriteAssetPaths);
    }

    public boolean isFavorite(File file) {
        return file != null && ConfigManager.INSTANCE.getConfig().assetBrowser.favoriteAssetPaths.contains(pathKey(file));
    }

    public void toggle(File file) {
        if (file == null || !file.isFile()
                || !AssetTypeRegistry.INSTANCE.resolveLocal(file)
                .supports(AssetTypeAction.FAVORITE)) return;

        String key = pathKey(file);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteAssetPaths;
            if (favorites.contains(key)) {
                favorites.remove(key);
            } else {
                favorites.add(key);
            }
        });
    }

    public void updatePath(File oldFile, File newFile) {
        String oldKey = pathKey(oldFile);
        String newKey = pathKey(newFile);
        if (oldKey.equals(newKey)) return;
        if (oldFile.isDirectory() || newFile.isDirectory()) {
            updateDirectoryPath(oldFile, newFile);
            return;
        }
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteAssetPaths;
            int index = favorites.indexOf(oldKey);
            if (index < 0) return;
            favorites.set(index, newKey);
        });
    }

    public void removePath(File file) {
        String key = pathKey(file);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteAssetPaths;
            favorites.removeIf(favorite -> favorite.equals(key) || favorite.startsWith(key + File.separator));
        });
    }

    public String pathKey(File file) {
        return LocalAssetRepository.pathKey(file);
    }

    private void updateDirectoryPath(File oldDirectory, File newDirectory) {
        String oldPrefix = pathKey(oldDirectory);
        String newPrefix = pathKey(newDirectory);
        ConfigManager.INSTANCE.update(config -> {
            List<String> favorites = config.assetBrowser.favoriteAssetPaths;
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
