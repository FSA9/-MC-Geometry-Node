package com.mine.geometry_node.client.ui.editor.asset.browser;

import java.io.File;
import java.util.List;

/** Clipboard state scoped to one asset browser UI session. */
final class AssetBrowserClipboard {
    private List<File> localFiles = List.of();
    private boolean cutLocal;
    private List<String> remotePaths = List.of();
    private boolean cutRemote;

    List<File> localFiles() {
        return localFiles;
    }

    boolean isLocalCut() {
        return cutLocal;
    }

    void setLocal(List<File> files, boolean cut) {
        localFiles = files == null ? List.of() : List.copyOf(files);
        cutLocal = cut && !localFiles.isEmpty();
    }

    void clearLocal() {
        localFiles = List.of();
        cutLocal = false;
    }

    List<String> remotePaths() {
        return remotePaths;
    }

    boolean isRemoteCut() {
        return cutRemote;
    }

    void setRemote(List<String> paths, boolean cut) {
        remotePaths = paths == null ? List.of() : List.copyOf(paths);
        cutRemote = cut && !remotePaths.isEmpty();
    }

    void clearRemote() {
        remotePaths = List.of();
        cutRemote = false;
    }

    void clear() {
        clearLocal();
        clearRemote();
    }
}
