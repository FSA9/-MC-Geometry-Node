package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.Objects;
/** Stable Data Library reference transported between workspace editors. */
public record DataLibraryDragPayload(PortType type, String key)
        implements WorkspaceDragPayload {
    public DataLibraryDragPayload {
        Objects.requireNonNull(type, "type");
        key = key == null ? "" : key;
    }
}
