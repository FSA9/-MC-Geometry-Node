package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** UI boundary for the server-authoritative Data Library. */
public interface DataLibraryUiRepository {
    record EntryKey(PortType type, UUID id) {}

    record Entry(PortType type, UUID id, String key, Object value) {}

    List<Entry> entries();

    void create(PortType type);

    void update(Entry entry);

    void delete(Set<EntryKey> ids);

    default void addChangeListener(Runnable listener) {}

    default void removeChangeListener(Runnable listener) {}

    default void refresh(Runnable completion) {
        if (completion != null) completion.run();
    }

    DataLibraryUiRepository EMPTY = new DataLibraryUiRepository() {
        @Override public List<Entry> entries() { return List.of(); }
        @Override public void create(PortType type) {}
        @Override public void update(Entry entry) {}
        @Override public void delete(Set<EntryKey> ids) {}
    };
}
