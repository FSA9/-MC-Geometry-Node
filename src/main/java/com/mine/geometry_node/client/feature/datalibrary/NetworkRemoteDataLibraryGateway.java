package com.mine.geometry_node.client.feature.datalibrary;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.platform.ClientThread;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryObjectKey;
import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryObjectFingerprint;
import com.mine.geometry_node.core.engine.system.datalibrary.storage.DataLibraryFileStore;
import com.mine.geometry_node.core.engine.system.datalibrary.storage.DataLibraryJsonCodec;
import com.mine.geometry_node.core.engine.system.datalibrary.storage.DataLibraryLoadResult;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.data.library.PacketRemoteDataLibraryRequest;
import com.mine.geometry_node.core.network.packet.data.library.PacketRemoteDataLibraryResponse;
import com.mine.geometry_node.core.network.packet.data.library.RemoteDataLibraryOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Remote Data Library control plane; document bytes use the shared file transfer service. */
public final class NetworkRemoteDataLibraryGateway implements RemoteDataLibraryGateway {
    public static final NetworkRemoteDataLibraryGateway INSTANCE = new NetworkRemoteDataLibraryGateway();
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("remote-data-library", ClientThread::execute);
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);
    private final Consumer<Runnable> clientQueue;
    private final Deque<Runnable> mutations = new ArrayDeque<>();
    private boolean mutationRunning;
    private long connectionGeneration;

    private NetworkRemoteDataLibraryGateway() {
        this(ClientThread::execute);
    }

    NetworkRemoteDataLibraryGateway(Consumer<Runnable> clientQueue) {
        this.clientQueue = java.util.Objects.requireNonNull(clientQueue);
    }

    @Override public List<DataLibraryRepository.Folder> folderSnapshot() { return snapshot.get().folders(); }

    @Override public List<DataLibraryRepository.Entry> snapshot() { return snapshot.get().entries(); }

    @Override public DataLibraryRepository.Entry findEntry(UUID id) {
        return id == null ? null : snapshot.get().entriesById.get(id);
    }

    @Override public String folderPath(UUID folderId) {
        return folderId == null ? "/" : snapshot.get().folderPaths.getOrDefault(folderId, "/");
    }

    @Override public boolean hasLoadedSnapshot() { return snapshot.get().loaded; }

    @Override
    public void refresh(Runnable completion) {
        enqueueMutation((generation, finished) -> refreshNow(generation, finished), completion);
    }

    @Override
    public void create(Supplier<DataLibraryRepository.Entry> entry, Runnable completion) {
        enqueueMutation((generation, finished) -> upload(generation, List.of(entry.get()), null, AssetTransferPurpose.DATA_LIBRARY_CREATE,
                finished), completion);
    }

    @Override
    public void update(DataLibraryRepository.Entry previous, DataLibraryRepository.Entry entry,
                       Runnable completion) {
        enqueueMutation((generation, finished) -> {
            if (previous == null || !previous.id().equals(entry.id())) {
                failure(AssetTransferPurpose.DATA_LIBRARY_UPDATE, "Entry no longer exists: " + entry.id());
                refreshNow(generation, finished);
                return;
            }
            if (previous.type() != entry.type()) {
                failure(AssetTransferPurpose.DATA_LIBRARY_UPDATE, "Entry type cannot be changed: " + entry.id());
                refreshNow(generation, finished);
                return;
            }
            if (!java.util.Objects.equals(previous.parentId(), entry.parentId())) {
                sendMoveEntry(previous, entry.parentId(), response -> {
                    if (!isGenerationActive(generation)) return;
                    if (!response.success()) {
                        failure(RemoteDataLibraryOperation.MOVE_ENTRY, response.message());
                        refreshNow(generation, finished);
                        return;
                    }
                    if (java.util.Objects.equals(previous.key(), entry.key())
                            && java.util.Objects.equals(previous.value(), entry.value())
                            && java.util.Objects.equals(previous.tags(), entry.tags())
                            && com.mine.geometry_node.core.metadata.DataDictionary.equivalent(previous.data(), entry.data(),
                            com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(null))) {
                        refreshNow(generation, finished);
                        return;
                    }
                    DataLibraryRepository.Entry moved = new DataLibraryRepository.Entry(
                            previous.id(), entry.parentId(), previous.type(), previous.key(), previous.value(),
                            previous.tags(), previous.data());
                    upload(generation, List.of(entry), moved, AssetTransferPurpose.DATA_LIBRARY_UPDATE,
                            finished);
                });
                return;
            }
            upload(generation, List.of(entry), previous, AssetTransferPurpose.DATA_LIBRARY_UPDATE,
                    finished);
        }, completion);
    }

    @Override
    public void delete(Set<DataLibraryRepository.EntryKey> entries, Set<UUID> folders,
                       Runnable completion) {
        Set<DataLibraryObjectKey> objectKeys = new java.util.LinkedHashSet<>(keys(entries));
        objectKeys.addAll(DataLibraryMapper.toFolderKeys(folders));
        enqueueMutation((generation, finished) -> {
            try {
                Snapshot current = snapshot.get();
                var document = DataLibraryMapper.toDocument(current.folders(), current.entries());
                String expected = DataLibraryObjectFingerprint.deletion(
                        document, Set.copyOf(objectKeys), registries());
                control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                        RemoteDataLibraryOperation.DELETE, Set.copyOf(objectKeys),
                        null, null, "", expected), response -> {
                    if (!isGenerationActive(generation)) return;
                    if (!response.success()) failure(RemoteDataLibraryOperation.DELETE, response.message());
                    refreshNow(generation, finished);
                });
            } catch (RuntimeException exception) {
                failure(RemoteDataLibraryOperation.DELETE, exception.getMessage());
                refreshNow(generation, finished);
            }
        }, completion);
    }

    @Override
    public void createFolder(Supplier<DataLibraryRepository.Folder> folderSupplier, Runnable completion) {
        enqueueMutation((generation, finished) -> {
            DataLibraryRepository.Folder folder = folderSupplier.get();
            try {
                control(new PacketRemoteDataLibraryRequest(
                        REQUESTS.nextRequestId(), RemoteDataLibraryOperation.CREATE_FOLDER, Set.of(),
                        null, folder.parentId(), folder.name(), ""), response -> {
                    if (!isGenerationActive(generation)) return;
                    if (!response.success()) failure(RemoteDataLibraryOperation.CREATE_FOLDER, response.message());
                    refreshNow(generation, finished);
                });
            } catch (RuntimeException exception) {
                failure(RemoteDataLibraryOperation.CREATE_FOLDER, exception.getMessage());
                refreshNow(generation, finished);
            }
        }, completion);
    }

    @Override
    public void updateFolder(DataLibraryRepository.Folder previous,
                             DataLibraryRepository.Folder folder, Runnable completion) {
        enqueueMutation((generation, finished) -> {
            if (previous == null || !previous.id().equals(folder.id())) {
                failure(RemoteDataLibraryOperation.UPDATE_FOLDER, "Folder no longer exists: " + folder.id());
                refreshNow(generation, finished);
                return;
            }
            boolean moved = !java.util.Objects.equals(previous.parentId(), folder.parentId());
            RemoteDataLibraryOperation operation = moved
                    ? RemoteDataLibraryOperation.MOVE_FOLDER : RemoteDataLibraryOperation.UPDATE_FOLDER;
            String name = moved ? "" : folder.name();
            String expected = DataLibraryObjectFingerprint.folder(DataLibraryMapper.toCore(previous));
            try {
                control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(), operation, Set.of(),
                        folder.id(), folder.parentId(), name, expected), response -> {
                    if (!isGenerationActive(generation)) return;
                    if (!response.success()) {
                        failure(operation, response.message());
                        refreshNow(generation, finished);
                        return;
                    }
                    if (!moved || java.util.Objects.equals(previous.name(), folder.name())) {
                        refreshNow(generation, finished);
                        return;
                    }
                    DataLibraryRepository.Folder movedFolder = new DataLibraryRepository.Folder(
                            previous.id(), folder.parentId(), previous.name());
                    String renameExpected = DataLibraryObjectFingerprint.folder(
                            DataLibraryMapper.toCore(movedFolder));
                    control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                            RemoteDataLibraryOperation.UPDATE_FOLDER, Set.of(), folder.id(),
                            folder.parentId(), folder.name(), renameExpected), renamed -> {
                        if (!isGenerationActive(generation)) return;
                        if (!renamed.success()) {
                            failure(RemoteDataLibraryOperation.UPDATE_FOLDER, renamed.message());
                        }
                        refreshNow(generation, finished);
                    });
                });
            } catch (RuntimeException exception) {
                failure(operation, exception.getMessage());
                refreshNow(generation, finished);
            }
        }, completion);
    }

    @Override
    public void moveEntry(UUID entryId, UUID parentId, Runnable completion) {
        enqueueMutation((generation, finished) -> {
            DataLibraryRepository.Entry current = snapshot.get().entriesById.get(entryId);
            if (current == null) {
                failure(RemoteDataLibraryOperation.MOVE_ENTRY, "Entry no longer exists: " + entryId);
                refreshNow(generation, finished);
                return;
            }
            if (java.util.Objects.equals(current.parentId(), parentId)) {
                finished.run();
                return;
            }
            sendMoveEntry(current, parentId, response -> {
                if (!isGenerationActive(generation)) return;
                if (!response.success()) failure(RemoteDataLibraryOperation.MOVE_ENTRY, response.message());
                refreshNow(generation, finished);
            });
        }, completion);
    }

    @Override
    public void moveFolder(UUID folderId, UUID parentId, Runnable completion) {
        enqueueMutation((generation, finished) -> {
            DataLibraryRepository.Folder current = snapshot.get().foldersById.get(folderId);
            if (current == null) {
                failure(RemoteDataLibraryOperation.MOVE_FOLDER, "Folder no longer exists: " + folderId);
                refreshNow(generation, finished);
                return;
            }
            if (java.util.Objects.equals(current.parentId(), parentId)) {
                finished.run();
                return;
            }
            String expected = DataLibraryObjectFingerprint.folder(DataLibraryMapper.toCore(current));
            control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                    RemoteDataLibraryOperation.MOVE_FOLDER, Set.of(), current.id(), parentId, "", expected),
                    response -> {
                        if (!isGenerationActive(generation)) return;
                        if (!response.success()) failure(RemoteDataLibraryOperation.MOVE_FOLDER, response.message());
                        refreshNow(generation, finished);
                    });
        }, completion);
    }

    public void handle(PacketRemoteDataLibraryResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    public void resetConnection() {
        synchronized (mutations) {
            mutations.clear();
            connectionGeneration++;
            mutationRunning = false;
            snapshot.set(Snapshot.EMPTY);
        }
        REQUESTS.reset();
    }

    private void upload(long generation, List<DataLibraryRepository.Entry> entries,
                        DataLibraryRepository.Entry previous,
                        AssetTransferPurpose purpose, Runnable completion) {
        if (!isGenerationActive(generation)) return;
        Path temporary = null;
        try {
            temporary = Files.createTempFile("geometrynode-data-library-", ".json");
            var document = DataLibraryMapper.toEntryMutationDocument(snapshot.get().folders(), entries);
            String json = previous == null
                    ? DataLibraryJsonCodec.encode(document, registries())
                    : DataLibraryJsonCodec.encodeMutation(document, registries(), Map.of(previous.id(),
                    DataLibraryObjectFingerprint.entry(DataLibraryMapper.toCore(previous), registries())));
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            deleteQuietly(temporary);
            failure(purpose, exception.getMessage());
            refreshNow(generation, completion);
            return;
        }
        Path source = temporary;
        if (!isGenerationActive(generation)) {
            deleteQuietly(source);
            return;
        }
        UUID job = ClientAssetTransferService.INSTANCE.submit(List.of(
                DataLibraryTransferRequests.upload(source, purpose)));
        ClientAssetTransferService.INSTANCE.onCompletion(job, (result, error) -> {
            deleteQuietly(source);
            if (!isGenerationActive(generation)) return;
            if (error != null || result.files().stream().anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                failure(purpose, transferFailure(result, error, "Data Library upload failed"));
                refreshNow(generation, completion);
            } else {
                refreshNow(generation, completion);
            }
        });
    }

    private static void sendMoveEntry(DataLibraryRepository.Entry previous, UUID parentId,
                                      Consumer<PacketRemoteDataLibraryResponse> completion) {
        String expected = DataLibraryObjectFingerprint.entry(DataLibraryMapper.toCore(previous), registries());
        control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                RemoteDataLibraryOperation.MOVE_ENTRY, Set.of(), previous.id(), parentId, "", expected), completion);
    }

    private void refreshNow(long generation, Runnable completion) {
        if (!isGenerationActive(generation)) return;
        Path target;
        try {
            target = Files.createTempFile("geometrynode-data-library-download-", ".json");
            Files.deleteIfExists(target);
        } catch (Exception exception) {
            failure(AssetTransferPurpose.DATA_LIBRARY_DOWNLOAD, exception.getMessage());
            run(completion);
            return;
        }
        if (!isGenerationActive(generation)) {
            deleteQuietly(target);
            return;
        }
        UUID job = ClientAssetTransferService.INSTANCE.submit(List.of(
                DataLibraryTransferRequests.download(target)));
        ClientAssetTransferService.INSTANCE.onCompletion(job, (result, error) -> {
            try {
                if (!isGenerationActive(generation)) return;
                if (error != null || result.files().stream().anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                    rejectSnapshot(generation, result);
                    failure(AssetTransferPurpose.DATA_LIBRARY_DOWNLOAD,
                            transferFailure(result, error, "Data Library download failed"));
                    return;
                }
                if (!Files.isRegularFile(target)) {
                    throw new java.io.IOException("Downloaded Data Library file is missing");
                }
                DataLibraryLoadResult loaded = DataLibraryFileStore.read(target, registries());
                publishLoadedSnapshot(generation, loaded);
            } catch (Exception exception) {
                if (isGenerationActive(generation)) {
                    failure(AssetTransferPurpose.DATA_LIBRARY_DOWNLOAD, exception.getMessage());
                }
            } finally {
                deleteQuietly(target);
                if (isGenerationActive(generation)) run(completion);
            }
        });
    }

    void publishLoadedSnapshot(long generation, DataLibraryLoadResult loaded) {
        if (!isGenerationActive(generation)) return;
        loaded.requireValidDocument();
        publishSnapshot(generation, new Snapshot(DataLibraryMapper.folders(loaded),
                DataLibraryMapper.fromDocument(loaded), true));
    }

    void rejectSnapshot(long generation, AssetTransferJobSnapshot result) {
        if (result != null && result.files().stream().anyMatch(file -> file.failure() != null
                && file.failure().code() == AssetTransferErrorCode.PERMISSION_DENIED)) {
            publishSnapshot(generation, Snapshot.EMPTY);
        }
    }

    void publishSnapshot(long generation, Snapshot value) {
        synchronized (mutations) {
            if (generation == connectionGeneration) snapshot.set(value);
        }
    }

    private static Set<DataLibraryObjectKey> keys(Set<DataLibraryRepository.EntryKey> keys) {
        return DataLibraryMapper.toKeys(keys);
    }

    private static void control(PacketRemoteDataLibraryRequest request,
                                Consumer<PacketRemoteDataLibraryResponse> completion) {
        int requestId = request.requestId();
        REQUESTS.register(requestId, PacketRemoteDataLibraryResponse.class, completion,
                () -> completion.accept(new PacketRemoteDataLibraryResponse(
                        requestId, false, "Data Library request timed out", "")));
        try {
            NetworkHandler.sendToServer(request);
        } catch (RuntimeException exception) {
            REQUESTS.cancel(requestId);
            ClientThread.execute(() -> completion.accept(
                    new PacketRemoteDataLibraryResponse(requestId, false, exception.getMessage(), "")));
        }
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) throw new IllegalStateException("A client world is required");
        return minecraft.level.registryAccess();
    }

    private static void failure(Object operation, String message) {
        GeometryNode.LOGGER.warn("Remote Data Library operation {} failed: {}", operation, message);
    }

    private static String transferFailure(AssetTransferJobSnapshot result, Throwable error, String fallback) {
        if (error != null) {
            String message = rootMessage(error);
            return message.isBlank() ? fallback : message;
        }
        if (result == null) return fallback;
        for (AssetTransferFileSnapshot file : result.files()) {
            if (file.state() == AssetTransferState.COMPLETED) continue;
            if (file.failure() == null) return fallback + " [state=" + file.state() + "]";
            StringBuilder message = new StringBuilder(fallback)
                    .append(" [state=").append(file.state())
                    .append(", code=").append(file.failure().code())
                    .append(", message=").append(file.failure().messageKey()).append(']');
            if (!file.failure().detail().isBlank()) {
                message.append(": ").append(file.failure().detail());
            }
            return message.toString();
        }
        return fallback;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null || current.getMessage() == null) return "";
        return current.getMessage();
    }

    private static void run(Runnable completion) { if (completion != null) completion.run(); }
    private static void deleteQuietly(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (Exception ignored) { } }

    private void enqueueMutation(BiConsumer<Long, Runnable> mutation, Runnable completion) {
        Runnable start = null;
        synchronized (mutations) {
            long generation = connectionGeneration;
            AtomicBoolean completed = new AtomicBoolean();
            Runnable finished = () -> {
                if (completed.compareAndSet(false, true)) finishMutation(generation, completion);
            };
            mutations.addLast(() -> {
                if (!isGenerationActive(generation)) return;
                try {
                    mutation.accept(generation, finished);
                } catch (RuntimeException exception) {
                    System.getLogger(NetworkRemoteDataLibraryGateway.class.getName()).log(
                            System.Logger.Level.WARNING, "Remote Data Library mutation failed", exception);
                    finished.run();
                }
            });
            if (!mutationRunning) {
                mutationRunning = true;
                start = mutations.removeFirst();
            }
        }
        if (start != null) clientQueue.accept(start);
    }

    private void finishMutation(long generation, Runnable completion) {
        if (!isGenerationActive(generation)) return;
        Runnable next = null;
        try {
            run(completion);
        } finally {
            synchronized (mutations) {
                if (generation != connectionGeneration) return;
                if (mutations.isEmpty()) {
                    mutationRunning = false;
                } else {
                    next = mutations.removeFirst();
                }
            }
            if (next != null) clientQueue.accept(next);
        }
    }

    private boolean isGenerationActive(long generation) {
        synchronized (mutations) {
            return generation == connectionGeneration;
        }
    }

    static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), false);
        private final List<DataLibraryRepository.Folder> folders;
        private final List<DataLibraryRepository.Entry> entries;
        private final Map<UUID, DataLibraryRepository.Entry> entriesById;
        private final Map<UUID, DataLibraryRepository.Folder> foldersById;
        private final Map<UUID, String> folderPaths;
        private final boolean loaded;

        Snapshot(List<DataLibraryRepository.Folder> folders,
                 List<DataLibraryRepository.Entry> entries, boolean loaded) {
            this.folders = List.copyOf(folders);
            this.entries = List.copyOf(entries);
            this.loaded = loaded;
            this.entriesById = this.entries.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            DataLibraryRepository.Entry::id, entry -> entry));
            this.foldersById = this.folders.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            DataLibraryRepository.Folder::id, folder -> folder));
            Map<UUID, String> paths = new java.util.HashMap<>();
            for (DataLibraryRepository.Folder folder : this.folders) {
                paths.put(folder.id(), resolveFolderPath(folder.id(), this.foldersById));
            }
            this.folderPaths = Map.copyOf(paths);
        }

        private List<DataLibraryRepository.Folder> folders() { return folders; }
        private List<DataLibraryRepository.Entry> entries() { return entries; }

        private static String resolveFolderPath(UUID folderId,
                                                Map<UUID, DataLibraryRepository.Folder> folders) {
            java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
            java.util.HashSet<UUID> visited = new java.util.HashSet<>();
            UUID current = folderId;
            while (current != null && visited.add(current)) {
                DataLibraryRepository.Folder folder = folders.get(current);
                if (folder == null) break;
                names.addFirst(folder.name());
                current = folder.parentId();
            }
            return names.isEmpty() ? "/" : "/" + String.join("/", names);
        }
    }
}
