package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerImagePreviewGenerator;
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
    private final ServerImagePreviewGenerator imageGenerator = new ServerImagePreviewGenerator(store);
    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-Preview-ServerIO", 2, 64);
    private final Map<RequestKey, Boolean> active = new ConcurrentHashMap<>();
    private final Map<AssetPreviewRevision, CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>>>
            inFlight = new ConcurrentHashMap<>();
    private boolean initialized;

    private ServerAssetPreviewService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
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
        Path source;
        try {
            source = validateSource(player.level().getServer(), revision);
        } catch (StaleRevisionException exception) {
            result(player, requestId, AssetPreviewResultCode.STALE_REVISION, "stale_revision");
            return;
        } catch (Exception exception) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "invalid_source");
            return;
        }

        RequestKey key = new RequestKey(player.getUUID(), requestId);
        if (active.putIfAbsent(key, Boolean.TRUE) != null) {
            result(player, requestId, AssetPreviewResultCode.INVALID_REQUEST, "duplicate_request");
            return;
        }

        resolvePreview(player.level().getServer(), source, revision).whenComplete((stored, error) ->
                player.level().getServer().execute(() -> completeResolution(player, key, stored, error)));
    }

    public void cancel(ServerPlayer player, UUID requestId) {
        active.remove(new RequestKey(player.getUUID(), requestId));
    }

    private CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> resolvePreview(
            MinecraftServer server, Path source, AssetPreviewRevision revision) {
        return inFlight.computeIfAbsent(revision, ignored -> {
            CompletableFuture<Optional<ServerAssetPreviewStore.StoredPreview>> future = io.submit(() -> {
                Optional<ServerAssetPreviewStore.StoredPreview> cached = store.find(server, revision);
                if (cached.isPresent() || revision.identity().kind() != AssetPreviewKind.IMAGE) return cached;
                return Optional.of(imageGenerator.generate(server, source, revision));
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
            if (hasCause(error, ServerImagePreviewGenerator.SourceChangedException.class)) {
                result(player, key.requestId, AssetPreviewResultCode.STALE_REVISION, "stale_revision");
            } else if (hasCause(error, ServerImagePreviewGenerator.PreviewUnavailableException.class)) {
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
        Path source = RemoteAssetFileService.resolveTransferSource(server, revision.identity().remotePath());
        AssetPreviewKind actualKind = AssetPreviewKind.fromAssetType(AssetTypeCatalog.inspect(source).typeId());
        if (actualKind != revision.identity().kind()) {
            throw new IllegalArgumentException("Preview source type does not match request");
        }
        if (Files.size(source) != revision.sourceSize()
                || Files.getLastModifiedTime(source).toMillis() != revision.sourceLastModified()) {
            throw new StaleRevisionException();
        }
        return source;
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
}
