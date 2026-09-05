package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Runs Data Library serialization and persistence outside the Minecraft server thread. */
public final class RemoteDataLibraryRepositoryService {
    public static final RemoteDataLibraryRepositoryService INSTANCE = new RemoteDataLibraryRepositoryService();

    private final AssetTransferIoExecutor io =
            new AssetTransferIoExecutor("GeometryNode-DataLibrary-IO", 2, 64);

    private RemoteDataLibraryRepositoryService() {
    }

    public CompletableFuture<byte[]> readSnapshot(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return io.submit(() -> {
            DataLibraryLoadResult loaded = RemoteDataLibraryService.INSTANCE.refresh(server);
            byte[] content = DataLibraryJsonCodec.encode(
                    loaded.document(), server.overworld().registryAccess()).getBytes(StandardCharsets.UTF_8);
            if (content.length > AssetTransferLimits.MAX_FILE_BYTES) {
                throw new IllegalStateException("Data Library exceeds file limit: " + content.length);
            }
            return content;
        });
    }

    public CompletableFuture<Void> createAsync(ServerPlayer player, byte[] content) {
        return updateFromJson(player, content, false);
    }

    public CompletableFuture<Void> updateAsync(ServerPlayer player, byte[] content) {
        return updateFromJson(player, content, true);
    }

    public CompletableFuture<Void> deleteAsync(ServerPlayer player, Set<DataLibraryObjectKey> keys,
                                               String expectedFingerprint) {
        MinecraftServer server = player.level().getServer();
        return io.run(() -> RemoteDataLibraryService.INSTANCE.delete(
                server, keys, expectedFingerprint));
    }

    public CompletableFuture<Void> createFolderAsync(ServerPlayer player, UUID parentId, String name) {
        MinecraftServer server = player.level().getServer();
        return io.run(() -> RemoteDataLibraryService.INSTANCE.createFolder(
                server, parentId, name));
    }

    public CompletableFuture<Void> updateFolderAsync(ServerPlayer player, UUID folderId,
                                                     UUID parentId, String name, String expectedFingerprint) {
        MinecraftServer server = player.level().getServer();
        return io.run(() -> RemoteDataLibraryService.INSTANCE.updateFolder(
                server, new DataLibraryFolder(folderId, parentId, name), expectedFingerprint));
    }

    public CompletableFuture<Void> moveEntryAsync(ServerPlayer player, UUID entryId,
                                                  UUID parentId, String expectedFingerprint) {
        MinecraftServer server = player.level().getServer();
        return io.run(() -> RemoteDataLibraryService.INSTANCE.moveEntry(
                server, entryId, parentId, expectedFingerprint));
    }

    public CompletableFuture<Void> moveFolderAsync(ServerPlayer player, UUID folderId,
                                                   UUID parentId, String expectedFingerprint) {
        MinecraftServer server = player.level().getServer();
        return io.run(() -> RemoteDataLibraryService.INSTANCE.moveFolder(
                server, folderId, parentId, expectedFingerprint));
    }

    private CompletableFuture<Void> updateFromJson(ServerPlayer player, byte[] content, boolean update) {
        if (content == null || content.length > AssetTransferLimits.MAX_FILE_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Data Library exceeds file limit"));
        }
        MinecraftServer server = player.level().getServer();
        return io.run(() -> {
            DataLibraryLoadResult incoming = DataLibraryJsonCodec.decode(
                    new String(content, StandardCharsets.UTF_8), server.overworld().registryAccess());
            if (!incoming.diagnostics().isEmpty()) {
                String details = incoming.diagnostics().stream()
                        .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
                        .collect(Collectors.joining("; "));
                throw new IllegalArgumentException("Uploaded Data Library contains invalid entries: " + details);
            }
            if (update) {
                Map<UUID, String> fingerprints = incoming.expectedFingerprints();
                RemoteDataLibraryService.INSTANCE.update(server, incoming.document(), fingerprints);
            } else {
                RemoteDataLibraryService.INSTANCE.create(server, incoming.document());
            }
        });
    }
}
