package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.PreviewSourceChangedException;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.PreviewUnavailableException;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerAssetPreviewGenerator;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerAssetPreviewGeneratorRegistry;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewAccepted;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewChunk;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewComplete;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewResult;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAssetPreviewService implements AutoCloseable {
    public static final ServerAssetPreviewService INSTANCE = new ServerAssetPreviewService();

    private final ServerAssetPreviewStore store = new ServerAssetPreviewStore();
    private final ServerAssetPreviewGeneratorRegistry generators = new ServerAssetPreviewGeneratorRegistry();
    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-Preview-ServerIO", 2, 64);
    private final Map<RequestKey, Boolean> active = new ConcurrentHashMap<>();
    private final Map<AssetPreviewRevision, CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>>>
            inFlight = new ConcurrentHashMap<>();
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
        PlayerEvent.PLAYER_QUIT.register(player ->
                active.keySet().removeIf(key -> key.playerId.equals(player.getUUID())));
    }

    public void handleRequest(ServerPlayer player, PacketAssetPreviewRequest packet) {
        UUID requestId = packet.request().requestId();
        if (!RemoteAssetPermissions.canDownloadAssets(player)) {
            result(player, requestId, AssetPreviewResultCode.PERMISSION_DENIED, "permission_denied");
            return;
        }

        AssetPreviewRevision revision = packet.request().revision();
        RequestKey key = new RequestKey(player.getUUID(), requestId);
        if (active.putIfAbsent(key, Boolean.TRUE) != null) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "duplicate_request");
            return;
        }

        resolvePreview(player.level().getServer(), revision).whenComplete((stored, error) ->
                player.level().getServer().execute(() -> completeResolution(player, key, stored, error)));
    }

    public void cancel(ServerPlayer player, UUID requestId) {
        active.remove(new RequestKey(player.getUUID(), requestId));
    }

    private CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> resolvePreview(
            MinecraftServer server, AssetPreviewRevision revision) {
        return inFlight.computeIfAbsent(revision, ignored -> {
            CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> future = io.submit(() -> {
                Path source = validateSource(server, revision);
                Optional<ServerAssetPreviewStore.StoredPreview> cached = store.find(server, revision);
                if (cached.isPresent()) return cached;
                ServerAssetPreviewGenerator generator = generators.get(revision.identity().kind());
                return generator == null ? Optional.empty()
                        : Optional.of(generator.generate(server, source, revision));
            });
            future.whenComplete((value, error) -> inFlight.remove(revision, future));
            return future;
        });
    }

    private void completeResolution(ServerPlayer player, RequestKey key,
                                    Optional<ServerAssetPreviewStore.StoredPreview> stored, Throwable error) {
        if (!active.containsKey(key)) return;
        if (error != null) {
            active.remove(key);
            if (hasCause(error, PreviewSourceChangedException.class)) {
                result(player, key.requestId, AssetPreviewResultCode.STALE_REVISION, "stale_revision");
            } else if (hasCause(error, StaleRevisionException.class)) {
                result(player, key.requestId, AssetPreviewResultCode.STALE_REVISION, "stale_revision");
            } else if (hasCause(error, InvalidPreviewRequestException.class)) {
                result(player, key.requestId, AssetPreviewResultCode.INVALID_REQUEST, "invalid_source");
            } else if (hasCause(error, PreviewUnavailableException.class)) {
                result(player, key.requestId, AssetPreviewResultCode.NOT_AVAILABLE, "not_available");
            } else {
                result(player, key.requestId, AssetPreviewResultCode.IO_FAILURE, "preview_generation_failed");
            }
            return;
        }
        if (stored == null || stored.isEmpty()) {
            active.remove(key);
            result(player, key.requestId, AssetPreviewResultCode.NOT_AVAILABLE, "not_available");
            return;
        }
        sendStored(player, key, stored.get());
    }

    private void sendStored(ServerPlayer player, RequestKey key, ServerAssetPreviewStore.StoredPreview stored) {
        NetworkHandler.sendToPlayer(player, new PacketAssetPreviewAccepted(key.requestId, stored.descriptor()));
        io.submit(() -> Files.readAllBytes(stored.path())).whenComplete((content, error) ->
                player.level().getServer().execute(() -> {
                    if (!active.remove(key)) return;
                    if (error != null || content.length != stored.descriptor().encodedBytes()) {
                        result(player, key.requestId, AssetPreviewResultCode.IO_FAILURE, "artifact_read_failed");
                        return;
                    }
                    int sequence = 0;
                    for (int offset = 0; offset < content.length; offset += AssetPreviewLimits.MAX_CHUNK_BYTES) {
                        int end = Math.min(content.length, offset + AssetPreviewLimits.MAX_CHUNK_BYTES);
                        NetworkHandler.sendToPlayer(player, new PacketAssetPreviewChunk(key.requestId, sequence++, offset,
                                Arrays.copyOfRange(content, offset, end)));
                    }
                    NetworkHandler.sendToPlayer(player, new PacketAssetPreviewComplete(key.requestId));
                }));
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
        NetworkHandler.sendToPlayer(player, new PacketAssetPreviewResult(id, code, detail));
    }

    @Override
    public void close() {
        active.clear();
        inFlight.clear();
        io.close();
    }

    private record RequestKey(UUID playerId, UUID requestId) {
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
