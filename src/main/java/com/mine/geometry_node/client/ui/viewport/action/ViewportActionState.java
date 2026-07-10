package com.mine.geometry_node.client.ui.viewport.action;

public interface ViewportActionState {
    boolean isReady();
    boolean isInsideGroupScope();
    boolean hasSelection();
}
