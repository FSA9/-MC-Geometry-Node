package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AtomicAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Runs remote repository disk work outside the Minecraft server thread. */
public final class RemoteAssetRepositoryService implements ServerEngine {
    public static final RemoteAssetRepositoryService INSTANCE = new RemoteAssetRepositoryService();

    private final AssetTransferIoExecutor reads =
            new AssetTransferIoExecutor("GeometryNode-AssetRepository-ReadIO", 2, 128);
    private final AssetTransferIoExecutor mutations =
            new AssetTransferIoExecutor("GeometryNode-AssetRepository-MutationIO", 2, 64);
    private final Object transactionLock = new Object();
    private final Map<MinecraftServer, ServerTransactions> transactions =
            new IdentityHashMap<>();

    private RemoteAssetRepositoryService() {
    }

    @Override
    public String id() {
        return "geometry_node:remote_asset_repository";
    }

    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        synchronized (transactionLock) {
            ServerTransactions current = transactions.get(server);
            if (current != null && current.active) return;
            transactions.put(server, new ServerTransactions());
        }
    }

    @Override
    public void shutdown(MinecraftServer server) {
        ServerTransactions state;
        synchronized (transactionLock) {
            state = transactions.remove(server);
            if (state != null) state.active = false;
        }
        if (state != null) state.tail.cancel(false);
    }

    public CompletableFuture<ListResult> list(
            MinecraftServer server,
            String requestedDirectory,
        boolean createIfMissing
    ) {
        if (createIfMissing) {
            return enqueueTransaction(server, state -> reads.submit(() -> {
                Lock writeLock = state.repositoryLock.writeLock();
                writeLock.lock();
                try {
                    requireActive(server, state);
                    String directory = RemoteAssetFileService.normalizeDirectoryPath(requestedDirectory);
                    Files.createDirectories(RemoteAssetFileService.resolveDirectory(server, directory));
                    return new ListResult(directory, RemoteAssetFileService.list(server, directory));
                } finally {
                    writeLock.unlock();
                }
            }));
        }
        return reads.submit(() -> read(server, () -> {
                String directory = RemoteAssetFileService.normalizeDirectoryPath(requestedDirectory);
                return new ListResult(directory, RemoteAssetFileService.list(server, directory));
            }));
    }

    public CompletableFuture<List<RemoteAssetConflict>> findUploadConflicts(
            MinecraftServer server, List<String> targetPaths) {
        return reads.submit(() -> read(server,
                () -> RemoteAssetFileService.findUploadConflicts(server, targetPaths)));
    }

    public CompletableFuture<List<AssetDescriptor>> flattenSelection(
            MinecraftServer server, List<String> selectedPaths) {
        return reads.submit(() -> read(server,
                () -> RemoteAssetFileService.flattenSelection(server, selectedPaths)));
    }

    public CompletableFuture<TransferFile> readTransferFile(MinecraftServer server, String assetPath) {
        return reads.submit(() -> read(server, () -> {
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
        return submitMutation(server, () -> RemoteAssetFileService.deleteSelection(server, paths));
    }

    public CompletableFuture<RemoteAssetOperationResult> copy(
            MinecraftServer server,
            List<String> paths,
            String targetDirectory
    ) {
        return submitMutation(server,
                () -> RemoteAssetFileService.copySelection(server, paths, targetDirectory));
    }

    public CompletableFuture<RemoteAssetOperationResult> move(
            MinecraftServer server,
            List<String> paths,
            String targetDirectory
    ) {
        return submitMutation(server,
                () -> RemoteAssetFileService.moveSelection(server, paths, targetDirectory));
    }

    public CompletableFuture<RemoteAssetOperationResult> createDirectory(
            MinecraftServer server,
            String directoryPath
    ) {
        return submitMutation(server,
                () -> RemoteAssetFileService.createDirectory(server, directoryPath));
    }

    public CompletableFuture<RemoteAssetOperationResult> rename(
            MinecraftServer server,
            String sourcePath,
            String destinationPath
    ) {
        return submitMutation(server,
                () -> RemoteAssetFileService.rename(server, sourcePath, destinationPath));
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
        return enqueueTransaction(server, state -> mutations.submit(() -> {
            Lock writeLock = state.repositoryLock.writeLock();
            writeLock.lock();
            Path temporary = null;
            try {
                requireActive(server, state);
                Path temporaryDirectory = RemoteAssetFileService.transferTemporaryDirectory(server);
                Files.createDirectories(temporaryDirectory);
                temporary = Files.createTempFile(temporaryDirectory, ".geometrynode-upload-", ".part");
                Files.write(temporary, content);
                Path committedTemporary = temporary;
                return RemoteAssetFileService.commitUpload(
                        server, targetPath, committedTemporary, conflictPolicy);
            } catch (Exception exception) {
                deleteQuietly(temporary);
                throw exception;
            } catch (Error error) {
                deleteQuietly(temporary);
                throw error;
            } finally {
                writeLock.unlock();
            }
        }).thenCompose(result -> refreshUpload(server, result)));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (java.io.IOException ignored) {
        }
    }

    private CompletableFuture<RemoteAssetOperationResult> submitMutation(
            MinecraftServer server, Mutation operation) {
        return enqueueTransaction(server, state -> mutations.submit(
                        () -> mutate(server, state, operation))
                .thenCompose(result -> refreshMutation(server, result))
                .exceptionallyCompose(error -> refreshAfterFailure(server, error)));
    }

    private RemoteAssetOperationResult mutate(MinecraftServer server, ServerTransactions state,
                                              Mutation operation) throws Exception {
        Lock writeLock = state.repositoryLock.writeLock();
        writeLock.lock();
        try {
            requireActive(server, state);
            return operation.run();
        } finally {
            writeLock.unlock();
        }
    }

    private <T> T read(MinecraftServer server, ReadOperation<T> operation) throws Exception {
        ServerTransactions state = requireActive(server);
        Lock readLock = state.repositoryLock.readLock();
        readLock.lock();
        try {
            requireActive(server, state);
            return operation.run();
        } finally {
            readLock.unlock();
        }
    }

    private CompletableFuture<RemoteAssetOperationResult> refreshMutation(
            MinecraftServer server, RemoteAssetOperationResult result) {
        return AssetLifecycleDispatcher.INSTANCE.refresh(server, result.affectedTypeIds(),
                        result.affectedPaths(), result.directoryScope())
                .handle((ignored, failure) -> result.withRefreshFailure(failure));
    }

    private CompletableFuture<RemoteAssetFileService.UploadCommitResult> refreshUpload(
            MinecraftServer server, RemoteAssetFileService.UploadCommitResult result) {
        if (result.commit() != AtomicAssetCommitter.CommitResult.COMMITTED) {
            return CompletableFuture.completedFuture(result);
        }
        return AssetLifecycleDispatcher.INSTANCE.refresh(server, result.affectedTypeIds(),
                        result.affectedPaths(), result.directoryScope())
                .handle((ignored, failure) -> result.withRefreshFailure(failure));
    }

    private <T> CompletableFuture<T> refreshAfterFailure(MinecraftServer server, Throwable failure) {
        if (isCancellation(failure)) return CompletableFuture.failedFuture(failure);
        return AssetLifecycleDispatcher.INSTANCE.refreshAll(server).handle((ignored, refreshFailure) -> {
            if (refreshFailure != null) failure.addSuppressed(refreshFailure);
            throw new java.util.concurrent.CompletionException(failure);
        });
    }

    private static boolean isCancellation(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != current) {
            if (current instanceof java.util.concurrent.CancellationException) return true;
            current = current.getCause();
        }
        return false;
    }

    private <T> CompletableFuture<T> enqueueTransaction(
            MinecraftServer server, TransactionOperation<T> operation) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(operation, "operation");
        CompletableFuture<T> result;
        ServerTransactions state;
        synchronized (transactionLock) {
            state = transactions.get(server);
            if (state == null || !state.active) {
                return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException(
                        "Asset repository is not active for this server"));
            }
            CompletableFuture<Void> previous = state.tail;
            result = previous.handle((ignored, failure) -> null).thenCompose(ignored -> {
                synchronized (transactionLock) {
                    if (!state.active || transactions.get(server) != state) {
                        return CompletableFuture.failedFuture(
                                new java.util.concurrent.CancellationException(
                                        "Asset repository transaction was cancelled during server shutdown"));
                    }
                }
                try {
                    return operation.start(state);
                } catch (Throwable error) {
                    return CompletableFuture.failedFuture(error);
                }
            });
            state.tail = result.handle((ignored, failure) -> null);
        }
        return result;
    }

    private ServerTransactions requireActive(MinecraftServer server) {
        synchronized (transactionLock) {
            ServerTransactions state = transactions.get(server);
            if (state == null || !state.active) {
                throw new java.util.concurrent.CancellationException(
                        "Asset repository is not active for this server");
            }
            return state;
        }
    }

    private void requireActive(MinecraftServer server, ServerTransactions state) {
        synchronized (transactionLock) {
            if (!state.active || transactions.get(server) != state) {
                throw new java.util.concurrent.CancellationException(
                        "Asset repository transaction was cancelled during server shutdown");
            }
        }
    }

    @FunctionalInterface
    private interface TransactionOperation<T> {
        CompletableFuture<T> start(ServerTransactions state);
    }

    private static final class ServerTransactions {
        private final ReentrantReadWriteLock repositoryLock = new ReentrantReadWriteLock();
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
        private boolean active = true;
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
