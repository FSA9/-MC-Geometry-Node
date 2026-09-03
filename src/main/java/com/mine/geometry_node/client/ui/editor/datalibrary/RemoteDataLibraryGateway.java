package com.mine.geometry_node.client.ui.editor.datalibrary;

import java.util.List;
import java.util.Set;

/** Network boundary for the server-authoritative Data Library. */
public interface RemoteDataLibraryGateway {
    List<DataLibraryUiRepository.Folder> folderSnapshot();

    List<DataLibraryUiRepository.Entry> snapshot();

    DataLibraryUiRepository.Entry findEntry(java.util.UUID id);

    String folderPath(java.util.UUID folderId);

    boolean hasLoadedSnapshot();

    void refresh(Runnable completion);

    void create(DataLibraryUiRepository.Entry entry, Runnable completion);

    void update(DataLibraryUiRepository.Entry expected, DataLibraryUiRepository.Entry replacement,
                Runnable completion);

    void delete(Set<DataLibraryUiRepository.EntryKey> entries, Set<java.util.UUID> folders,
                Runnable completion);

    void createFolder(DataLibraryUiRepository.Folder folder, Runnable completion);

    void updateFolder(DataLibraryUiRepository.Folder expected, DataLibraryUiRepository.Folder replacement,
                      Runnable completion);

    void moveEntry(java.util.UUID entryId, java.util.UUID parentId, Runnable completion);

    void moveFolder(java.util.UUID folderId, java.util.UUID parentId, Runnable completion);

}
