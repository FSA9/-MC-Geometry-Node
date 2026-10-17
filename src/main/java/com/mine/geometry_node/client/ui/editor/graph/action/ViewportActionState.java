package com.mine.geometry_node.client.ui.editor.graph.action;

public interface ViewportActionState {
    boolean isReady();
    default com.mine.geometry_node.client.history.HistoryTarget historyTarget() { return null; }
    default boolean canUndo() { return historyTarget() != null && historyTarget().historyState().canUndo(); }
    default boolean canRedo() { return historyTarget() != null && historyTarget().historyState().canRedo(); }
    boolean isInsideGroupScope();
    boolean hasSelection();
}
