package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.Objects;
import java.util.UUID;

/** Stable Data Library reference transported between workspace editors. */
public record DataLibraryDragPayload(PortType type, UUID id, String name)
        implements WorkspaceDragPayload {
    public DataLibraryDragPayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
    }
}
