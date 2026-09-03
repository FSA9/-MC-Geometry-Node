package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.Objects;
import java.util.UUID;

/** Data Library object transported between workspace editors. */
public record DataLibraryDragPayload(Kind kind, UUID id, PortType type, String key)
        implements WorkspaceDragPayload {
    public enum Kind {
        ENTRY,
        FOLDER
    }

    public DataLibraryDragPayload {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (kind == Kind.ENTRY) Objects.requireNonNull(type, "type");
        key = key == null ? "" : key;
    }

    public static DataLibraryDragPayload entry(UUID id, PortType type, String key) {
        return new DataLibraryDragPayload(Kind.ENTRY, id, type, key);
    }

    public static DataLibraryDragPayload folder(UUID id) {
        return new DataLibraryDragPayload(Kind.FOLDER, id, null, "");
    }

    public boolean isEntry() {
        return kind == Kind.ENTRY;
    }
}
