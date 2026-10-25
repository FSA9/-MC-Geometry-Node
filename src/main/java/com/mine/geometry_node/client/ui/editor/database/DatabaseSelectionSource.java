package com.mine.geometry_node.client.ui.editor.database;

/** Owner contract for the registered database inspector, independent of the page layout. */
public interface DatabaseSelectionSource {
    DatabaseSelection selection();
}
