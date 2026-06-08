package com.mine.geometry_node.client.ui.viewport.action;

public interface ViewportActionSink {
    void performAction(ViewportActionId id, ViewportActionRequest request);
}
