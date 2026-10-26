package com.mine.geometry_node.client.feature.datalibrary;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Network boundary for the server-authoritative Data Library. */
public interface RemoteDataLibraryGateway {
    List<DataLibraryRepository.Folder> folderSnapshot();

    List<DataLibraryRepository.Entry> snapshot();

    DataLibraryRepository.Entry findEntry(java.util.UUID id);

    String folderPath(java.util.UUID folderId);

    boolean hasLoadedSnapshot();

    void refresh(Runnable completion);

    /** Evaluated when the serialized mutation starts, after earlier mutations have refreshed the snapshot. */
    void create(Supplier<DataLibraryRepository.Entry> entry, Runnable completion);

    void update(DataLibraryRepository.Entry expected, DataLibraryRepository.Entry replacement,
                Runnable completion);

    void delete(Set<DataLibraryRepository.EntryKey> entries, Set<java.util.UUID> folders,
                Runnable completion);

    void createFolder(Supplier<DataLibraryRepository.Folder> folder, Runnable completion);

    void updateFolder(DataLibraryRepository.Folder expected, DataLibraryRepository.Folder replacement,
                      Runnable completion);

    void moveEntry(java.util.UUID entryId, java.util.UUID parentId, Runnable completion);

    void moveFolder(java.util.UUID folderId, java.util.UUID parentId, Runnable completion);

}
