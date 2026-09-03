package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryDocument;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntry;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntryKey;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryLoadResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Single adapter boundary between editor state and the core Data Library model. */
final class DataLibraryUiMapper {
    private DataLibraryUiMapper() {}

    static DataLibraryDocument toDocument(List<DataLibraryUiRepository.Entry> entries) {
        DataLibraryDocument document = new DataLibraryDocument();
        for (DataLibraryUiRepository.Entry entry : entries) {
            document.put(entry.type(), new DataLibraryEntry(entry.id(), entry.key(), entry.value()));
        }
        return document;
    }

    static List<DataLibraryUiRepository.Entry> fromDocument(DataLibraryLoadResult loaded) {
        List<DataLibraryUiRepository.Entry> result = new ArrayList<>();
        loaded.document().entriesByType().forEach((type, values) -> values.values().forEach(entry ->
                result.add(new DataLibraryUiRepository.Entry(type, entry.id(), entry.key(), entry.value()))));
        return List.copyOf(result);
    }

    static Set<DataLibraryEntryKey> toKeys(Set<DataLibraryUiRepository.EntryKey> keys) {
        return keys.stream().map(key -> new DataLibraryEntryKey(key.type(), key.id()))
                .collect(Collectors.toUnmodifiableSet());
    }

    static DataLibraryUiRepository.EntryKey key(DataLibraryUiRepository.Entry entry) {
        return new DataLibraryUiRepository.EntryKey(entry.type(), entry.id());
    }
}
