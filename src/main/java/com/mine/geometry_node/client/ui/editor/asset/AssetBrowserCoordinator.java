package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;

import java.io.File;
import java.util.List;

public interface AssetBrowserCoordinator {
    void dispatchNavigateTo(File directory);

    void dispatchNavigateToFavorites();

    void dispatchNavigateToRemoteRoot();

    boolean canBrowseRemote();

    void showUploadDialog(List<File> selectedFiles);

    void showDownloadDialog(List<AssetEntry> remoteEntries);

    default void createRemoteGraph(String targetPath, Runnable onSuccess) {
    }

    void notifySidebarChanged();
}
