package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.PreviewSourceChangedException;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.PreviewUnavailableException;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerAssetPreviewGenerator;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerAssetPreviewGeneratorRegistry;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewResponse;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAssetPreviewService implements AutoCloseable {
    public static final ServerAssetPreviewService INSTANCE = new ServerAssetPreviewService();

    private static final int MAX_ACTIVE_PER_PLAYER = 8;
    private static final int MAX_ACTIVE_PER_SERVER = 64;

    private final ServerAssetPreviewStore store = new ServerAssetPreviewStore();
    private final ServerAssetPreviewGeneratorRegistry generators = new ServerAssetPreviewGeneratorRegistry();
    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-Preview-ServerIO", 2, 64);
    private final Map<MinecraftServer, ServerState> states = new ConcurrentHashMap<>();
    private boolean initialized;

    private ServerAssetPreviewService() {
        generators.registerBuiltins(store);
    }

    public void registerGenerator(AssetPreviewKind kind, ServerAssetPreviewGenerator generator) {
        generators.register(kind, generator);
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        ServerAssetPreviewAssociations.init();
        PlayerEvent.PLAYER_QUIT.register(player -> cancelPlayer(
                player.level().getServer(), player.getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> closeServer(event.getServer()));
    }

    public void handleRequest(ServerPlayer player, PacketAssetPreviewRequest packet) {
        UUID requestId = packet.request().requestId();
        if (!RemoteAssetPermissions.canDownloadAssets(player)) {
            result(player, requestId, AssetPreviewResultCode.PERMISSION_DENIED, "permission_denied");
            return;
        }

        MinecraftServer server = player.level().getServer();
        ServerState state = states.computeIfAbsent(server, ServerState::new);
        RequestKey key = new RequestKey(player.getUUID(), requestId);
        if (state.active.containsKey(key)) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "duplicate_request");
            return;
        }
        if (state.active.size() >= MAX_ACTIVE_PER_SERVER) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "server_busy");
            return;
        }
        long playerRequests = state.active.keySet().stream()
                .filter(candidate -> candidate.playerId().equals(player.getUUID()))
                .count();
        if (playerRequests >= MAX_ACTIVE_PER_PLAYER) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "too_many_requests");
            return;
        }

        AssetPreviewRevision revision = packet.request().revision();
        ActiveRequest request = new ActiveRequest(revision);
        state.active.put(key, request);
        long generation = state.generation;
        CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> acquisition;
        try {
            acquisition = acquirePreview(state, revision);
        } catch (RuntimeException exception) {
            state.active.remove(key, request);
            result(player, requestId, AssetPreviewResultCode.IO_FAILURE, "preview_generation_failed");
            return;
        }
        acquisition.whenComplete((stored, error) -> {
            if (currentRequest(state, generation, key) == null) return;
            server.execute(() -> completeResolution(state, generation, key, stored, error));
        });
    }

    public void cancel(ServerPlayer player, PacketAssetPreviewCancel packet) {
        ServerState state = states.get(player.level().getServer());
        if (state != null) {
            removeRequest(state, new RequestKey(player.getUUID(), packet.requestId()));
        }
    }

    private CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> acquirePreview(
            ServerState state, AssetPreviewRevision revision) {
        synchronized (state) {
            InFlightPreview existing = state.inFlight.get(revision);
            if (existing != null) {
                existing.waiters++;
                return existing.promise;
            }
            CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> promise = new CompletableFuture<>();
            CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> worker = io.submit(() -> {
                Path source = validateSource(state.server, revision);
                Optional<ServerAssetPreviewStore.StoredPreview> cached = store.find(state.server, revision);
                if (cached.isPresent()) return cached;
                ServerAssetPreviewGenerator generator = generators.get(revision.identity().kind());
                return generator == null ? Optional.empty()
                        : Optional.of(generator.generate(state.server, source, revision));
            });
            InFlightPreview created = new InFlightPreview(promise, worker);
            created.waiters = 1;
            state.inFlight.put(revision, created);
            worker.whenComplete((value, error) -> {
                if (error != null) promise.completeExceptionally(error);
                else promise.complete(value);
            });
            return promise;
        }
    }

    private void completeResolution(ServerState state, long generation, RequestKey key,
                                    Optional<ServerAssetPreviewStore.StoredPreview> stored, Throwable error) {
        ActiveRequest request = currentRequest(state, generation, key);
        if (request == null) return;
        if (error != null) {
            removeRequest(state, key);
            if (hasCause(error, PreviewSourceChangedException.class)
                    || hasCause(error, StaleRevisionException.class)) {
                result(state, key, AssetPreviewResultCode.STALE_REVISION, "stale_revision");
            } else if (hasCause(error, InvalidPreviewRequestException.class)) {
                result(state, key, AssetPreviewResultCode.INVALID_REQUEST, "invalid_source");
            } else if (hasCause(error, PreviewUnavailableException.class)) {
                result(state, key, AssetPreviewResultCode.NOT_AVAILABLE, "not_available");
            } else {
                result(state, key, AssetPreviewResultCode.IO_FAILURE, "preview_generation_failed");
            }
            return;
        }
        if (stored == null || stored.isEmpty()) {
            removeRequest(state, key);
            result(state, key, AssetPreviewResultCode.NOT_AVAILABLE, "not_available");
            return;
        }

        ServerAssetPreviewStore.StoredPreview preview = stored.get();
        CompletableFuture<byte[]> artifactRead;
        try {
            artifactRead = io.submit(() -> Files.readAllBytes(preview.path()));
        } catch (RuntimeException exception) {
            removeRequest(state, key);
            result(state, key, AssetPreviewResultCode.IO_FAILURE, "artifact_read_failed");
            return;
        }
        request.artifactRead = artifactRead;
        artifactRead.whenComplete((content, readError) -> {
            if (currentRequest(state, generation, key) == null) return;
            state.server.execute(() -> completeRead(state, generation, key, preview, content, readError));
        });
    }

    private void completeRead(ServerState state, long generation, RequestKey key,
                              ServerAssetPreviewStore.StoredPreview stored, byte[] content, Throwable error) {
        ActiveRequest request = currentRequest(state, generation, key);
        if (request == null) return;
        request.artifactRead = null;
        if (error != null || content == null || content.length != stored.descriptor().encodedBytes()) {
            removeRequest(state, key);
            result(state, key, AssetPreviewResultCode.IO_FAILURE, "artifact_read_failed");
            return;
        }
        if (!removeRequest(state, key, request)) return;
        ServerPlayer player = onlinePlayer(state, key);
        if (player != null) {
            NetworkHandler.sendToPlayer(player, PacketAssetPreviewResponse.available(
                    key.requestId(), stored.descriptor(), content));
        }
    }

    private void cancelPlayer(MinecraftServer server, UUID playerId) {
        ServerState state = states.get(server);
        if (state == null) return;
        for (Map.Entry<RequestKey, ActiveRequest> entry : Map.copyOf(state.active).entrySet()) {
            if (entry.getKey().playerId().equals(playerId)) {
                removeRequest(state, entry.getKey(), entry.getValue());
            }
        }
    }

    private void closeServer(MinecraftServer server) {
        ServerState state = states.remove(server);
        if (state == null) return;
        synchronized (state) {
            state.generation++;
            state.active.values().forEach(request -> {
                if (request.artifactRead != null) request.artifactRead.cancel(false);
            });
            state.active.clear();
            state.inFlight.values().forEach(InFlightPreview::cancel);
            state.inFlight.clear();
        }
    }

    private ActiveRequest currentRequest(ServerState state, long generation, RequestKey key) {
        return states.get(state.server) == state && state.generation == generation
                ? state.active.get(key) : null;
    }

    private static boolean removeRequest(ServerState state, RequestKey key, ActiveRequest expected) {
        if (!state.active.remove(key, expected)) return false;
        if (expected.artifactRead != null) expected.artifactRead.cancel(false);
        releasePreview(state, expected.revision);
        return true;
    }

    private static void removeRequest(ServerState state, RequestKey key) {
        ActiveRequest request = state.active.get(key);
        if (request != null) removeRequest(state, key, request);
    }

    private static void releasePreview(ServerState state, AssetPreviewRevision revision) {
        synchronized (state) {
            InFlightPreview inFlight = state.inFlight.get(revision);
            if (inFlight == null) return;
            if (--inFlight.waiters > 0) return;
            state.inFlight.remove(revision, inFlight);
            inFlight.cancel();
        }
    }

    private static ServerPlayer onlinePlayer(ServerState state, RequestKey key) {
        return state.server.getPlayerList().getPlayer(key.playerId());
    }

    private static void result(ServerState state, RequestKey key, AssetPreviewResultCode code, String detail) {
        result(onlinePlayer(state, key), key.requestId(), code, detail);
    }

    private static Path validateSource(MinecraftServer server, AssetPreviewRevision revision) throws Exception {
        try {
            if (revision.formatVersion() != AssetPreviewLimits.FORMAT_VERSION) {
                throw new InvalidPreviewRequestException();
            }
            Path source = RemoteAssetFileService.resolveTransferSource(server, revision.identity().remotePath());
            AssetPreviewKind actualKind = AssetTypeCatalog.previewKind(AssetTypeCatalog.inspect(source).typeId());
            if (!actualKind.equals(revision.identity().kind())) {
                throw new InvalidPreviewRequestException();
            }
            if (Files.size(source) != revision.sourceSize()
                    || Files.getLastModifiedTime(source).toMillis() != revision.sourceLastModified()) {
                throw new StaleRevisionException();
            }
            return source;
        } catch (StaleRevisionException | InvalidPreviewRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPreviewRequestException(exception);
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static void result(ServerPlayer player, UUID id, AssetPreviewResultCode code, String detail) {
        if (player != null) {
            NetworkHandler.sendToPlayer(player, PacketAssetPreviewResponse.failure(id, code, detail));
        }
    }

    @Override
    public void close() {
        for (MinecraftServer server : states.keySet()) closeServer(server);
        io.close();
    }

    private record RequestKey(UUID playerId, UUID requestId) {
    }

    private static final class ActiveRequest {
        private final AssetPreviewRevision revision;
        private CompletableFuture<byte[]> artifactRead;

        private ActiveRequest(AssetPreviewRevision revision) {
            this.revision = revision;
        }
    }

    private static final class InFlightPreview {
        private final CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> promise;
        private final CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> worker;
        private int waiters;

        private InFlightPreview(
                CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> promise,
                CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> worker) {
            this.promise = promise;
            this.worker = worker;
        }

        private void cancel() {
            promise.cancel(false);
            worker.cancel(false);
        }
    }

    private static final class ServerState {
        private final MinecraftServer server;
        private final Map<RequestKey, ActiveRequest> active = new ConcurrentHashMap<>();
        private final Map<AssetPreviewRevision, InFlightPreview> inFlight = new ConcurrentHashMap<>();
        private long generation;

        private ServerState(MinecraftServer server) {
            this.server = server;
        }
    }

    private static final class StaleRevisionException extends Exception {
    }

    private static final class InvalidPreviewRequestException extends Exception {
        private InvalidPreviewRequestException() {
        }

        private InvalidPreviewRequestException(Throwable cause) {
            super(cause);
        }
    }
}
