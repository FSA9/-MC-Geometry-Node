package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
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

    public CompletableFuture<List<RemoteAssetConflict>> findUploadConflicts(
            MinecraftServer server, List<String> targetPaths) {
        return reads.submit(() -> read(
                () -> RemoteAssetFileService.findUploadConflicts(server, targetPaths)));
    }

    public CompletableFuture<List<AssetDescriptor>> flattenSelection(
            MinecraftServer server, List<String> selectedPaths) {
        return reads.submit(() -> read(
                () -> RemoteAssetFileService.flattenSelection(server, selectedPaths)));
    }

    public CompletableFuture<TransferFile> readTransferFile(MinecraftServer server, String assetPath) {
        return reads.submit(() -> read(() -> {
            Path source = RemoteAssetFileService.resolveTransferSource(server, assetPath);
            BasicFileAttributes before = Files.readAttributes(
                    source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.size() > AssetTransferLimits.MAX_FILE_BYTES) {
                throw new java.io.IOException("Transfer source exceeds file limit: " + before.size());
            }
            byte[] content = Files.readAllBytes(source);
            if (content.length > AssetTransferLimits.MAX_FILE_BYTES) {
                throw new java.io.IOException("Transfer source exceeds file limit: " + content.length);
            }
            BasicFileAttributes after = Files.readAttributes(
                    source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw new java.io.IOException("Transfer source changed while it was being read");
            }
            return new TransferFile(content, content.length, after.lastModifiedTime().toMillis());
        }));
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

    public CompletableFuture<RemoteAssetFileService.UploadCommitResult> commitUpload(
            MinecraftServer server,
            String targetPath,
            byte[] content,
            AssetTransferConflictPolicy conflictPolicy
    ) {
        if (content == null || content.length > AssetTransferLimits.MAX_FILE_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Uploaded file exceeds file limit"));
        }
        return mutations.submit(() -> {
            repositoryLock.writeLock().lock();
            Path temporary = null;
            try {
                Path temporaryDirectory = RemoteAssetFileService.transferTemporaryDirectory(server);
                Files.createDirectories(temporaryDirectory);
                temporary = Files.createTempFile(temporaryDirectory, ".geometrynode-upload-", ".part");
                Files.write(temporary, content);
                Path committedTemporary = temporary;
                return RemoteAssetFileService.commitUpload(
                                server, targetPath, committedTemporary, conflictPolicy)
                        .whenComplete((ignored, throwable) -> {
                            if (throwable != null) deleteQuietly(committedTemporary);
                        });
            } catch (Exception exception) {
                deleteQuietly(temporary);
                throw exception;
            } catch (Error error) {
                deleteQuietly(temporary);
                throw error;
            } finally {
                repositoryLock.writeLock().unlock();
            }
        }).thenCompose(result -> result);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (java.io.IOException ignored) {
        }
    }

    private RemoteAssetOperationResult mutate(Mutation operation) throws Exception {
        repositoryLock.writeLock().lock();
        try {
            return operation.run();
        } finally {
            repositoryLock.writeLock().unlock();
        }
    }

    private <T> T read(ReadOperation<T> operation) throws Exception {
        repositoryLock.readLock().lock();
        try {
            return operation.run();
        } finally {
            repositoryLock.readLock().unlock();
        }
    }

    @FunctionalInterface
    private interface Mutation {
        RemoteAssetOperationResult run() throws Exception;
    }

    @FunctionalInterface
    private interface ReadOperation<T> {
        T run() throws Exception;
    }

    public record ListResult(String directory, List<AssetDescriptor> entries) {
        public ListResult {
            directory = directory == null ? "" : directory;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public record TransferFile(byte[] content, long size, long lastModified) {
    }
}
