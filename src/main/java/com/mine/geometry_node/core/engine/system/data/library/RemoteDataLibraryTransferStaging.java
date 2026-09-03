package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.WeakHashMap;

/** Owner-bound staging files transported through the shared chunked asset protocol. */
public final class RemoteDataLibraryTransferStaging {
    public static final RemoteDataLibraryTransferStaging INSTANCE = new RemoteDataLibraryTransferStaging();
    private static final String DIRECTORY = ".data-library-transfer";
    private static final long TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private final AssetTransferIoExecutor io =
            new AssetTransferIoExecutor("GeometryNode-DataLibrary-IO", 2, 64);
    private final Map<String, TicketState> tickets = new ConcurrentHashMap<>();
    private final Set<MinecraftServer> cleanedServers = Collections.newSetFromMap(new WeakHashMap<>());
    private volatile boolean initialized;

    private RemoteDataLibraryTransferStaging() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        PlayerEvent.PLAYER_QUIT.register(player -> cleanupPlayer(player.getUUID()));
        LifecycleEvent.SERVER_STOPPING.register(this::cleanupServer);
    }

    public StagingTicket prepareDownload(ServerPlayer player) throws IOException {
        sweepExpired();
        StagingTicket ticket = createTicket(player);
        try {
            MinecraftServer server = player.level().getServer();
            DataLibraryLoadResult loaded = RemoteDataLibraryService.INSTANCE.refresh(server);
            Files.writeString(ticket.absolutePath(), DataLibraryJsonCodec.encode(
                    loaded.document(), server.overworld().registryAccess()), StandardCharsets.UTF_8);
            return ticket;
        } catch (IOException | RuntimeException exception) {
            TicketState state = tickets.get(ticket.token());
            if (state != null) tickets.remove(ticket.token(), state);
            deleteQuietly(ticket.absolutePath());
            throw exception;
        }
    }

    public CompletableFuture<StagingTicket> prepareDownloadAsync(
            ServerPlayer player) {
        return io.submit(() -> prepareDownload(player));
    }

    public CompletableFuture<Void> deleteAsync(ServerPlayer player, Set<DataLibraryObjectKey> keys,
                                               String expectedFingerprint) {
        return io.run(() -> RemoteDataLibraryService.INSTANCE.delete(
                player.level().getServer(), keys, expectedFingerprint));
    }

    public CompletableFuture<Void> createFolderAsync(ServerPlayer player, UUID parentId, String name) {
        return io.run(() -> RemoteDataLibraryService.INSTANCE.createFolder(
                player.level().getServer(), parentId, name));
    }

    public CompletableFuture<Void> updateFolderAsync(ServerPlayer player, UUID folderId,
                                                     UUID parentId, String name, String expectedFingerprint) {
        return io.run(() -> RemoteDataLibraryService.INSTANCE.updateFolder(
                player.level().getServer(), new DataLibraryFolder(folderId, parentId, name), expectedFingerprint));
    }

    public CompletableFuture<Void> moveEntryAsync(ServerPlayer player, UUID entryId,
                                                  UUID parentId, String expectedFingerprint) {
        return io.run(() -> RemoteDataLibraryService.INSTANCE.moveEntry(
                player.level().getServer(), entryId, parentId, expectedFingerprint));
    }

    public CompletableFuture<Void> moveFolderAsync(ServerPlayer player, UUID folderId,
                                                   UUID parentId, String expectedFingerprint) {
        return io.run(() -> RemoteDataLibraryService.INSTANCE.moveFolder(
                player.level().getServer(), folderId, parentId, expectedFingerprint));
    }

    public Path claimDownload(ServerPlayer player, String token) throws IOException {
        TicketState state = requireOwned(player, token);
        if (!tickets.remove(token, state)) throw new IOException("Data Library staging token is no longer available");
        return state.ticket.absolutePath();
    }

    private StagingTicket createTicket(ServerPlayer player) throws IOException {
        String token = UUID.randomUUID().toString();
        Path directory = ServerDataLibraryPaths.file(player.level().getServer()).getParent().resolve(DIRECTORY);
        cleanStaleOnce(player.level().getServer(), directory);
        Files.createDirectories(directory);
        Path path = directory.resolve(token + ".json").toAbsolutePath().normalize();
        StagingTicket ticket = new StagingTicket(token, path);
        tickets.put(token, new TicketState(player.getUUID(), player.level().getServer(),
                System.currentTimeMillis(), ticket));
        return ticket;
    }

    private void cleanStaleOnce(MinecraftServer server, Path directory) throws IOException {
        synchronized (cleanedServers) {
            if (!cleanedServers.add(server)) return;
        }
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file) && !Files.isSymbolicLink(file)) Files.deleteIfExists(file);
            }
        }
    }

    private TicketState requireOwned(ServerPlayer player, String token) throws IOException {
        sweepExpired();
        TicketState state = tickets.get(token);
        if (state == null || !state.playerId.equals(player.getUUID())
                || state.server != player.level().getServer()) {
            throw new IOException("Unknown Data Library staging token");
        }
        return state;
    }

    private void sweepExpired() {
        long cutoff = System.currentTimeMillis() - TTL_MILLIS;
        tickets.forEach((token, state) -> {
            if (state.createdAtMillis >= cutoff || !tickets.remove(token, state)) return;
            deleteQuietly(state.ticket.absolutePath());
        });
    }

    private void cleanupPlayer(UUID playerId) {
        tickets.forEach((token, state) -> {
            if (state.playerId.equals(playerId) && tickets.remove(token, state)) deleteQuietly(state.ticket.absolutePath());
        });
    }

    private void cleanupServer(MinecraftServer server) {
        tickets.forEach((token, state) -> {
            if (state.server == server && tickets.remove(token, state)) deleteQuietly(state.ticket.absolutePath());
        });
        synchronized (cleanedServers) {
            cleanedServers.remove(server);
        }
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record StagingTicket(String token, Path absolutePath) {
    }

    private record TicketState(UUID playerId, MinecraftServer server,
                               long createdAtMillis, StagingTicket ticket) {
    }
}
