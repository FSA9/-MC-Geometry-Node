package com.mine.geometry_node.core.engine.system.asset.transfer.service;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewIdentity;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferServerConfig;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferServerPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.IncomingAssetTransferFile;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.OutgoingAssetTransferFile;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAccepted;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAck;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferCancel;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferOpen;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferResult;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferServerResult;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferUploadAck;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanResponse;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerAssetTransferService implements AutoCloseable {
    public static final ServerAssetTransferService INSTANCE = new ServerAssetTransferService();

    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-AssetTransfer-ServerIO", 2, 128);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetTransfer-ServerRate");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, PlayerContext> players = new ConcurrentHashMap<>();
    private final ServerAssetPreviewStore previewStore = new ServerAssetPreviewStore();
    private volatile boolean initialized;

    private ServerAssetTransferService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        PlayerEvent.PLAYER_QUIT.register(this::closePlayer);
        LifecycleEvent.SERVER_STOPPING.register(server -> closeAllPlayers());
        scheduler.scheduleAtFixedRate(this::expireIdleSessions, 1L, 1L, TimeUnit.SECONDS);
    }

    public void handleOpen(ServerPlayer player, PacketAssetTransferOpen packet) {
        if (!hasPermission(player, packet.direction())) {
            sendFailure(player, packet.transferId(), AssetTransferErrorCode.PERMISSION_DENIED, "permission_denied", "");
            return;
        }
        AssetTransferServerPolicy policy = AssetTransferServerConfig.policy();
        PlayerContext owner = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerContext(policy));
        owner.applyPolicy(policy);
        int chunkBytes = Math.min(packet.requestedChunkBytes(), policy.maxChunkBytes());
        synchronized (owner) {
            if (owner.sessions.containsKey(packet.transferId())) {
                sendFailure(player, packet.transferId(), AssetTransferErrorCode.INVALID_SEQUENCE, "duplicate_transfer_id", "");
                return;
            }
            int activeDirection = (int) owner.sessions.values().stream()
                    .filter(session -> session.direction == packet.direction()).count();
            int maximum = packet.direction() == AssetTransferDirection.UPLOAD
                    ? policy.maxConcurrentUploadsPerPlayer() : policy.maxConcurrentDownloadsPerPlayer();
            if (activeDirection >= maximum) {
                sendFailure(player, packet.transferId(), AssetTransferErrorCode.TEMPORARY_STORAGE_LIMIT,
                        "concurrency_limit", "");
                return;
            }
            ServerSession session = new ServerSession(player, packet, chunkBytes, policy);
            owner.sessions.put(packet.transferId(), session);
        }

        if (packet.direction() == AssetTransferDirection.UPLOAD) openUpload(owner, packet.transferId());
        else openDownload(owner, packet.transferId());
    }

    public void handlePlan(ServerPlayer player, PacketAssetTransferPlanRequest packet) {
        if (!hasPermission(player, packet.kind() == com.mine.geometry_node.core.network.packet.asset.transfer.AssetTransferPlanKind.UPLOAD_CONFLICTS
                ? AssetTransferDirection.UPLOAD : AssetTransferDirection.DOWNLOAD)) {
            NetworkHandler.sendToPlayer(player, new PacketAssetTransferPlanResponse(
                    packet.requestId(), packet.kind(), false, "permission_denied", List.of(), List.of()));
            return;
        }
        io.submit(() -> switch (packet.kind()) {
            case UPLOAD_CONFLICTS -> new PacketAssetTransferPlanResponse(
                    packet.requestId(), packet.kind(), true, "", List.of(),
                    RemoteAssetFileService.findUploadConflicts(player.level().getServer(), packet.paths()));
            case DOWNLOAD_MANIFEST -> new PacketAssetTransferPlanResponse(
                    packet.requestId(), packet.kind(), true, "",
                    RemoteAssetFileService.flattenSelection(player.level().getServer(), packet.paths()), List.of());
        }).whenComplete((response, throwable) -> player.level().getServer().execute(() ->
                NetworkHandler.sendToPlayer(player, throwable == null ? response : new PacketAssetTransferPlanResponse(
                        packet.requestId(), packet.kind(), false, rootMessage(throwable), List.of(), List.of()))));
    }

    public void handleChunk(ServerPlayer player, PacketAssetTransferChunk packet) {
        SessionLookup lookup = find(player, packet.transferId(), AssetTransferDirection.UPLOAD);
        if (lookup == null) return;
        ServerSession session = lookup.session;
        session.touch();
        io.submit(() -> session.incoming.writeChunk(packet.sequence(), packet.offset(), packet.content()))
                .whenComplete((ignored, throwable) -> player.level().getServer().execute(() -> {
                    if (throwable != null) {
                        failAndClose(lookup.owner, session, AssetTransferErrorCode.INVALID_SEQUENCE,
                                "invalid_sequence", rootMessage(throwable));
                    } else {
                        long delay = lookup.owner.uploadLimiter.reserveDelayNanos(packet.content().length);
                        scheduler.schedule(() -> player.level().getServer().execute(() -> {
                            if (lookup.owner.sessions.get(session.transferId) == session) {
                                NetworkHandler.sendToPlayer(player, new PacketAssetTransferUploadAck(
                                        session.transferId, session.incoming.nextSequence(), session.incoming.nextOffset()));
                            }
                        }), delay, TimeUnit.NANOSECONDS);
                    }
                }));
    }

    public void handleAck(ServerPlayer player, PacketAssetTransferAck packet) {
        SessionLookup lookup = find(player, packet.transferId(), AssetTransferDirection.DOWNLOAD);
        if (lookup == null) return;
        ServerSession session = lookup.session;
        synchronized (session) {
            if (session.sendInProgress) return;
            if (packet.nextSequence() != session.nextSequence || packet.nextOffset() != session.nextOffset) {
                failAndClose(lookup.owner, session, AssetTransferErrorCode.INVALID_SEQUENCE,
                        "invalid_acknowledgement", "");
                return;
            }
            session.sendInProgress = true;
        }
        session.touch();
        sendNextDownloadChunk(lookup.owner, session);
    }

    public void handleComplete(ServerPlayer player, PacketAssetTransferComplete packet) {
        SessionLookup lookup = find(player, packet.transferId(), AssetTransferDirection.UPLOAD);
        if (lookup == null) return;
        ServerSession session = lookup.session;
        session.touch();
        io.run(() -> {
            session.incoming.verifyAndClose();
            var temporary = session.incoming.retainVerifiedFile();
            Path previewTemporary = null;
            try {
                previewTemporary = stageSchematicPreview(player, session, packet, temporary);
                VerifiedAssetCommitter.CommitResult commit = RemoteAssetFileService.commitVerifiedUpload(
                        player.level().getServer(), session.remotePath, temporary,
                        session.open.conflictPolicy()).join();
                if (commit == VerifiedAssetCommitter.CommitResult.COMMITTED && previewTemporary != null) {
                    publishSchematicPreview(player, session, packet, previewTemporary);
                }
            } catch (Throwable throwable) {
                Files.deleteIfExists(temporary);
                throw throwable;
            } finally {
                if (previewTemporary != null) Files.deleteIfExists(previewTemporary);
            }
        }).whenComplete((ignored, throwable) -> player.level().getServer().execute(() -> {
            if (throwable != null) {
                failAndClose(lookup.owner, session, AssetTransferErrorCode.HASH_MISMATCH,
                        "verification_or_commit_failed", rootMessage(throwable));
            } else {
                completeAndClose(lookup.owner, session);
            }
        }));
    }

    private Path stageSchematicPreview(ServerPlayer player, ServerSession session,
                                       PacketAssetTransferComplete packet, Path verifiedFile) throws Exception {
        boolean schematic = AssetTypeCatalog.SCHEMATIC_TYPE_ID.equals(
                AssetTypeCatalog.inspect(verifiedFile, session.remotePath).typeId());
        if (!schematic) {
            if (packet.hasPreview()) throw new java.io.IOException("Preview attachment is not valid for this asset type");
            return null;
        }
        if (!packet.hasPreview() || packet.previewFormat()
                != com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat.PNG) {
            throw new java.io.IOException("Schematic upload requires a PNG nativepreview");
        }
        byte[] content = packet.previewContent();
        Path directory = RemoteAssetFileService.transferTemporaryDirectory(player.level().getServer());
        Files.createDirectories(directory);
        Path temporary = directory.resolve(session.transferId + ".nativepreview.tmp");
        Files.write(temporary, content);
        try {
            validatePreviewImage(temporary, packet.previewFormat(),
                    packet.previewWidth(), packet.previewHeight());
            return temporary;
        } catch (Throwable failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static void validatePreviewImage(Path source,
                                             com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat format,
                                             int expectedWidth, int expectedHeight) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new java.io.IOException("Cannot inspect schematic nativepreview image");
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new java.io.IOException("Unsupported schematic nativepreview image");
            ImageReader reader = readers.next();
            try {
                if (!format.name().equalsIgnoreCase(reader.getFormatName())) {
                    throw new java.io.IOException("Schematic nativepreview encoding does not match its descriptor");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width != expectedWidth || height != expectedHeight
                        || !com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits
                        .validDimensions(width, height)) {
                    throw new java.io.IOException("Invalid schematic nativepreview dimensions");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private void publishSchematicPreview(ServerPlayer player, ServerSession session,
                                         PacketAssetTransferComplete packet, Path previewTemporary) throws Exception {
        Path source = RemoteAssetFileService.resolveTransferSource(player.level().getServer(), session.remotePath);
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        AssetPreviewRevision revision = AssetPreviewRevision.current(
                new AssetPreviewIdentity(session.remotePath, AssetPreviewKind.SCHEMATIC),
                attributes.size(), attributes.lastModifiedTime().toMillis());
        AssetPreviewDescriptor descriptor = new AssetPreviewDescriptor(revision, packet.previewFormat(),
                packet.previewWidth(), packet.previewHeight(), Math.toIntExact(Files.size(previewTemporary)),
                AssetTransferHashing.sha256(previewTemporary));
        previewStore.publish(player.level().getServer(), previewTemporary, descriptor);
    }

    public void handleResult(ServerPlayer player, PacketAssetTransferResult packet) {
        SessionLookup lookup = find(player, packet.transferId(), AssetTransferDirection.DOWNLOAD);
        if (lookup != null) closeSession(lookup.owner, lookup.session);
    }

    public void handleCancel(ServerPlayer player, PacketAssetTransferCancel packet) {
        SessionLookup lookup = find(player, packet.transferId(), null);
        if (lookup == null) return;
        closeSession(lookup.owner, lookup.session);
        NetworkHandler.sendToPlayer(player, new PacketAssetTransferServerResult(packet.transferId(),
                AssetTransferState.CANCELLED, AssetTransferErrorCode.CANCELLED,
                "geometry_node.asset_transfer.error.cancelled", packet.reason()));
    }

    private void openUpload(PlayerContext owner, UUID transferId) {
        ServerSession session = owner.sessions.get(transferId);
        if (session == null) return;
        if (session.open.totalBytes() > session.policy.maxUploadFileBytes()) {
            failAndClose(owner, session, AssetTransferErrorCode.FILE_TOO_LARGE, "file_too_large", "");
            return;
        }
        io.submit(() -> IncomingAssetTransferFile.create(
                RemoteAssetFileService.transferTemporaryDirectory(session.player.level().getServer()),
                session.open.totalBytes(), session.open.sha256())).whenComplete((incoming, throwable) ->
                session.player.level().getServer().execute(() -> {
                    if (throwable != null || owner.sessions.get(transferId) != session) {
                        closeQuietly(incoming);
                        if (throwable != null) failAndClose(owner, session, AssetTransferErrorCode.IO_FAILURE,
                                "open_failed", rootMessage(throwable));
                        return;
                    }
                    session.incoming = incoming;
                    NetworkHandler.sendToPlayer(session.player, new PacketAssetTransferAccepted(
                            transferId, session.open.totalBytes(), session.open.sha256(), session.chunkBytes));
                }));
    }

    private void openDownload(PlayerContext owner, UUID transferId) {
        ServerSession session = owner.sessions.get(transferId);
        if (session == null) return;
        io.submit(() -> OutgoingAssetTransferFile.open(
                RemoteAssetFileService.resolveTransferSource(session.player.level().getServer(), session.remotePath),
                session.policy.maxDownloadFileBytes())).whenComplete((outgoing, throwable) ->
                session.player.level().getServer().execute(() -> {
                    if (throwable != null || owner.sessions.get(transferId) != session) {
                        closeQuietly(outgoing);
                        if (throwable != null) failAndClose(owner, session, AssetTransferErrorCode.IO_FAILURE,
                                "open_failed", rootMessage(throwable));
                        return;
                    }
                    session.outgoing = outgoing;
                    NetworkHandler.sendToPlayer(session.player, new PacketAssetTransferAccepted(
                            transferId, outgoing.totalBytes(), outgoing.sha256(), session.chunkBytes));
                }));
    }

    private void sendNextDownloadChunk(PlayerContext owner, ServerSession session) {
        if (session.nextOffset >= session.outgoing.totalBytes()) {
            io.run(session.outgoing::verifyUnchanged).whenComplete((ignored, throwable) -> session.player.level().getServer().execute(() -> {
                session.sendInProgress = false;
                if (throwable != null) failAndClose(owner, session, AssetTransferErrorCode.SOURCE_CHANGED,
                        "source_changed", rootMessage(throwable));
                else NetworkHandler.sendToPlayer(session.player, new PacketAssetTransferDownloadComplete(session.transferId));
            }));
            return;
        }
        long offset = session.nextOffset;
        int sequence = session.nextSequence;
        io.submit(() -> session.outgoing.readChunk(offset, session.chunkBytes)).whenComplete((content, throwable) -> {
            if (throwable != null) {
                session.player.level().getServer().execute(() -> failAndClose(owner, session, AssetTransferErrorCode.IO_FAILURE,
                        "read_failed", rootMessage(throwable)));
                return;
            }
            long delay = owner.downloadLimiter.reserveDelayNanos(content.length);
            scheduler.schedule(() -> session.player.level().getServer().execute(() -> {
                synchronized (session) {
                    if (owner.sessions.get(session.transferId) != session) return;
                    session.nextSequence++;
                    session.nextOffset += content.length;
                    session.sendInProgress = false;
                }
                NetworkHandler.sendToPlayer(session.player,
                        new PacketAssetTransferDownloadChunk(session.transferId, sequence, offset, content));
            }), delay, TimeUnit.NANOSECONDS);
        });
    }

    private SessionLookup find(ServerPlayer player, UUID transferId, AssetTransferDirection direction) {
        PlayerContext owner = players.get(player.getUUID());
        if (owner == null) return null;
        ServerSession session = owner.sessions.get(transferId);
        if (session == null || session.player != player || (direction != null && session.direction != direction)) return null;
        return new SessionLookup(owner, session);
    }

    private void expireIdleSessions() {
        long now = System.nanoTime();
        for (PlayerContext owner : players.values()) {
            for (ServerSession session : owner.sessions.values()) {
                if (now - session.lastActivityNanos < session.policy.idleTimeout().toNanos()
                        || !owner.sessions.remove(session.transferId, session)) continue;
                closeQuietly(session);
                session.player.level().getServer().execute(() -> sendFailure(session.player, session.transferId,
                        AssetTransferErrorCode.TIMEOUT, "timeout", ""));
            }
        }
    }

    private void completeAndClose(PlayerContext owner, ServerSession session) {
        closeSession(owner, session);
        NetworkHandler.sendToPlayer(session.player, new PacketAssetTransferServerResult(session.transferId,
                AssetTransferState.COMPLETED, AssetTransferErrorCode.NONE, "", ""));
    }

    private void failAndClose(PlayerContext owner, ServerSession session, AssetTransferErrorCode code,
                              String message, String detail) {
        closeSession(owner, session);
        sendFailure(session.player, session.transferId, code, message, detail);
    }

    private void closeSession(PlayerContext owner, ServerSession session) {
        if (owner.sessions.remove(session.transferId, session)) closeQuietly(session);
    }

    private void closePlayer(ServerPlayer player) {
        PlayerContext owner = players.remove(player.getUUID());
        if (owner != null) owner.close();
    }

    private void closeAllPlayers() {
        for (PlayerContext owner : players.values()) owner.close();
        players.clear();
    }

    @Override public void close() {
        closeAllPlayers();
        scheduler.shutdownNow();
        io.close();
    }

    private static boolean hasPermission(ServerPlayer player, AssetTransferDirection direction) {
        return direction == AssetTransferDirection.UPLOAD
                ? RemoteAssetPermissions.canUploadAssets(player) : RemoteAssetPermissions.canDownloadAssets(player);
    }

    private static void sendFailure(ServerPlayer player, UUID transferId, AssetTransferErrorCode code,
                                    String message, String detail) {
        NetworkHandler.sendToPlayer(player, new PacketAssetTransferServerResult(transferId, AssetTransferState.FAILED, code,
                "geometry_node.asset_transfer.error." + message, detail));
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) return "";
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private static void closeQuietly(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

    private final class PlayerContext implements AutoCloseable {
        private final Map<UUID, ServerSession> sessions = new ConcurrentHashMap<>();
        private final ByteRateLimiter uploadLimiter;
        private final ByteRateLimiter downloadLimiter;

        private PlayerContext(AssetTransferServerPolicy policy) {
            uploadLimiter = new ByteRateLimiter(policy.uploadRateBytesPerSecond());
            downloadLimiter = new ByteRateLimiter(policy.downloadRateBytesPerSecond());
        }

        private void applyPolicy(AssetTransferServerPolicy policy) {
            uploadLimiter.setRate(policy.uploadRateBytesPerSecond());
            downloadLimiter.setRate(policy.downloadRateBytesPerSecond());
        }

        @Override public void close() {
            for (ServerSession session : sessions.values()) closeQuietly(session);
            sessions.clear();
        }
    }

    private static final class ServerSession implements AutoCloseable {
        private final ServerPlayer player;
        private final PacketAssetTransferOpen open;
        private final UUID transferId;
        private final AssetTransferDirection direction;
        private final String remotePath;
        private final int chunkBytes;
        private final AssetTransferServerPolicy policy;
        private volatile IncomingAssetTransferFile incoming;
        private volatile OutgoingAssetTransferFile outgoing;
        private volatile long lastActivityNanos = System.nanoTime();
        private int nextSequence;
        private long nextOffset;
        private boolean sendInProgress;

        private ServerSession(ServerPlayer player, PacketAssetTransferOpen open, int chunkBytes,
                              AssetTransferServerPolicy policy) {
            this.player = player;
            this.open = open;
            transferId = open.transferId();
            direction = open.direction();
            remotePath = open.relativePath();
            this.chunkBytes = Math.clamp(chunkBytes, AssetTransferProtocolLimits.MIN_CHUNK_BYTES,
                    AssetTransferProtocolLimits.MAX_CHUNK_BYTES);
            this.policy = policy;
        }

        private void touch() { lastActivityNanos = System.nanoTime(); }
        @Override public void close() {
            closeQuietly(incoming);
            closeQuietly(outgoing);
        }
    }

    private record SessionLookup(PlayerContext owner, ServerSession session) { }
}
