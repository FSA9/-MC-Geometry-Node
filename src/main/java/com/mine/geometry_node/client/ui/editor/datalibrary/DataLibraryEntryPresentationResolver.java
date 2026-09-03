package com.mine.geometry_node.client.ui.editor.datalibrary;

import org.jetbrains.annotations.Nullable;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.UUID;

/** Resolves read-only entry metadata from the current client snapshot. */
public final class DataLibraryEntryPresentationResolver {
    private static final int MAX_VALUE_LENGTH = 80;

    private DataLibraryEntryPresentationResolver() {}

    @Nullable
    public static Presentation resolve(UUID entryId) {
        if (entryId == null) return null;
        DataLibraryUiRepository repository = ClientDataLibraryRepository.INSTANCE;
        DataLibraryUiRepository.Entry entry = repository.findEntry(entryId);
        if (entry == null) return null;
        return new Presentation(repository.folderPath(entry.parentId()), entry.key(),
                entry.type(), summarize(entry.value()));
    }

    private static String summarize(@Nullable Object value) {
        String text = value == null ? "null" : String.valueOf(value);
        text = text.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }

    public record Presentation(String path, String key, PortType type, String value) {}
}
