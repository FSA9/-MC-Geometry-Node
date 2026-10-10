package com.mine.geometry_node.client.ui.editor.datalibrary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Stable-ID selection rules, independent from view lifetime and repository mutations. */
final class DataLibrarySelection {
    final Set<DataLibraryUiRepository.EntryKey> entries = new LinkedHashSet<>();
    final Set<UUID> folders = new LinkedHashSet<>();
    final Set<UUID> rendered = new LinkedHashSet<>();
    UUID anchor;

    void clear() {
        entries.clear();
        folders.clear();
        anchor = null;
    }

    void selectEntry(UUID id, boolean range) {
        var ordered = new ArrayList<>(rendered);
        int target = ordered.indexOf(id);
        if (target < 0) return;
        int start = range && anchor != null ? ordered.indexOf(anchor) : -1;
        if (start < 0) start = target;
        entries.clear();
        for (int i = Math.min(start, target); i <= Math.max(start, target); i++) {
            entries.add(new DataLibraryUiRepository.EntryKey(ordered.get(i)));
        }
        anchor = id;
    }

    void selectFolderTree(DataLibrarySnapshot snapshot, UUID root, boolean checked) {
        if (root == null) return;
        snapshot.indexChildren();
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            if (!visited.add(id)) continue;
            if (checked) folders.add(id); else folders.remove(id);
            for (var entry : snapshot.childEntries.getOrDefault(id, java.util.List.of())) {
                var key = new DataLibraryUiRepository.EntryKey(entry.id());
                if (checked) entries.add(key); else entries.remove(key);
            }
            for (var folder : snapshot.childFolders.getOrDefault(id, java.util.List.of())) queue.add(folder.id());
        }
    }

    void selectAll(DataLibrarySnapshot snapshot, boolean checked) {
        if (!checked) {
            entries.clear();
            folders.clear();
            return;
        }
        folders.addAll(snapshot.folders.keySet());
        for (UUID id : snapshot.entries.keySet()) entries.add(new DataLibraryUiRepository.EntryKey(id));
    }

    void retain(DataLibrarySnapshot snapshot) {
        entries.removeIf(key -> !snapshot.entries.containsKey(key.id()));
        folders.retainAll(snapshot.folders.keySet());
        if (anchor != null && !snapshot.entries.containsKey(anchor)) anchor = null;
    }
}
