package com.mine.geometry_node.core.engine.system.asset.transfer.service;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetRepositoryService;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AtomicAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.engine.system.data.library.RemoteDataLibraryRepositoryService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.AssetTransferPlanKind;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileDownloadRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileResponse;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileUpload;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanResponse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Handles one complete file per request; NeoForge performs transport-level packet splitting. */
public final class ServerAssetTransferService {
    public static final ServerAssetTransferService INSTANCE = new ServerAssetTransferService();
    private static final int MAX_ACTIVE_REQUESTS = 8;
    private static final int MAX_ACTIVE_REQUESTS_PER_PLAYER = 1;
    private static final int COMPLETED_REQUEST_HISTORY = 4_096;

    private final Map<MinecraftServer, RequestRegistry> requestRegistries = new WeakHashMap<>();

    private ServerAssetTransferService() {
    }

    public void handleUpload(ServerPlayer player, PacketAssetFileUpload packet) {
        if (!hasUploadPermission(player, packet.purpose())) {
            sendFailure(player, packet.transferId(), AssetTransferErrorCode.PERMISSION_DENIED,
                    "permission_denied", "");
            return;
        }

        MinecraftServer server = player.level().getServer();
        RequestKey requestKey = new RequestKey(player.getUUID(), packet.transferId());
        if (!admit(player, server, requestKey)) return;

        CompletableFuture<UploadOutcome> operation;
        try {
            operation = switch (packet.purpose()) {
                case ASSET_REPOSITORY -> RemoteAssetRepositoryService.INSTANCE.commitUpload(
                                server, packet.remotePath(), packet.content(), packet.conflictPolicy())
                        .thenApply(ServerAssetTransferService::assetUploadOutcome);
                case DATA_LIBRARY_CREATE -> RemoteDataLibraryRepositoryService.INSTANCE
                        .createAsync(player, packet.content())
                        .thenApply(ignored -> UploadOutcome.committed(packet.content().length));
                case DATA_LIBRARY_UPDATE -> RemoteDataLibraryRepositoryService.INSTANCE
                        .updateAsync(player, packet.content())
                        .thenApply(ignored -> UploadOutcome.committed(packet.content().length));
                case DATA_LIBRARY_DOWNLOAD -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("Invalid upload purpose"));
            };
        } catch (Throwable throwable) {
            completeRequest(server, requestKey);
            sendFailure(player, packet.transferId(), errorCode(throwable),
                    "commit_failed", rootMessage(throwable));
            return;
        }

