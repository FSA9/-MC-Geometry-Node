package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragPayload;
import com.mine.geometry_node.core.node.definition.port.PortType;
import java.util.*;

/** Data Library object(s) transported between workspace editors. */
public record DataLibraryDragPayload(Kind kind, UUID id, PortType type, String key,
                                    Set<UUID> entryIds, Set<UUID> folderIds)
        implements WorkspaceDragPayload {
    public enum Kind { ENTRY, FOLDER }
    public DataLibraryDragPayload {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(id, "id");
        if (kind == Kind.ENTRY) Objects.requireNonNull(type, "type");
        key = key == null ? "" : key;
        entryIds = entryIds == null || entryIds.isEmpty() ? Set.of(id) : Set.copyOf(entryIds);
        folderIds = folderIds == null || folderIds.isEmpty() ? (kind == Kind.FOLDER ? Set.of(id) : Set.of()) : Set.copyOf(folderIds);
    }
    public DataLibraryDragPayload(Kind kind, UUID id, PortType type, String key) { this(kind,id,type,key,null,null); }
    public static DataLibraryDragPayload entry(UUID id, PortType type, String key) { return new DataLibraryDragPayload(Kind.ENTRY,id,type,key); }
    public static DataLibraryDragPayload entries(UUID anchor, PortType type, String key, Set<UUID> ids) { return new DataLibraryDragPayload(Kind.ENTRY,anchor,type,key,ids,Set.of()); }
    public static DataLibraryDragPayload folder(UUID id) { return new DataLibraryDragPayload(Kind.FOLDER,id,null,""); }
    public static DataLibraryDragPayload folders(UUID anchor, Set<UUID> ids) { return new DataLibraryDragPayload(Kind.FOLDER,anchor,null,"",Set.of(),ids); }
    public boolean isEntry() { return kind == Kind.ENTRY; }
}
