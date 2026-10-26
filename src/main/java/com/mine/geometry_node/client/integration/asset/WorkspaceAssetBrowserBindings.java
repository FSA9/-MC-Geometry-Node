package com.mine.geometry_node.client.integration.asset;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetBrowserDocumentActions;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserAreaBindings;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import icyllis.modernui.view.ViewGroup;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Installs the graph properties, document actions and transfers used by the asset Area. */
public final class WorkspaceAssetBrowserBindings implements AssetBrowserAreaBindings {
    @Override public AssetBrowserDocumentActions documents(AssetTaskController tasks) {
        return new WorkspaceAssetBrowserActions(tasks);
    }

    @Override public Properties properties(EditorSidebar sidebar) {
        GraphPropertiesPanel panel = sidebar.requirePanel(GraphPropertiesPanel.PANEL_ID, GraphPropertiesPanel.class);
        return new Properties() {
            @Override public void bind(List<AssetEntry> selection, Runnable onSaved) {
                panel.bind(AssetFilePropertiesTarget.fromSelection(selection, onSaved));
            }
            @Override public void commitPendingEdits() { panel.commitPendingEdits(); }
        };
    }

    @Override public Transfers transfers(ViewGroup owner, AssetFileBrowserPanel browser) {
        AssetTaskController tasks = new AssetTaskController(owner);
        return new AssetBrowserTransferController(owner, browser, tasks, documents(tasks));
    }

    @Override public AutoCloseable onDocumentSaved(Consumer<Path> listener) {
        Consumer<GraphSession> saved = session -> listener.accept(session.filePath());
        DocumentManager.INSTANCE.addOnSessionSavedListener(saved);
        return () -> DocumentManager.INSTANCE.removeOnSessionSavedListener(saved);
    }
}
