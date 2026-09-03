package com.mine.geometry_node.client.ui.workspace.drag;

/** Semantic operation requested by a drag source. */
public enum WorkspaceDragOperation {
    COPY,
    MOVE,
    LINK,
    IMPORT,
    /** The drop target decides whether the gesture moves or links the payload. */
    CONTEXTUAL
}
