package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client facade for the server-authoritative Data Library. */
public final class ClientDataLibraryRepository implements DataLibraryUiRepository {
    public static final ClientDataLibraryRepository INSTANCE = new ClientDataLibraryRepository();

    private final RemoteDataLibraryGateway remote;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.ArrayList<Runnable> pendingInitialLoad = new java.util.ArrayList<>();
    private boolean initialLoadInFlight;
    private long connectionGeneration;
    private final java.util.Map<UUID, Integer> pendingUpdates = new java.util.HashMap<>();

    private ClientDataLibraryRepository() { this(NetworkRemoteDataLibraryGateway.INSTANCE); }

    ClientDataLibraryRepository(RemoteDataLibraryGateway remote) {
        this.remote = java.util.Objects.requireNonNull(remote);
    }

    @Override
    public List<Folder> folders() {
        return List.copyOf(remote.folderSnapshot());
    }

    @Override
    public List<Entry> entries() {
        return List.copyOf(remote.snapshot());
    }

    @Override public boolean hasLoadedSnapshot() { return remote.hasLoadedSnapshot(); }

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
            callbacks.forEach(ClientDataLibraryRepository::runCallback);
        });
    }

    public void resetConnection() {
        synchronized (pendingInitialLoad) {
            connectionGeneration++;
            pendingInitialLoad.clear();
            initialLoadInFlight = false;
            pendingUpdates.clear();
        }
    }

    @Override
    public void create(UUID parentId, PortType type) {
        remote.create(() -> new Entry(UUID.randomUUID(), parentId, type,
                uniqueKey(parentId, type), type.getDefaultValue()), this::notifyChanged);
    }

    @Override
    public void createFolder(UUID parentId) {
        remote.createFolder(() -> new Folder(UUID.randomUUID(), parentId, uniqueFolderName(parentId)),
                this::notifyChanged);
    }

    @Override
    public void update(Entry expected, Entry replacement) {
        UUID id = replacement.id();
        long generation;
        synchronized (pendingInitialLoad) {
            generation = connectionGeneration;
            pendingUpdates.merge(id, 1, Integer::sum);
        }
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable completion = () -> {
            if (!finished.compareAndSet(false, true)) return;
            synchronized (pendingInitialLoad) {
                if (generation != connectionGeneration) return;
                pendingUpdates.computeIfPresent(id, (ignored, count) -> count > 1 ? count - 1 : null);
            }
            notifyChanged();
        };
        try {
            remote.update(expected, replacement, completion);
        } catch (RuntimeException failure) {
            completion.run();
            throw failure;
        }
    }

    @Override public boolean isUpdatePending(UUID entryId) {
        synchronized (pendingInitialLoad) { return pendingUpdates.containsKey(entryId); }
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
        listeners.forEach(ClientDataLibraryRepository::runCallback);
    }

    private static void runCallback(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException failure) {
            System.getLogger(ClientDataLibraryRepository.class.getName()).log(
                    System.Logger.Level.WARNING, "Data Library callback failed", failure);
        }
    }
}
