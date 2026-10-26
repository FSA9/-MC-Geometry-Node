package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One repository read and its indexes, shared by a complete UI refresh. */
final class DataLibrarySnapshot {
    final Map<UUID, DataLibraryRepository.Folder> folders = new LinkedHashMap<>();
    final Map<UUID, DataLibraryRepository.Entry> entries = new LinkedHashMap<>();
    final Map<UUID, List<DataLibraryRepository.Folder>> childFolders = new LinkedHashMap<>();
    final Map<UUID, List<DataLibraryRepository.Entry>> childEntries = new LinkedHashMap<>();
    private boolean indexed;

    DataLibrarySnapshot(DataLibraryRepository repository) {
        for (var folder : repository.folders()) {
            if (folder == null || folder.id() == null) continue;
            folders.put(folder.id(), folder);
        }
        for (var entry : repository.entries()) {
            if (entry == null || entry.id() == null || entry.type() == null) continue;
            entries.put(entry.id(), entry);
        }

    }

    void indexChildren() {
        if (indexed) return;
        indexed = true;
        for (var folder : folders.values()) {
            childFolders.computeIfAbsent(folder.parentId(), ignored -> new ArrayList<>()).add(folder);
        }
        for (var entry : entries.values()) {
            childEntries.computeIfAbsent(entry.parentId(), ignored -> new ArrayList<>()).add(entry);
        }
        childFolders.values().forEach(values -> values.sort(Comparator.comparing(
                DataLibraryRepository.Folder::name, String.CASE_INSENSITIVE_ORDER)));
        childEntries.values().forEach(values -> values.sort(Comparator.comparing(
                DataLibraryRepository.Entry::key, String.CASE_INSENSITIVE_ORDER)));
    }

    boolean sameStructure(DataLibrarySnapshot other) {
        if (other == null || !folders.equals(other.folders) || !entries.keySet().equals(other.entries.keySet())) return false;
        for (var entry : entries.values()) {
            var previous = other.entries.get(entry.id());
            if (entry.type() != previous.type() || !Objects.equals(entry.parentId(), previous.parentId())
                    || !Objects.equals(entry.key(), previous.key())
                    || !Objects.equals(entry.tags(), previous.tags())) return false;
        }
        return true;
    }
}
