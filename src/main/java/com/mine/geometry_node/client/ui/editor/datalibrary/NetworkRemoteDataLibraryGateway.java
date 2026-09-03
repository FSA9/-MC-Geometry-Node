package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectKey;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectFingerprint;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryFileStore;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryJsonCodec;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryLoadResult;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Remote Data Library control plane; payload bytes use the shared chunked transfer service. */
public final class NetworkRemoteDataLibraryGateway implements RemoteDataLibraryGateway {
    public static final NetworkRemoteDataLibraryGateway INSTANCE = new NetworkRemoteDataLibraryGateway();
    private static final ClientRequestTracker.Group REQUESTS = ClientRequestTracker.group("remote-data-library");
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);
    private final Deque<Runnable> mutations = new ArrayDeque<>();
    private boolean mutationRunning;
    private long connectionGeneration;

    private NetworkRemoteDataLibraryGateway() {
    }

    @Override public List<DataLibraryUiRepository.Folder> folderSnapshot() { return snapshot.get().folders(); }

    @Override public List<DataLibraryUiRepository.Entry> snapshot() { return snapshot.get().entries(); }

    @Override public DataLibraryUiRepository.Entry findEntry(UUID id) {
        return id == null ? null : snapshot.get().entriesById.get(id);
    }

    @Override public String folderPath(UUID folderId) {
        return folderId == null ? "/" : snapshot.get().folderPaths.getOrDefault(folderId, "/");
    }

    @Override public boolean hasLoadedSnapshot() { return snapshot.get().loaded; }

    @Override
    public void refresh(Runnable completion) {
        enqueueMutation(generation -> refreshNow(() -> finishMutation(generation, completion)));
    }

    @Override
    public void create(DataLibraryUiRepository.Entry entry, Runnable completion) {
        enqueueMutation(generation -> upload(List.of(entry), null, AssetTransferPurpose.DATA_LIBRARY_CREATE,
                () -> finishMutation(generation, completion)));
    }

    @Override
    public void update(DataLibraryUiRepository.Entry previous, DataLibraryUiRepository.Entry entry,
                       Runnable completion) {
        enqueueMutation(generation -> {
            if (previous == null || !previous.id().equals(entry.id())) {
                failure(AssetTransferPurpose.DATA_LIBRARY_UPDATE, "Entry no longer exists: " + entry.id());
                refreshNow(() -> finishMutation(generation, completion));
                return;
            }
            if (previous.type() != entry.type()) {
                failure(AssetTransferPurpose.DATA_LIBRARY_UPDATE, "Entry type cannot be changed: " + entry.id());
                refreshNow(() -> finishMutation(generation, completion));
                return;
            }
            if (!java.util.Objects.equals(previous.parentId(), entry.parentId())) {
                sendMoveEntry(previous, entry.parentId(), response -> {
                    if (!response.success()) {
                        failure(RemoteDataLibraryOperation.MOVE_ENTRY, response.message());
                        refreshNow(() -> finishMutation(generation, completion));
                        return;
                    }
                    if (java.util.Objects.equals(previous.key(), entry.key())
                            && java.util.Objects.equals(previous.value(), entry.value())) {
                        refreshNow(() -> finishMutation(generation, completion));
                        return;
                    }
                    DataLibraryUiRepository.Entry moved = new DataLibraryUiRepository.Entry(
                            previous.id(), entry.parentId(), previous.type(), previous.key(), previous.value());
                    upload(List.of(entry), moved, AssetTransferPurpose.DATA_LIBRARY_UPDATE,
                            () -> finishMutation(generation, completion));
                });
                return;
            }
            upload(List.of(entry), previous, AssetTransferPurpose.DATA_LIBRARY_UPDATE,
                    () -> finishMutation(generation, completion));
        });
    }

    @Override
    public void delete(Set<DataLibraryUiRepository.EntryKey> entries, Set<UUID> folders,
                       Runnable completion) {
        Set<DataLibraryObjectKey> objectKeys = new java.util.LinkedHashSet<>(keys(entries));
        objectKeys.addAll(DataLibraryUiMapper.toFolderKeys(folders));
        enqueueMutation(generation -> {
            try {
                Snapshot current = snapshot.get();
                var document = DataLibraryUiMapper.toDocument(current.folders(), current.entries());
                String expected = DataLibraryObjectFingerprint.deletion(
                        document, Set.copyOf(objectKeys), registries());
                control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                        RemoteDataLibraryOperation.DELETE, Set.copyOf(objectKeys),
                        null, null, "", expected), response -> {
                    if (!response.success()) failure(RemoteDataLibraryOperation.DELETE, response.message());
                    refreshNow(() -> finishMutation(generation, completion));
                });
            } catch (RuntimeException exception) {
                failure(RemoteDataLibraryOperation.DELETE, exception.getMessage());
                refreshNow(() -> finishMutation(generation, completion));
            }
        });
    }

    @Override
    public void createFolder(DataLibraryUiRepository.Folder folder, Runnable completion) {
        enqueueMutation(generation -> {
            try {
                control(new PacketRemoteDataLibraryRequest(
                        REQUESTS.nextRequestId(), RemoteDataLibraryOperation.CREATE_FOLDER, Set.of(),
                        null, folder.parentId(), folder.name(), ""), response -> {
                    if (!response.success()) failure(RemoteDataLibraryOperation.CREATE_FOLDER, response.message());
                    refreshNow(() -> finishMutation(generation, completion));
                });
            } catch (RuntimeException exception) {
                failure(RemoteDataLibraryOperation.CREATE_FOLDER, exception.getMessage());
                refreshNow(() -> finishMutation(generation, completion));
            }
        });
    }

    @Override
    public void updateFolder(DataLibraryUiRepository.Folder previous,
                             DataLibraryUiRepository.Folder folder, Runnable completion) {
        enqueueMutation(generation -> {
            if (previous == null || !previous.id().equals(folder.id())) {
                failure(RemoteDataLibraryOperation.UPDATE_FOLDER, "Folder no longer exists: " + folder.id());
                refreshNow(() -> finishMutation(generation, completion));
                return;
            }
            boolean moved = !java.util.Objects.equals(previous.parentId(), folder.parentId());
            RemoteDataLibraryOperation operation = moved
                    ? RemoteDataLibraryOperation.MOVE_FOLDER : RemoteDataLibraryOperation.UPDATE_FOLDER;
            String name = moved ? "" : folder.name();
            String expected = DataLibraryObjectFingerprint.folder(DataLibraryUiMapper.toCore(previous));
            try {
                control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(), operation, Set.of(),
                        folder.id(), folder.parentId(), name, expected), response -> {
                    if (!response.success()) {
                        failure(operation, response.message());
                        refreshNow(() -> finishMutation(generation, completion));
                        return;
                    }
                    if (!moved || java.util.Objects.equals(previous.name(), folder.name())) {
                        refreshNow(() -> finishMutation(generation, completion));
                        return;
                    }
                    DataLibraryUiRepository.Folder movedFolder = new DataLibraryUiRepository.Folder(
                            previous.id(), folder.parentId(), previous.name());
                    String renameExpected = DataLibraryObjectFingerprint.folder(
                            DataLibraryUiMapper.toCore(movedFolder));
                    control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                            RemoteDataLibraryOperation.UPDATE_FOLDER, Set.of(), folder.id(),
                            folder.parentId(), folder.name(), renameExpected), renamed -> {
                        if (!renamed.success()) {
                            failure(RemoteDataLibraryOperation.UPDATE_FOLDER, renamed.message());
                        }
                        refreshNow(() -> finishMutation(generation, completion));
                    });
                });
            } catch (RuntimeException exception) {
                failure(operation, exception.getMessage());
                refreshNow(() -> finishMutation(generation, completion));
            }
        });
    }

    @Override
    public void moveEntry(UUID entryId, UUID parentId, Runnable completion) {
        enqueueMutation(generation -> {
            DataLibraryUiRepository.Entry current = snapshot.get().entriesById.get(entryId);
            if (current == null) {
                failure(RemoteDataLibraryOperation.MOVE_ENTRY, "Entry no longer exists: " + entryId);
                refreshNow(() -> finishMutation(generation, completion));
                return;
            }
            if (java.util.Objects.equals(current.parentId(), parentId)) {
                finishMutation(generation, completion);
                return;
            }
            sendMoveEntry(current, parentId, response -> {
                if (!response.success()) failure(RemoteDataLibraryOperation.MOVE_ENTRY, response.message());
                refreshNow(() -> finishMutation(generation, completion));
            });
        });
    }

    @Override
    public void moveFolder(UUID folderId, UUID parentId, Runnable completion) {
        enqueueMutation(generation -> {
            DataLibraryUiRepository.Folder current = snapshot.get().foldersById.get(folderId);
            if (current == null) {
                failure(RemoteDataLibraryOperation.MOVE_FOLDER, "Folder no longer exists: " + folderId);
                refreshNow(() -> finishMutation(generation, completion));
                return;
            }
            if (java.util.Objects.equals(current.parentId(), parentId)) {
                finishMutation(generation, completion);
                return;
            }
            String expected = DataLibraryObjectFingerprint.folder(DataLibraryUiMapper.toCore(current));
            control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                    RemoteDataLibraryOperation.MOVE_FOLDER, Set.of(), current.id(), parentId, "", expected),
                    response -> {
                        if (!response.success()) failure(RemoteDataLibraryOperation.MOVE_FOLDER, response.message());
                        refreshNow(() -> finishMutation(generation, completion));
                    });
        });
    }

    public void handle(PacketRemoteDataLibraryResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    public void resetConnection() {
        synchronized (mutations) {
            mutations.clear();
            connectionGeneration++;
            mutationRunning = false;
        }
        REQUESTS.reset();
        snapshot.set(Snapshot.EMPTY);
    }

    private void upload(List<DataLibraryUiRepository.Entry> entries,
                        DataLibraryUiRepository.Entry previous,
                        AssetTransferPurpose purpose, Runnable completion) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("geometrynode-data-library-", ".json");
            var document = DataLibraryUiMapper.toEntryMutationDocument(snapshot.get().folders(), entries);
            String json = previous == null
                    ? DataLibraryJsonCodec.encode(document, registries())
                    : DataLibraryJsonCodec.encodeMutation(document, registries(), Map.of(previous.id(),
                    DataLibraryObjectFingerprint.entry(DataLibraryUiMapper.toCore(previous), registries())));
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            deleteQuietly(temporary);
            failure(purpose, exception.getMessage());
            refreshNow(completion);
            return;
        }
        Path source = temporary;
        UUID job = ClientAssetTransferService.INSTANCE.submit(List.of(
                ClientAssetTransferRequest.dataLibraryUpload(source, purpose)));
        ClientAssetTransferService.INSTANCE.completion(job).whenComplete((result, error) -> {
            deleteQuietly(source);
            if (error != null || result.files().stream().anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                failure(purpose, transferFailure(result, error, "Data Library upload failed"));
                refreshNow(completion);
            } else {
                refreshNow(completion);
            }
        });
    }

    private static void sendMoveEntry(DataLibraryUiRepository.Entry previous, UUID parentId,
                                      Consumer<PacketRemoteDataLibraryResponse> completion) {
        String expected = DataLibraryObjectFingerprint.entry(DataLibraryUiMapper.toCore(previous), registries());
        control(new PacketRemoteDataLibraryRequest(REQUESTS.nextRequestId(),
                RemoteDataLibraryOperation.MOVE_ENTRY, Set.of(), previous.id(), parentId, "", expected), completion);
    }

    private void refreshNow(Runnable completion) {
        control(RemoteDataLibraryOperation.PREPARE_REFRESH, Set.of(), prepared -> {
            if (!prepared.success()) {
                failure(RemoteDataLibraryOperation.PREPARE_REFRESH, prepared.message());
                run(completion);
                return;
            }
            Path target;
            try {
                target = Files.createTempFile("geometrynode-data-library-download-", ".json");
                Files.deleteIfExists(target);
            } catch (Exception exception) {
                failure(RemoteDataLibraryOperation.PREPARE_REFRESH, exception.getMessage());
                run(completion);
                return;
            }
            UUID job = ClientAssetTransferService.INSTANCE.submit(List.of(
                    ClientAssetTransferRequest.dataLibraryDownload(prepared.token(), target)));
            ClientAssetTransferService.INSTANCE.completion(job).whenComplete((result, error) -> {
                try {
                    if (error != null || result.files().stream().anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                        failure(RemoteDataLibraryOperation.PREPARE_REFRESH,
                                transferFailure(result, error, "staging download failed"));
                        return;
                    }
                    DataLibraryLoadResult loaded = DataLibraryFileStore.read(target, registries());
                    snapshot.set(new Snapshot(DataLibraryUiMapper.folders(loaded),
                            DataLibraryUiMapper.fromDocument(loaded), true));
                } catch (Exception exception) {
                    failure(RemoteDataLibraryOperation.PREPARE_REFRESH, exception.getMessage());
                } finally {
                    deleteQuietly(target);
                    run(completion);
                }
            });
        });
    }

    private static Set<DataLibraryObjectKey> keys(Set<DataLibraryUiRepository.EntryKey> keys) {
        return DataLibraryUiMapper.toKeys(keys);
    }

    private static void control(RemoteDataLibraryOperation operation, Set<DataLibraryObjectKey> keys,
                                Consumer<PacketRemoteDataLibraryResponse> completion) {
        int requestId = REQUESTS.nextRequestId();
        REQUESTS.register(requestId, PacketRemoteDataLibraryResponse.class, completion, () -> {
            Runnable fail = () -> completion.accept(new PacketRemoteDataLibraryResponse(
                    requestId, false, "Data Library request timed out", ""));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.execute(fail); else fail.run();
        });
        try {
            NetworkHandler.sendToServer(new PacketRemoteDataLibraryRequest(requestId, operation, keys));
        } catch (RuntimeException exception) {
            REQUESTS.cancel(requestId);
            completion.accept(new PacketRemoteDataLibraryResponse(
                    requestId, false, exception.getMessage(), ""));
        }
    }

    private static void control(PacketRemoteDataLibraryRequest request,
                                Consumer<PacketRemoteDataLibraryResponse> completion) {
        int requestId = request.requestId();
        REQUESTS.register(requestId, PacketRemoteDataLibraryResponse.class, completion, () -> {
            Runnable fail = () -> completion.accept(new PacketRemoteDataLibraryResponse(
                    requestId, false, "Data Library request timed out", ""));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.execute(fail); else fail.run();
        });
        try {
            NetworkHandler.sendToServer(request);
        } catch (RuntimeException exception) {
            REQUESTS.cancel(requestId);
            completion.accept(new PacketRemoteDataLibraryResponse(requestId, false, exception.getMessage(), ""));
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

    private void enqueueMutation(LongConsumer mutation) {
        Runnable start = null;
        synchronized (mutations) {
            long generation = connectionGeneration;
            mutations.addLast(() -> {
                if (isGenerationActive(generation)) mutation.accept(generation);
            });
            if (!mutationRunning) {
                mutationRunning = true;
                start = mutations.removeFirst();
            }
        }
        if (start != null) start.run();
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
        }
        if (next != null) next.run();
    }

    private boolean isGenerationActive(long generation) {
        synchronized (mutations) {
            return generation == connectionGeneration;
        }
    }

    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), false);
        private final List<DataLibraryUiRepository.Folder> folders;
        private final List<DataLibraryUiRepository.Entry> entries;
        private final Map<UUID, DataLibraryUiRepository.Entry> entriesById;
        private final Map<UUID, DataLibraryUiRepository.Folder> foldersById;
        private final Map<UUID, String> folderPaths;
        private final boolean loaded;

        private Snapshot(List<DataLibraryUiRepository.Folder> folders,
                         List<DataLibraryUiRepository.Entry> entries, boolean loaded) {
            this.folders = List.copyOf(folders);
            this.entries = List.copyOf(entries);
            this.loaded = loaded;
            this.entriesById = this.entries.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            DataLibraryUiRepository.Entry::id, entry -> entry));
            this.foldersById = this.folders.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            DataLibraryUiRepository.Folder::id, folder -> folder));
            Map<UUID, String> paths = new java.util.HashMap<>();
            for (DataLibraryUiRepository.Folder folder : this.folders) {
                paths.put(folder.id(), resolveFolderPath(folder.id(), this.foldersById));
            }
            this.folderPaths = Map.copyOf(paths);
        }

        private List<DataLibraryUiRepository.Folder> folders() { return folders; }
        private List<DataLibraryUiRepository.Entry> entries() { return entries; }

        private static String resolveFolderPath(UUID folderId,
                                                Map<UUID, DataLibraryUiRepository.Folder> folders) {
            java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
            java.util.HashSet<UUID> visited = new java.util.HashSet<>();
            UUID current = folderId;
            while (current != null && visited.add(current)) {
                DataLibraryUiRepository.Folder folder = folders.get(current);
                if (folder == null) break;
                names.addFirst(folder.name());
                current = folder.parentId();
            }
            return names.isEmpty() ? "/" : "/" + String.join("/", names);
        }
    }
}
