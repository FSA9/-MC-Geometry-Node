package com.mine.geometry_node.client.feature.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client facade for the server-authoritative Data Library. */
public final class ClientDataLibraryRepository implements DataLibraryRepository {
    public static final ClientDataLibraryRepository INSTANCE = new ClientDataLibraryRepository();

    private final RemoteDataLibraryGateway remote;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.ArrayList<Runnable> pendingInitialLoad = new java.util.ArrayList<>();
    private boolean initialLoadInFlight;
    private long connectionGeneration;
    private int connectionResets;
    private final java.util.Map<UUID, Integer> pendingUpdates = new java.util.HashMap<>();
    private final java.util.Map<UUID, String> tagUpdateMessages = new java.util.HashMap<>();
    private final java.util.Map<UUID, String> dataUpdateMessages = new java.util.HashMap<>();
    private final java.util.Map<UUID, java.util.function.Consumer<String>> dataCompletions = new java.util.HashMap<>();

    private ClientDataLibraryRepository() { this(NetworkRemoteDataLibraryGateway.INSTANCE); }

    public ClientDataLibraryRepository(RemoteDataLibraryGateway remote) {
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

    @Override public boolean hasLoadedSnapshot() {
        synchronized (pendingInitialLoad) { return connectionResets == 0 && remote.hasLoadedSnapshot(); }
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
            callbacks.forEach(ClientDataLibraryRepository::runCallback);
        });
    }

    public void resetConnection() {
        java.util.List<java.util.function.Consumer<String>> cancelled;
        synchronized (pendingInitialLoad) {
            connectionGeneration++;
            connectionResets++;
            pendingInitialLoad.clear();
            initialLoadInFlight = false;
            pendingUpdates.clear();
            tagUpdateMessages.clear();
            dataUpdateMessages.clear();
            cancelled = java.util.List.copyOf(dataCompletions.values());
            dataCompletions.clear();
        }
        try { cancelled.forEach(callback -> completeData(callback, "database.data_update_failed")); }
        finally { synchronized (pendingInitialLoad) { connectionResets--; } }
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
        update(expected, replacement, null);
    }

    private void update(Entry expected, Entry replacement, java.util.function.Consumer<String> ownDataCompletion) {
        UUID id = replacement.id();
        boolean tagOnly = expected != null && expected.id().equals(id)
                && java.util.Objects.equals(expected.parentId(), replacement.parentId())
                && expected.type() == replacement.type()
                && java.util.Objects.equals(expected.key(), replacement.key())
                && java.util.Objects.equals(expected.value(), replacement.value())
                && !expected.tags().equals(replacement.tags());
        boolean dataChanged = expected != null && expected.id().equals(id)
                && !com.mine.geometry_node.core.metadata.DataDictionary.equivalent(expected.data(), replacement.data(),
                com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(null));
        long generation;
        synchronized (pendingInitialLoad) {
            generation = connectionGeneration;
            pendingUpdates.merge(id, 1, Integer::sum);
            if (ownDataCompletion != null) dataCompletions.put(id, ownDataCompletion);
            if (tagOnly) tagUpdateMessages.remove(id);
            if (dataChanged) dataUpdateMessages.remove(id);
        }
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable completion = () -> {
            if (!finished.compareAndSet(false, true)) return;
            java.util.function.Consumer<String> dataCompletion;
            String dataMessage;
            synchronized (pendingInitialLoad) {
                if (generation != connectionGeneration) return;
                pendingUpdates.computeIfPresent(id, (ignored, count) -> count > 1 ? count - 1 : null);
                if (tagOnly) {
                    Entry current = remote.findEntry(id);
                    if (!remote.hasLoadedSnapshot() || current == null || !current.tags().equals(replacement.tags())) {
                        tagUpdateMessages.put(id, "database.tag_update_failed");
                    } else {
                        tagUpdateMessages.remove(id);
                    }
                }
                if (dataChanged) {
                    Entry current = remote.findEntry(id);
                    if (!remote.hasLoadedSnapshot() || current == null
                            || !com.mine.geometry_node.core.metadata.DataDictionary.equivalent(current.data(), replacement.data(),
                            com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(null))) {
                        dataUpdateMessages.put(id, "database.data_update_failed");
                    } else dataUpdateMessages.remove(id);
                }
                dataCompletion = ownDataCompletion != null && dataCompletions.remove(id, ownDataCompletion)
                        ? ownDataCompletion : null;
                dataMessage = dataUpdateMessages.getOrDefault(id, "");
            }
            completeData(dataCompletion, dataMessage);
            notifyChanged();
        };
        try {
            notifyChanged();
            synchronized (pendingInitialLoad) {
                if (generation != connectionGeneration) return;
            }
            remote.update(expected, replacement, completion);
        } catch (RuntimeException failure) {
            completion.run();
            throw failure;
        }
    }

    @Override public void replaceData(Entry expected, java.util.Map<String, Object> replacement) {
        replaceData(expected, replacement, null);
    }

    @Override public void replaceData(Entry expected, java.util.Map<String, Object> replacement,
                                      java.util.function.Consumer<String> completion) {
        // Validate and snapshot before publishing a callback or busy state.
        var policy = com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(null);
        var data = com.mine.geometry_node.core.metadata.DataDictionary.snapshot(replacement, policy);
        boolean unchanged;
        synchronized (pendingInitialLoad) {
            if (!canEditData(expected) || isUpdatePending(expected.id()) || !expected.sameContent(findEntry(expected.id())))
                throw new IllegalStateException("Data Library entry changed, is unavailable, or is still saving");
            unchanged = com.mine.geometry_node.core.metadata.DataDictionary.equivalent(expected.data(), data, policy);
            if (unchanged) dataUpdateMessages.remove(expected.id());
        }
        if (unchanged) {
            completeData(completion, "");
            return;
        }
        var delivered = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> ownCompletion = completion == null ? null : message -> {
            if (delivered.compareAndSet(false, true)) completeData(completion, message);
        };
        try {
            update(expected, new Entry(expected.id(), expected.parentId(), expected.type(), expected.key(),
                    expected.value(), expected.tags(), data), ownCompletion);
        } catch (RuntimeException failure) {
            synchronized (pendingInitialLoad) { dataCompletions.remove(expected.id(), ownCompletion); }
            completeData(ownCompletion, "database.data_update_failed");
            // update() already completes failed submissions. A second throw would make UI adapters
            // report a second completion from their catch block after the failure callback.
            if (completion == null) throw failure;
        }
    }

    private static void completeData(java.util.function.Consumer<String> completion, String message) {
        if (completion != null) runCallback(() -> completion.accept(message));
    }

    @Override public boolean isUpdatePending(UUID entryId) {
        synchronized (pendingInitialLoad) { return pendingUpdates.containsKey(entryId); }
    }

    @Override public String tagUpdateMessage(UUID entryId) {
        synchronized (pendingInitialLoad) { return tagUpdateMessages.getOrDefault(entryId, ""); }
    }

    @Override public String dataUpdateMessage(UUID entryId) {
        synchronized (pendingInitialLoad) { return dataUpdateMessages.getOrDefault(entryId, ""); }
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
        synchronized (pendingInitialLoad) {
            if (!dataUpdateMessages.isEmpty() && remote.hasLoadedSnapshot()) {
                dataUpdateMessages.keySet().removeIf(id -> remote.findEntry(id) == null);
            }
            if (!tagUpdateMessages.isEmpty() && remote.hasLoadedSnapshot()) {
                tagUpdateMessages.keySet().removeIf(id -> remote.findEntry(id) == null);
            }
        }
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
