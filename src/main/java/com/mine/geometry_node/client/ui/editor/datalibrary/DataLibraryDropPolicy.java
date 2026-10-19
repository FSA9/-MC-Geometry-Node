package com.mine.geometry_node.client.ui.editor.datalibrary;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validates an entire drop against one snapshot before any move is submitted. */
final class DataLibraryDropPolicy {
    private DataLibraryDropPolicy() {}

    static boolean canMove(DataLibrarySnapshot snapshot, DataLibraryDragPayload payload, UUID target) {
        if (payload == null || target != null && !snapshot.folders.containsKey(target)) return false;
        snapshot.indexChildren();
        if (payload.isEntry()) {
            for (UUID id : payload.entryIds()) {
                var source = snapshot.entries.get(id);
                if (source == null || Objects.equals(source.parentId(), target)) return false;
                for (var other : snapshot.childEntries.getOrDefault(target, java.util.List.of())) {
                    if (!other.id().equals(id) && other.type() == source.type()
                            && other.key().trim().equals(source.key().trim())) return false;
                }
            }
            Set<String> incoming = new HashSet<>();
            for (UUID id : payload.entryIds()) {
                var entry = snapshot.entries.get(id);
                if (!incoming.add(entry.type().name() + ":" + entry.key().trim())) return false;
            }
            return !payload.entryIds().isEmpty();
        }
        Set<String> incoming = new HashSet<>();
        for (UUID id : payload.folderIds()) {
            var source = snapshot.folders.get(id);
            if (source == null || Objects.equals(source.parentId(), target)
                    || isInFolderTree(target, id, snapshot.folders)) return false;
            if (!incoming.add(source.name().trim())) return false;
            for (var other : snapshot.childFolders.getOrDefault(target, java.util.List.of())) {
                if (!other.id().equals(id) && other.name().trim().equals(source.name().trim())) return false;
            }
        }
        return !payload.folderIds().isEmpty();
    }

    static Set<UUID> draggedFolderRoots(UUID grabbed, Set<UUID> selected, DataLibrarySnapshot snapshot) {
        if (!selected.contains(grabbed)) return Set.of(grabbed);
        for (UUID id : selected) {
            if (!id.equals(grabbed) && isInFolderTree(grabbed, id, snapshot.folders)) return Set.of(grabbed);
        }
        Set<UUID> roots = new LinkedHashSet<>(selected);
        for (UUID id : selected) {
            for (UUID ancestor : selected) {
                if (!id.equals(ancestor) && isInFolderTree(id, ancestor, snapshot.folders)) {
                    roots.remove(id);
                    break;
                }
            }
        }
        return roots;
    }

    private static boolean isInFolderTree(UUID folderId, UUID root,
                                         Map<UUID, DataLibraryUiRepository.Folder> folders) {
        if (root == null) return false;
        Set<UUID> visited = new HashSet<>();
        for (UUID cursor = folderId; cursor != null && visited.add(cursor);) {
            if (cursor.equals(root)) return true;
            var folder = folders.get(cursor);
            cursor = folder == null ? null : folder.parentId();
        }
        return false;
    }
}
