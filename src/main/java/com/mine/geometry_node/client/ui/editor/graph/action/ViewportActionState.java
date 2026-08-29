package com.mine.geometry_node.client.ui.editor.graph.action;

public interface ViewportActionState {
    boolean isReady();
    boolean isInsideGroupScope();
    boolean hasSelection();
}
