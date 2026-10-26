package com.mine.geometry_node.client.integration.asset;

import com.mine.geometry_node.client.feature.graph.persistence.GraphJsonIO;
import com.mine.geometry_node.client.integration.graph.GraphDocuments;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetBrowserDocumentActions;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Connects asset filesystem actions to the workspace's graph documents. */
public final class WorkspaceAssetBrowserActions implements AssetBrowserDocumentActions {
    private final AssetTaskController tasks;

    public WorkspaceAssetBrowserActions(AssetTaskController tasks) {
        this.tasks = Objects.requireNonNull(tasks);
    }

    @Override public void open(File file) { GraphDocuments.openLocal(file, tasks); }
    @Override public String newFileContent() { return GraphJsonIO.toJson(new NodeGraph()); }
    @Override public void closeUnder(List<Path> paths) { DocumentManager.INSTANCE.closeSessionsUnder(paths); }
    @Override public void refreshFileReferences() { DocumentManager.INSTANCE.refreshFileReferences(); }
}
