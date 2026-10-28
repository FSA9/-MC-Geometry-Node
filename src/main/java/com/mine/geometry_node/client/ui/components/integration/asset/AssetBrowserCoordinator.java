package com.mine.geometry_node.client.ui.components.integration.asset;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;

import java.io.File;
import java.util.List;

public interface AssetBrowserCoordinator {
    void dispatchNavigateTo(File directory);

    void dispatchNavigateToFavorites();

    void dispatchNavigateToRemoteRoot();

    default void dispatchNavigateToRemote(String directory) {
        if (directory == null || directory.isEmpty()) dispatchNavigateToRemoteRoot();
    }

    default boolean allowsSource(AssetSourceKind source) { return true; }

    boolean canBrowseRemote();

    void showUploadDialog(List<File> selectedFiles);

    void showDownloadDialog(List<AssetEntry> remoteEntries);

    default void createRemoteGraph(String targetPath, Runnable onSuccess) {
    }

    void notifySidebarChanged();
}
