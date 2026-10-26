package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetBrowserDocumentActions;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import icyllis.modernui.view.ViewGroup;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Workspace-specific services assembled outside the asset Area and its reusable browser. */
public interface AssetBrowserAreaBindings {
    AssetBrowserDocumentActions documents(AssetTaskController tasks);

    Properties properties(EditorSidebar sidebar);

    Transfers transfers(ViewGroup owner, AssetFileBrowserPanel browser);

    AutoCloseable onDocumentSaved(Consumer<Path> listener);

    interface Properties {
        void bind(List<AssetEntry> selection, Runnable onSaved);
        void commitPendingEdits();
    }

    interface Transfers {
        void showUploadDialog(List<File> files);
        void showDownloadDialog(List<AssetEntry> entries);
        void createRemoteGraph(String targetPath, Runnable onSuccess);
        /** Resume owner tasks and UI delivery after a temporary hide; ignored after dispose. */
        void activate();
        /** Temporarily stop planning/tasks and invalidate old UI callbacks; submitted transfers still finish maintenance. */
        void deactivate();
        /** Idempotent permanent retirement; later activate calls must not revive this owner. */
        void dispose();
    }
}
