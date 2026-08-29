package com.mine.geometry_node.client.ui.editor.graph.action;

public interface ViewportActionSink {
    void performAction(ViewportActionId id, ViewportActionRequest request);

    default String graphTypeId() {
        return "blueprint";
    }
}
