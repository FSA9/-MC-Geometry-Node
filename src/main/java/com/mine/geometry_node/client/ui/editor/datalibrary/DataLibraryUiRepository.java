package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** UI boundary for the server-authoritative Data Library. */
public interface DataLibraryUiRepository {
    record EntryKey(UUID id) {}

    record Folder(UUID id, UUID parentId, String name) {}

    record Entry(UUID id, UUID parentId, PortType type, String key, Object value) {}

    List<Folder> folders();

    List<Entry> entries();

    default Entry findEntry(UUID id) {
        return entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    default String folderPath(UUID folderId) {
        if (folderId == null) return "/";
        java.util.Map<UUID, Folder> byId = folders().stream()
                .collect(java.util.stream.Collectors.toMap(Folder::id, folder -> folder));
        java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        UUID current = folderId;
        while (current != null && visited.add(current)) {
            Folder folder = byId.get(current);
            if (folder == null) break;
            names.addFirst(folder.name());
            current = folder.parentId();
        }
        return names.isEmpty() ? "/" : "/" + String.join("/", names);
    }

    void create(UUID parentId, PortType type);

    void createFolder(UUID parentId);

    void update(Entry expected, Entry replacement);

    void updateFolder(Folder expected, Folder replacement);

    void moveEntry(UUID entryId, UUID parentId);

    void moveFolder(UUID folderId, UUID parentId);

    void delete(Set<EntryKey> entries, Set<UUID> folders);

    default void addChangeListener(Runnable listener) {}

    default void removeChangeListener(Runnable listener) {}

    default void refresh(Runnable completion) {
        if (completion != null) completion.run();
    }

    DataLibraryUiRepository EMPTY = new DataLibraryUiRepository() {
        @Override public List<Folder> folders() { return List.of(); }
        @Override public List<Entry> entries() { return List.of(); }
        @Override public void create(UUID parentId, PortType type) {}
        @Override public void createFolder(UUID parentId) {}
        @Override public void update(Entry expected, Entry replacement) {}
        @Override public void updateFolder(Folder expected, Folder replacement) {}
        @Override public void moveEntry(UUID entryId, UUID parentId) {}
        @Override public void moveFolder(UUID folderId, UUID parentId) {}
        @Override public void delete(Set<EntryKey> entries, Set<UUID> folders) {}
    };
}
