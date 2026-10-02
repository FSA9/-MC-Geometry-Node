package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryDocument;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntry;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryFolder;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectKey;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryLoadResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Single adapter boundary between editor state and the core Data Library model. */
final class DataLibraryUiMapper {
    private DataLibraryUiMapper() {}

    static DataLibraryDocument toDocument(List<DataLibraryUiRepository.Folder> folders,
                                          List<DataLibraryUiRepository.Entry> entries) {
        DataLibraryDocument document = new DataLibraryDocument();
        // A full document must preserve empty folders as well as folders that
        // currently contain entries.  Filtering to entry ancestors here made
        // empty folders disappear during delete/fingerprint mutations.
        putFolders(document, folders);
        for (DataLibraryUiRepository.Entry entry : entries) {
            document.put(new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(), entry.value(),
                    entry.tags()));
        }
        return document;
    }

    /** Builds a partial upload containing entries and only the folders needed to resolve their parents. */
    static DataLibraryDocument toEntryMutationDocument(List<DataLibraryUiRepository.Folder> folders,
                                                       List<DataLibraryUiRepository.Entry> entries) {
        java.util.Map<UUID, DataLibraryUiRepository.Folder> byId = folders.stream()
                .collect(Collectors.toMap(DataLibraryUiRepository.Folder::id, folder -> folder));
        java.util.LinkedHashSet<UUID> required = new java.util.LinkedHashSet<>();
        for (DataLibraryUiRepository.Entry entry : entries) {
            UUID current = entry.parentId();
            while (current != null && required.add(current)) {
                DataLibraryUiRepository.Folder folder = byId.get(current);
                if (folder == null) throw new IllegalArgumentException("Unknown Data Library folder: " + current);
                current = folder.parentId();
            }
        }
        List<DataLibraryUiRepository.Folder> ancestors = folders.stream()
                .filter(folder -> required.contains(folder.id())).toList();
        return toDocument(ancestors, entries);
    }

    static List<DataLibraryUiRepository.Folder> folders(DataLibraryLoadResult loaded) {
        return loaded.document().folders().values().stream()
                .map(folder -> new DataLibraryUiRepository.Folder(folder.id(), folder.parentId(), folder.name()))
                .toList();
    }

    static List<DataLibraryUiRepository.Entry> fromDocument(DataLibraryLoadResult loaded) {
        return loaded.document().entries().values().stream().map(entry -> new DataLibraryUiRepository.Entry(
                entry.id(), entry.parentId(), entry.type(), entry.key(), entry.value(), entry.tags())).toList();
    }

    static Set<DataLibraryObjectKey> toKeys(Set<DataLibraryUiRepository.EntryKey> keys) {
        return keys.stream().map(key -> new DataLibraryObjectKey(key.id()))
                .collect(Collectors.toUnmodifiableSet());
    }

    static Set<DataLibraryObjectKey> toFolderKeys(Set<UUID> ids) {
        return ids.stream().map(DataLibraryObjectKey::new).collect(Collectors.toUnmodifiableSet());
    }

    static DataLibraryEntry toCore(DataLibraryUiRepository.Entry entry) {
        return new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(), entry.value(),
                entry.tags());
    }

    static DataLibraryFolder toCore(DataLibraryUiRepository.Folder folder) {
        return new DataLibraryFolder(folder.id(), folder.parentId(), folder.name());
    }

    private static void putFolders(DataLibraryDocument document, List<DataLibraryUiRepository.Folder> folders) {
        List<DataLibraryUiRepository.Folder> remaining = new ArrayList<>();
        java.util.HashSet<UUID> ids = new java.util.HashSet<>();
        for (DataLibraryUiRepository.Folder folder : folders) {
            if (folder == null || folder.id() == null) {
                throw new IllegalArgumentException("Data Library folder cannot be null");
            }
            if (!ids.add(folder.id())) {
                throw new IllegalArgumentException("Duplicate Data Library folder: " + folder.id());
            }
            remaining.add(folder);
        }
        boolean changed;
        do {
            changed = false;
            for (var iterator = remaining.iterator(); iterator.hasNext();) {
                DataLibraryUiRepository.Folder folder = iterator.next();
                if (folder.parentId() != null && !document.folders().containsKey(folder.parentId())) continue;
                document.putFolder(new DataLibraryFolder(folder.id(), folder.parentId(), folder.name()));
                iterator.remove();
                changed = true;
            }
        } while (changed && !remaining.isEmpty());
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Data Library folder hierarchy is invalid (missing parent or cycle)");
        }
    }
}
