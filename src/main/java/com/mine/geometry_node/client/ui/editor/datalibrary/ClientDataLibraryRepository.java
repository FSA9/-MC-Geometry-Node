package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client facade for the server-authoritative Data Library. */
public final class ClientDataLibraryRepository implements DataLibraryUiRepository {
    public static final ClientDataLibraryRepository INSTANCE = new ClientDataLibraryRepository();

    private final RemoteDataLibraryGateway remote = NetworkRemoteDataLibraryGateway.INSTANCE;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.ArrayList<Runnable> pendingInitialLoad = new java.util.ArrayList<>();
    private boolean initialLoadInFlight;
    private long connectionGeneration;

    private ClientDataLibraryRepository() {}

    @Override
    public List<Folder> folders() {
        return List.copyOf(remote.folderSnapshot());
    }

    @Override
    public List<Entry> entries() {
        return List.copyOf(remote.snapshot());
    }

    @Override public Entry findEntry(UUID id) { return remote.findEntry(id); }

    @Override public String folderPath(UUID folderId) { return remote.folderPath(folderId); }

    /** Coalesces the initial remote snapshot request used by reference metadata views. */
    public void ensureLoaded(Runnable completion) {
        if (remote.hasLoadedSnapshot()) {
            if (completion != null) completion.run();
            return;
        }
        long generation;
        synchronized (pendingInitialLoad) {
            if (completion != null) pendingInitialLoad.add(completion);
            if (initialLoadInFlight) return;
            initialLoadInFlight = true;
            generation = connectionGeneration;
        }
        remote.refresh(() -> {
            java.util.List<Runnable> callbacks;
            synchronized (pendingInitialLoad) {
                if (generation != connectionGeneration) return;
                callbacks = java.util.List.copyOf(pendingInitialLoad);
                pendingInitialLoad.clear();
                initialLoadInFlight = false;
            }
            notifyChanged();
            callbacks.forEach(Runnable::run);
        });
    }

    public void resetConnection() {
        synchronized (pendingInitialLoad) {
            connectionGeneration++;
            pendingInitialLoad.clear();
            initialLoadInFlight = false;
        }
    }

    @Override
    public void create(UUID parentId, PortType type) {
        Entry entry = new Entry(UUID.randomUUID(), parentId, type,
                uniqueKey(parentId, type), type.getDefaultValue());
        remote.create(entry, this::notifyChanged);
    }

    @Override
    public void createFolder(UUID parentId) {
        Folder folder = new Folder(UUID.randomUUID(), parentId, uniqueFolderName(parentId));
        remote.createFolder(folder, this::notifyChanged);
    }

    @Override
    public void update(Entry expected, Entry replacement) {
        remote.update(expected, replacement, this::notifyChanged);
    }

    @Override
    public void updateFolder(Folder expected, Folder replacement) {
        remote.updateFolder(expected, replacement, this::notifyChanged);
    }

    @Override
    public void moveEntry(UUID entryId, UUID parentId) {
        remote.moveEntry(entryId, parentId, this::notifyChanged);
    }

    @Override
    public void moveFolder(UUID folderId, UUID parentId) {
        remote.moveFolder(folderId, parentId, this::notifyChanged);
    }

    @Override
    public void delete(Set<EntryKey> entries, Set<UUID> folders) {
        remote.delete(entries, folders, this::notifyChanged);
    }

    @Override public void addChangeListener(Runnable listener) { if (listener != null) listeners.add(listener); }
    @Override public void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    @Override
    public void refresh(Runnable completion) {
        remote.refresh(() -> {
            notifyChanged();
            if (completion != null) completion.run();
        });
    }

    private String uniqueKey(UUID parentId, PortType type) {
        String base = type.name().toLowerCase(java.util.Locale.ROOT);
        Set<String> keys = entries().stream()
                .filter(entry -> java.util.Objects.equals(entry.parentId(), parentId) && entry.type() == type)
                .map(Entry::key)
                .collect(java.util.stream.Collectors.toSet());
        if (!keys.contains(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            if (!keys.contains(candidate)) return candidate;
        }
    }

    private String uniqueFolderName(UUID parentId) {
        String base = "folder";
        Set<String> names = folders().stream()
                .filter(folder -> java.util.Objects.equals(folder.parentId(), parentId))
                .map(Folder::name).collect(java.util.stream.Collectors.toSet());
        if (!names.contains(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            if (!names.contains(candidate)) return candidate;
        }
    }

    private void notifyChanged() {
        listeners.forEach(Runnable::run);
    }
}
