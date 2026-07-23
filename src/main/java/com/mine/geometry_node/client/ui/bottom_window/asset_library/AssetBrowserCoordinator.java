package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;

import java.io.File;
import java.util.List;

public interface AssetBrowserCoordinator {
    void dispatchNavigateTo(File directory);

    void dispatchNavigateToFavorites();

    void dispatchNavigateToRemoteRoot();

    boolean canBrowseRemote();

    void showUploadDialog(List<File> selectedFiles);

    void showDownloadDialog(List<AssetEntry> remoteEntries);

    void notifySidebarChanged();
}
