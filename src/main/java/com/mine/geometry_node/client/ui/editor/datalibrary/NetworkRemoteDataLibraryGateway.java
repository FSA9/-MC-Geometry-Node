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
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryDocument;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntry;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntryKey;
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
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Remote Data Library control plane; payload bytes use the shared chunked transfer service. */
public final class NetworkRemoteDataLibraryGateway implements RemoteDataLibraryGateway {
    public static final NetworkRemoteDataLibraryGateway INSTANCE = new NetworkRemoteDataLibraryGateway();
    private static final ClientRequestTracker.Group REQUESTS = ClientRequestTracker.group("remote-data-library");
    private final AtomicReference<List<DataLibraryUiRepository.Entry>> snapshot = new AtomicReference<>(List.of());
    private final Deque<Runnable> mutations = new ArrayDeque<>();
    private boolean mutationRunning;

    private NetworkRemoteDataLibraryGateway() {
    }

    @Override public List<DataLibraryUiRepository.Entry> snapshot() { return snapshot.get(); }

    @Override
    public void refresh(Runnable completion) {
        enqueueMutation(() -> refreshNow(() -> finishMutation(completion)));
    }

    @Override
    public void create(DataLibraryUiRepository.Entry entry, Runnable completion) {
        enqueueMutation(() -> upload(List.of(entry), AssetTransferPurpose.DATA_LIBRARY_CREATE,
                () -> finishMutation(completion)));
    }

    @Override
    public void update(DataLibraryUiRepository.Entry entry, Runnable completion) {
        enqueueMutation(() -> upload(List.of(entry), AssetTransferPurpose.DATA_LIBRARY_UPDATE,
                () -> finishMutation(completion)));
    }

    @Override
    public void delete(Set<DataLibraryUiRepository.EntryKey> entries, Runnable completion) {
        enqueueMutation(() -> control(RemoteDataLibraryOperation.DELETE, keys(entries), response -> {
            if (!response.success()) {
                failure(RemoteDataLibraryOperation.DELETE, response.message());
                finishMutation(completion);
                return;
            }
            refreshNow(() -> finishMutation(completion));
        }));
    }

    public void handle(PacketRemoteDataLibraryResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    public void resetConnection() {
        synchronized (mutations) {
            mutations.clear();
            mutationRunning = false;
        }
        REQUESTS.reset();
        snapshot.set(List.of());
    }

    private void upload(List<DataLibraryUiRepository.Entry> entries,
                        AssetTransferPurpose purpose, Runnable completion) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("geometrynode-data-library-", ".json");
            Files.writeString(temporary, DataLibraryJsonCodec.encode(document(entries), registries()), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            deleteQuietly(temporary);
            failure(purpose, exception.getMessage());
            run(completion);
            return;
        }
        Path source = temporary;
        UUID job = ClientAssetTransferService.INSTANCE.submit(List.of(
                ClientAssetTransferRequest.dataLibraryUpload(source, purpose)));
        ClientAssetTransferService.INSTANCE.completion(job).whenComplete((result, error) -> {
            deleteQuietly(source);
            if (error != null || result.files().stream().anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                failure(purpose, transferFailure(result, error, "Data Library upload failed"));
                run(completion);
            } else {
                refreshNow(completion);
            }
        });
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
                    List<DataLibraryUiRepository.Entry> accepted = entries(loaded);
                    snapshot.set(accepted);
                } catch (Exception exception) {
                    failure(RemoteDataLibraryOperation.PREPARE_REFRESH, exception.getMessage());
                } finally {
                    deleteQuietly(target);
                    run(completion);
                }
            });
        });
    }

    private static DataLibraryDocument document(List<DataLibraryUiRepository.Entry> entries) {
        DataLibraryDocument document = new DataLibraryDocument();
        for (DataLibraryUiRepository.Entry entry : entries) {
            document.put(entry.type(), new DataLibraryEntry(entry.id(), entry.name(), entry.value()));
        }
        return document;
    }

    private static List<DataLibraryUiRepository.Entry> entries(DataLibraryLoadResult loaded) {
        loaded.diagnostics().forEach(diagnostic -> GeometryNode.LOGGER.warn(
                "Remote Data Library {}: {}", diagnostic.path(), diagnostic.message()));
        List<DataLibraryUiRepository.Entry> result = new ArrayList<>();
        loaded.document().entriesByType().forEach((type, values) -> values.values().forEach(entry ->
                result.add(new DataLibraryUiRepository.Entry(type, entry.id(), entry.name(), entry.value()))));
        return List.copyOf(result);
    }

    private static Set<DataLibraryEntryKey> keys(Set<DataLibraryUiRepository.EntryKey> keys) {
        return keys.stream().map(key -> new DataLibraryEntryKey(key.type(), key.id()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void control(RemoteDataLibraryOperation operation, Set<DataLibraryEntryKey> keys,
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

    private void enqueueMutation(Runnable mutation) {
        Runnable start = null;
        synchronized (mutations) {
            mutations.addLast(mutation);
            if (!mutationRunning) {
                mutationRunning = true;
                start = mutations.removeFirst();
            }
        }
        if (start != null) start.run();
    }

    private void finishMutation(Runnable completion) {
        Runnable next = null;
        try {
            run(completion);
        } finally {
            synchronized (mutations) {
                if (mutations.isEmpty()) {
                    mutationRunning = false;
                } else {
                    next = mutations.removeFirst();
                }
            }
        }
        if (next != null) next.run();
    }
}
