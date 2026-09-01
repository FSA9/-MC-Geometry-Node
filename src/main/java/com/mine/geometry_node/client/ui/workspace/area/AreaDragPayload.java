package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;

/** Identifies an Area leaf being moved within the workspace layout. */
record AreaDragPayload(AreaLeafNode source) implements WorkspaceDragPayload {
}
