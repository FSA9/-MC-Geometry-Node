package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Runs remote repository disk work outside the Minecraft server thread. */
public final class RemoteAssetRepositoryService {
    public static final RemoteAssetRepositoryService INSTANCE = new RemoteAssetRepositoryService();

    private final AssetTransferIoExecutor reads =
            new AssetTransferIoExecutor("GeometryNode-AssetRepository-ReadIO", 2, 128);
    private final AssetTransferIoExecutor mutations =
            new AssetTransferIoExecutor("GeometryNode-AssetRepository-MutationIO", 1, 64);
    private final ReentrantReadWriteLock repositoryLock = new ReentrantReadWriteLock();

    private RemoteAssetRepositoryService() {
    }

    public CompletableFuture<ListResult> list(
            MinecraftServer server,
            String requestedDirectory,
        boolean createIfMissing
    ) {
        return reads.submit(() -> {
            Lock lock = createIfMissing ? repositoryLock.writeLock() : repositoryLock.readLock();
            lock.lock();
            try {
                String directory = RemoteAssetFileService.normalizeDirectoryPath(requestedDirectory);
                if (createIfMissing) {
                    Files.createDirectories(RemoteAssetFileService.resolveDirectory(server, directory));
                }
                return new ListResult(directory, RemoteAssetFileService.list(server, directory));
            } finally {
                lock.unlock();
            }
        });
    }

    public CompletableFuture<RemoteAssetOperationResult> delete(
            MinecraftServer server,
            List<String> paths
    ) {
        return mutations.submit(() -> mutate(() -> RemoteAssetFileService.deleteSelection(server, paths)));
    }

    public CompletableFuture<RemoteAssetOperationResult> copy(
            MinecraftServer server,
            List<String> paths,
            String targetDirectory
    ) {
        return mutations.submit(() -> mutate(
                () -> RemoteAssetFileService.copySelection(server, paths, targetDirectory)));
    }

    public CompletableFuture<RemoteAssetOperationResult> move(
            MinecraftServer server,
            List<String> paths,
            String targetDirectory
    ) {
        return mutations.submit(() -> mutate(
                () -> RemoteAssetFileService.moveSelection(server, paths, targetDirectory)));
    }

    public CompletableFuture<RemoteAssetOperationResult> createDirectory(
            MinecraftServer server,
            String directoryPath
    ) {
        return mutations.submit(() -> mutate(
                () -> RemoteAssetFileService.createDirectory(server, directoryPath)));
    }

    public CompletableFuture<RemoteAssetOperationResult> rename(
            MinecraftServer server,
            String sourcePath,
            String destinationPath
    ) {
        return mutations.submit(() -> mutate(
                () -> RemoteAssetFileService.rename(server, sourcePath, destinationPath)));
    }

    public CompletableFuture<VerifiedAssetCommitter.CommitResult> commitVerifiedUpload(
            MinecraftServer server,
            String targetPath,
            Path verifiedTemporaryFile,
            AssetTransferConflictPolicy conflictPolicy
    ) {
        return mutations.submit(() -> {
            repositoryLock.writeLock().lock();
            try {
                return RemoteAssetFileService.commitVerifiedUpload(
                        server, targetPath, verifiedTemporaryFile, conflictPolicy);
            } finally {
                repositoryLock.writeLock().unlock();
            }
        }).thenCompose(result -> result);
    }

    private RemoteAssetOperationResult mutate(Mutation operation) throws Exception {
        repositoryLock.writeLock().lock();
        try {
            return operation.run();
        } finally {
            repositoryLock.writeLock().unlock();
        }
    }

    @FunctionalInterface
    private interface Mutation {
        RemoteAssetOperationResult run() throws Exception;
    }

    public record ListResult(String directory, List<AssetDescriptor> entries) {
        public ListResult {
            directory = directory == null ? "" : directory;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