        operation.whenComplete((outcome, throwable) -> server.execute(() -> {
            completeRequest(server, requestKey);
            if (throwable != null) {
                Throwable cause = rootCause(throwable);
                if (packet.purpose() != AssetTransferPurpose.ASSET_REPOSITORY) {
                    GeometryNode.LOGGER.warn("Data Library upload {} failed for player {}: {}",
                            packet.purpose(), player.getGameProfile().name(), rootMessage(cause), cause);
                }
                boolean staleObject = rootMessage(cause).startsWith("STALE_OBJECT:");
                sendFailure(player, packet.transferId(),
                        staleObject ? AssetTransferErrorCode.STALE_OBJECT : errorCode(cause),
                        staleObject ? "stale_object" : "commit_failed", rootMessage(cause));
                return;
            }
            boolean refreshWarning = !outcome.refreshWarning().isEmpty();
            NetworkHandler.sendToPlayer(player, new PacketAssetFileResponse(
                    packet.transferId(), AssetTransferState.COMPLETED,
                    refreshWarning ? AssetTransferErrorCode.GRAPH_RELOAD_FAILED : AssetTransferErrorCode.NONE,
                    refreshWarning ? "geometry_node.asset_transfer.error.asset_refresh_failed" : "",
                    outcome.refreshWarning(), outcome.committed(), outcome.sourceSize(),
                    outcome.sourceLastModified(), new byte[0]));
        }));
    }

    public void handleDownloadRequest(ServerPlayer player, PacketAssetFileDownloadRequest packet) {
        if (!hasDownloadPermission(player, packet.purpose())) {
            sendFailure(player, packet.transferId(), AssetTransferErrorCode.PERMISSION_DENIED,
                    "permission_denied", "");
            return;
        }

        MinecraftServer server = player.level().getServer();
        RequestKey requestKey = new RequestKey(player.getUUID(), packet.transferId());
        if (!admit(player, server, requestKey)) return;

        CompletableFuture<DownloadOutcome> operation;
        try {
            operation = switch (packet.purpose()) {
                case ASSET_REPOSITORY -> RemoteAssetRepositoryService.INSTANCE
                        .readTransferFile(server, packet.remotePath())
                        .thenApply(file -> new DownloadOutcome(
                                file.content(), file.size(), file.lastModified()));
                case DATA_LIBRARY_DOWNLOAD -> RemoteDataLibraryRepositoryService.INSTANCE.readSnapshot(player)
                        .thenApply(content -> new DownloadOutcome(content, content.length, 0L));
                case DATA_LIBRARY_CREATE, DATA_LIBRARY_UPDATE -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("Invalid download purpose"));
            };
        } catch (Throwable throwable) {
            completeRequest(server, requestKey);
            sendFailure(player, packet.transferId(), errorCode(throwable),
                    "read_failed", rootMessage(throwable));
            return;
        }

        operation.whenComplete((outcome, throwable) -> server.execute(() -> {
            completeRequest(server, requestKey);
            if (throwable != null) {
                Throwable cause = rootCause(throwable);
                sendFailure(player, packet.transferId(), errorCode(cause),
                        "read_failed", rootMessage(cause));
                return;
            }
            NetworkHandler.sendToPlayer(player, new PacketAssetFileResponse(
                    packet.transferId(), AssetTransferState.COMPLETED, AssetTransferErrorCode.NONE,
                    "", "", false, outcome.sourceSize(), outcome.sourceLastModified(), outcome.content()));
        }));
    }

    public void handlePlan(ServerPlayer player, PacketAssetTransferPlanRequest packet) {
        AssetTransferDirection direction = packet.kind() == AssetTransferPlanKind.UPLOAD_CONFLICTS
                ? AssetTransferDirection.UPLOAD : AssetTransferDirection.DOWNLOAD;
        if (!hasAssetPermission(player, direction)) {
            NetworkHandler.sendToPlayer(player, new PacketAssetTransferPlanResponse(
                    packet.requestId(), packet.kind(), false, "permission_denied", List.of(), List.of()));
            return;
        }
        MinecraftServer server = player.level().getServer();
        CompletableFuture<PacketAssetTransferPlanResponse> plan = switch (packet.kind()) {
            case UPLOAD_CONFLICTS -> RemoteAssetRepositoryService.INSTANCE
                    .findUploadConflicts(server, packet.paths())
                    .thenApply(conflicts -> new PacketAssetTransferPlanResponse(
                            packet.requestId(), packet.kind(), true, "", List.of(), conflicts));
            case DOWNLOAD_MANIFEST -> RemoteAssetRepositoryService.INSTANCE
                    .flattenSelection(server, packet.paths())
                    .thenApply(files -> new PacketAssetTransferPlanResponse(
                            packet.requestId(), packet.kind(), true, "", files, List.of()));
        };
        plan.whenComplete((response, throwable) -> server.execute(() ->
                NetworkHandler.sendToPlayer(player, throwable == null ? response : new PacketAssetTransferPlanResponse(
                        packet.requestId(), packet.kind(), false, rootMessage(throwable), List.of(), List.of()))));
    }

    private static UploadOutcome assetUploadOutcome(RemoteAssetFileService.UploadCommitResult result) {
        if (result.commit() != AtomicAssetCommitter.CommitResult.COMMITTED) return UploadOutcome.SKIPPED;
        String refreshWarning = result.refreshFailure() == null ? "" : rootMessage(result.refreshFailure());
        return new UploadOutcome(true, result.sourceSize(), result.sourceLastModified(), refreshWarning);
    }

    private static boolean hasUploadPermission(ServerPlayer player, AssetTransferPurpose purpose) {
        return switch (purpose) {
            case ASSET_REPOSITORY, DATA_LIBRARY_CREATE, DATA_LIBRARY_UPDATE ->
                    RemoteAssetPermissions.canUploadAssets(player);
            case DATA_LIBRARY_DOWNLOAD -> false;
        };
    }

    private static boolean hasDownloadPermission(ServerPlayer player, AssetTransferPurpose purpose) {
        return switch (purpose) {
            case ASSET_REPOSITORY -> RemoteAssetPermissions.canDownloadAssets(player);
            case DATA_LIBRARY_DOWNLOAD -> RemoteAssetPermissions.canBrowseRemoteAssets(player)
                    && RemoteAssetPermissions.canDownloadAssets(player);
            case DATA_LIBRARY_CREATE, DATA_LIBRARY_UPDATE -> false;
        };
    }

    private static boolean hasAssetPermission(ServerPlayer player, AssetTransferDirection direction) {
        return direction == AssetTransferDirection.UPLOAD
                ? RemoteAssetPermissions.canUploadAssets(player)
                : RemoteAssetPermissions.canDownloadAssets(player);
    }

    private boolean admit(ServerPlayer player, MinecraftServer server, RequestKey key) {
        Admission admission = beginRequest(server, key);
        if (admission == Admission.ADMITTED) return true;
        if (admission == Admission.BUSY) {
            sendFailure(player, key.transferId(), AssetTransferErrorCode.SERVER_BUSY,
                    "server_busy", "");
        }
        return false;
    }

    private synchronized Admission beginRequest(MinecraftServer server, RequestKey key) {
        RequestRegistry registry = requestRegistries.computeIfAbsent(server, ignored -> new RequestRegistry());
        if (registry.active.contains(key) || registry.completed.containsKey(key)) return Admission.DUPLICATE;
        if (registry.active.size() >= MAX_ACTIVE_REQUESTS) return Admission.BUSY;
        long playerRequests = registry.active.stream()
                .filter(active -> active.playerId().equals(key.playerId()))
                .count();
        if (playerRequests >= MAX_ACTIVE_REQUESTS_PER_PLAYER) return Admission.BUSY;
        registry.active.add(key);
        return Admission.ADMITTED;
    }

    private synchronized void completeRequest(MinecraftServer server, RequestKey key) {
        RequestRegistry registry = requestRegistries.get(server);
        if (registry == null || !registry.active.remove(key)) return;
        registry.completed.put(key, Boolean.TRUE);
        while (registry.completed.size() > COMPLETED_REQUEST_HISTORY) {
            Iterator<RequestKey> oldest = registry.completed.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private static void sendFailure(ServerPlayer player, UUID transferId, AssetTransferErrorCode code,
                                    String message, String detail) {
        NetworkHandler.sendToPlayer(player, new PacketAssetFileResponse(
                transferId, AssetTransferState.FAILED, code,
                "geometry_node.asset_transfer.error." + message, detail,
                false, 0L, 0L, new byte[0]));
    }

    private static AssetTransferErrorCode errorCode(Throwable throwable) {
        String message = rootMessage(throwable).toLowerCase(java.util.Locale.ROOT);
        if (message.contains("file limit") || message.contains("too large") || message.contains("exceeds")) {
            return AssetTransferErrorCode.FILE_TOO_LARGE;
        }
        if (throwable instanceof IllegalArgumentException) return AssetTransferErrorCode.INVALID_PATH;
        return AssetTransferErrorCode.IO_FAILURE;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) return "";
        Throwable cause = rootCause(throwable);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private record DownloadOutcome(byte[] content, long sourceSize, long sourceLastModified) {
    }

    private enum Admission {
        ADMITTED,
        DUPLICATE,
        BUSY
    }

    private record RequestKey(UUID playerId, UUID transferId) {
    }

    private static final class RequestRegistry {
        private final Set<RequestKey> active = new HashSet<>();
        private final LinkedHashMap<RequestKey, Boolean> completed = new LinkedHashMap<>();
    }

    private record UploadOutcome(boolean committed, long sourceSize, long sourceLastModified,
                                 String refreshWarning) {
        private static final UploadOutcome SKIPPED = new UploadOutcome(false, 0L, 0L, "");

        private static UploadOutcome committed(long sourceSize) {
            return new UploadOutcome(true, sourceSize, 0L, "");
        }
    }
}
