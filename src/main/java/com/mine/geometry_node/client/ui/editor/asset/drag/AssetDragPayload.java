package com.mine.geometry_node.client.ui.editor.asset.drag;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Asset-browser payload transported through the workspace drag service. */
public record AssetDragPayload(List<AssetEntry> entries) implements WorkspaceDragPayload {
    public AssetDragPayload {
        entries = entries == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public AssetEntry entry() {
        return entries.size() == 1 ? entries.get(0) : null;
    }

    public boolean isSingleJsonGraph() {
        return AssetTypeRegistry.INSTANCE.isType(entry(), AssetTypeRegistry.GRAPH_ID);
    }
}
