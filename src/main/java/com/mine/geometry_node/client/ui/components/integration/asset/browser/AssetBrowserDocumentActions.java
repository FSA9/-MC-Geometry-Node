package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Document effects supplied by the workspace; browsing and picking do not require them. */
public interface AssetBrowserDocumentActions {
    AssetBrowserDocumentActions NONE = new AssetBrowserDocumentActions() {
        @Override public void open(File file) { }
        @Override public String newFileContent() {
            throw new IllegalStateException("Document creation is not installed for this browser");
        }
        @Override public void closeUnder(List<Path> paths) { }
        @Override public void refreshFileReferences() { }
    };

    void open(File file);

    String newFileContent();

    void closeUnder(List<Path> paths);

    /** Runs after committed filesystem changes, even if the browser owner has been hidden. */
    void refreshFileReferences();
}
