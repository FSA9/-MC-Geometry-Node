package com.mine.geometry_node.client.ui.editor.datalibrary;

import java.util.List;
import java.util.Set;

/** Network boundary for the server-authoritative Data Library. */
public interface RemoteDataLibraryGateway {
    List<DataLibraryUiRepository.Entry> snapshot();

    void refresh(Runnable completion);

    void create(DataLibraryUiRepository.Entry entry, Runnable completion);

    void update(DataLibraryUiRepository.Entry entry, Runnable completion);

    void delete(Set<DataLibraryUiRepository.EntryKey> entries, Runnable completion);
}
