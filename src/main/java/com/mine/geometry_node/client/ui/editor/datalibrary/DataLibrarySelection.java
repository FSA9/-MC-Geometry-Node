package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.datalibrary.DataLibraryRepository;

import java.util.ArrayDeque;
import com.mine.geometry_node.client.ui.components.base.list.UiListSelection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Stable-ID selection rules, independent from view lifetime and repository mutations. */
final class DataLibrarySelection {
    final Set<DataLibraryRepository.EntryKey> entries = new LinkedHashSet<>();
    final Set<UUID> folders = new LinkedHashSet<>();
    final Set<UUID> rendered = new LinkedHashSet<>();
    UUID anchor;

    void clear() {
        entries.clear();
        folders.clear();
        anchor = null;
    }

    void selectEntry(UUID id, boolean range) {
        selectEntry(id, range, false);
    }

    void selectEntry(UUID id, boolean range, boolean additive) {
        if (!rendered.contains(id)) return;
        if (!additive) folders.clear();
        var key = new DataLibraryRepository.EntryKey(id);
        var ordered = rendered.stream().map(DataLibraryRepository.EntryKey::new).toList();
        var nextAnchor = UiListSelection.click(entries, ordered,
                anchor == null ? null : new DataLibraryRepository.EntryKey(anchor), key, additive, range);
        anchor = nextAnchor == null ? null : nextAnchor.id();
    }

    void selectFolderTree(DataLibrarySnapshot snapshot, UUID root, boolean checked) {
        for (UUID id : folderTreeIds(snapshot, root)) {
            if (checked) folders.add(id); else folders.remove(id);
            for (var entry : snapshot.childEntries.getOrDefault(id, java.util.List.of())) {
                var key = new DataLibraryRepository.EntryKey(entry.id());
                if (checked) entries.add(key); else entries.remove(key);
            }
        }
    }

    static Set<UUID> folderTreeIds(DataLibrarySnapshot snapshot, UUID root) {
        Set<UUID> visited = new LinkedHashSet<>();
        if (root == null) return visited;
        snapshot.indexChildren();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            if (!visited.add(id)) continue;
            for (var folder : snapshot.childFolders.getOrDefault(id, java.util.List.of())) queue.add(folder.id());
        }
        return visited;
    }

    void selectAll(DataLibrarySnapshot snapshot, boolean checked) {
        clear();
        if (!checked) {
            return;
        }
        folders.addAll(snapshot.folders.keySet());
        for (UUID id : snapshot.entries.keySet()) entries.add(new DataLibraryRepository.EntryKey(id));
    }

    /** Header checkbox changes only mounted result rows, preserving other directories' selection. */
    void selectRendered(boolean checked) {
        for (UUID id : rendered) {
            var key = new DataLibraryRepository.EntryKey(id);
            if (checked) entries.add(key); else entries.remove(key);
        }
    }

    void retain(DataLibrarySnapshot snapshot) {
        entries.removeIf(key -> !snapshot.entries.containsKey(key.id()));
        folders.retainAll(snapshot.folders.keySet());
        if (anchor != null && !snapshot.entries.containsKey(anchor)) anchor = null;
    }
}
