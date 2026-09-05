package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RemoteAssetFileService {
    private RemoteAssetFileService() {
    }

    public static List<AssetDescriptor> list(MinecraftServer server, String directoryPath) throws IOException {
        Path directory = resolveDirectory(server, directoryPath);
        Path root = root(server);
        if (!Files.exists(directory) && directory.equals(root)) {
            Files.createDirectories(directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("Remote path is not a directory: " + directoryPath);
        }

        List<AssetDescriptor> entries = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> !Files.isSymbolicLink(path) && !isTransactionArtifact(path))
                    .filter(path -> Files.isDirectory(path)
                            || AssetTypeCatalog.isRecognizedAsset(path))
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> entries.add(toEntry(root, path)));
        }
        return entries;
    }

    public static List<RemoteAssetConflict> findUploadConflicts(MinecraftServer server, List<String> targetPaths) {
        Path root = root(server);
        List<RemoteAssetConflict> conflicts = new ArrayList<>();
        for (String targetPath : targetPaths) {
            Path resolved = resolveFile(server, targetPath);
            validateAssetCandidatePath(resolved);
            if (Files.exists(resolved)) {
                conflicts.add(new RemoteAssetConflict("", ServerAssetPaths.pathToId(root, resolved), Files.isDirectory(resolved)));
            }
        }
        return conflicts;
    }

    public static Path resolveTransferSource(MinecraftServer server, String assetPath) throws IOException {
        Path file = resolveFile(server, assetPath);
        validateAssetCandidatePath(file);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("Remote asset file does not exist: " + assetPath);
        }
        if (!AssetTypeCatalog.isRecognizedAsset(file, assetPath)) {
            throw new IOException("Remote file is not a recognized asset: " + assetPath);
        }
        return file;
    }

    public static Path transferTemporaryDirectory(MinecraftServer server) {
        return root(server).resolveSibling(".geometrynode-transfer").normalize();
    }

    public static Path previewCacheRoot(MinecraftServer server) {
        return root(server).resolveSibling(".geometrynode-nativepreview-cache").toAbsolutePath().normalize();
    }

    public static CompletableFuture<UploadCommitResult> commitVerifiedUpload(
            MinecraftServer server,
            String targetPath,
            Path verifiedTemporaryFile,
            AssetTransferConflictPolicy conflictPolicy
    ) throws IOException {
        Path target = resolveFile(server, targetPath);
        validateAssetCandidatePath(target);
        AssetMetadata oldMetadata = AssetTypeCatalog.inspect(target, targetPath);
        AssetMetadata newMetadata = AssetTypeCatalog.inspect(verifiedTemporaryFile, targetPath);
        if (!newMetadata.isKnown()) {
            throw new IOException("Uploaded file is not a recognized asset: " + targetPath);
        }
        VerifiedAssetCommitter.CommitResult commit =
                VerifiedAssetCommitter.commit(verifiedTemporaryFile, target, conflictPolicy);
        if (commit != VerifiedAssetCommitter.CommitResult.COMMITTED) {
            return CompletableFuture.completedFuture(new UploadCommitResult(commit, null));
        }

        Set<String> affectedTypeIds = new HashSet<>();
        if (oldMetadata.isKnown()) affectedTypeIds.add(oldMetadata.typeId());
        if (newMetadata.isKnown()) affectedTypeIds.add(newMetadata.typeId());
        if (affectedTypeIds.isEmpty()) {
            return CompletableFuture.completedFuture(new UploadCommitResult(commit, null));
        }

        String committedPath = ServerAssetPaths.pathToId(root(server), target);
        return AssetLifecycleRegistry.INSTANCE.refresh(
                        server, affectedTypeIds, Set.of(committedPath), false)
                .handle((ignored, refreshFailure) -> new UploadCommitResult(commit, refreshFailure));
    }

    public static List<AssetDescriptor> flattenSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        Path root = root(server);
        List<AssetDescriptor> files = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (String selectedPath : selectedPaths) {
            Path path = resolvePath(server, selectedPath);
            if (!Files.exists(path)) continue;
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                                    && !isInsideTransactionArtifact(p)
                                    && AssetTypeCatalog.isRecognizedAsset(p))
                            .map(p -> p.toAbsolutePath().normalize())
                            .filter(seen::add)
                            .forEach(p -> files.add(toEntry(root, p)));
                }
            } else if (Files.isRegularFile(path) && !Files.isSymbolicLink(path)
                    && AssetTypeCatalog.isRecognizedAsset(path)) {
                Path normalized = path.toAbsolutePath().normalize();
                if (seen.add(normalized)) {
                    files.add(toEntry(root, normalized));
                }
            }
        }
        files.sort(Comparator.comparing(AssetDescriptor::path, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    public static RemoteAssetOperationResult deleteSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        Set<String> affectedTypes = new HashSet<>();
        Set<String> affectedPaths = new HashSet<>();
        boolean directoryScope = false;
        List<PendingFileMutation> pending = new ArrayList<>();
        try {
            for (Path path : resolveSelectionRoots(server, selectedPaths)) {
                boolean directory = Files.isDirectory(path);
                directoryScope |= directory;
                if (!directory) affectedPaths.add(ServerAssetPaths.pathToId(root(server), path));
                collectAssetTypes(path, affectedTypes);
                RemoteAssetMutationRegistry.PreparedMutation mutation = prepareMutation(
                        server, RemoteAssetMutationRegistry.Operation.DELETE, path, null);
                Path tombstone = transactionSibling(path);
                int index = pending.size();
                pending.add(new PendingFileMutation(path, tombstone, 0, mutation));
                int affected = countRecursively(path);
                pending.set(index, new PendingFileMutation(path, tombstone, affected, mutation));
                moveWithoutReplace(path, tombstone);
            }
        } catch (IOException | RuntimeException exception) {
            rollbackDeletes(pending, exception);
            throw exception;
        }

        int deleted = 0;
        for (PendingFileMutation item : pending) {
            item.participant().commit();
            deleted += item.affectedEntries();
        }
        for (PendingFileMutation item : pending) {
            if (Files.exists(item.target())) {
                try {
                    deleteRecursively(item.target());
                } catch (IOException cleanupException) {
                    System.err.println("[RemoteAsset] Failed to clean deleted asset " + item.target()
                            + ": " + cleanupException.getMessage());
                }
            }
        }
        return new RemoteAssetOperationResult(deleted, affectedTypes, affectedPaths, directoryScope);
    }

    public static RemoteAssetOperationResult copySelection(MinecraftServer server, List<String> sourcePaths,
                                                           String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        Set<String> affectedTypes = new HashSet<>();
        Set<String> affectedPaths = new HashSet<>();
        boolean directoryScope = false;
        List<PendingFileMutation> pending = new ArrayList<>();
        try {
            for (Path source : resolveSelectionRoots(server, sourcePaths)) {
                Path target = resolveAvailableDestination(
                        targetDirectory, source.getFileName().toString(), Files.isDirectory(source));
                boolean directory = Files.isDirectory(source);
                directoryScope |= directory;
                if (directory
                        && target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
                    continue;
                }
                if (!directory) affectedPaths.add(ServerAssetPaths.pathToId(root(server), target));
                RemoteAssetMutationRegistry.PreparedMutation mutation = prepareMutation(
                        server, RemoteAssetMutationRegistry.Operation.COPY, source, target);
                int index = pending.size();
                pending.add(new PendingFileMutation(source, target, 0, mutation));
                collectAssetTypes(source, affectedTypes);
                int copied = copyAtomically(source, target);
                pending.set(index, new PendingFileMutation(source, target, copied, mutation));
            }
        } catch (IOException | RuntimeException exception) {
            rollbackCopies(pending, exception);
            throw exception;
        }

        int copied = 0;
        for (PendingFileMutation item : pending) {
            item.participant().commit();
            copied += item.affectedEntries();
        }
        return new RemoteAssetOperationResult(copied, affectedTypes, affectedPaths, directoryScope);
    }

    public static RemoteAssetOperationResult moveSelection(MinecraftServer server, List<String> sourcePaths,
                                                           String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
        Set<String> affectedTypes = new HashSet<>();
        Set<String> affectedPaths = new HashSet<>();
        boolean directoryScope = false;
        List<PendingFileMutation> pending = new ArrayList<>();
        try {
            for (Path source : resolveSelectionRoots(server, sourcePaths)) {
                Path normalizedSource = source.toAbsolutePath().normalize();
                if (source.getParent() != null
                        && source.getParent().toAbsolutePath().normalize().equals(normalizedTargetDirectory)) {
                    continue;
                }

                Path target = resolveAvailableDestination(
                        targetDirectory, source.getFileName().toString(), Files.isDirectory(source));
                boolean directory = Files.isDirectory(source);
                directoryScope |= directory;
                if (directory && target.toAbsolutePath().normalize().startsWith(normalizedSource)) {
                    continue;
                }
                if (!directory) {
                    affectedPaths.add(ServerAssetPaths.pathToId(root(server), source));
                    affectedPaths.add(ServerAssetPaths.pathToId(root(server), target));
                }
                RemoteAssetMutationRegistry.PreparedMutation mutation = prepareMutation(
                        server, RemoteAssetMutationRegistry.Operation.MOVE, source, target);
                int index = pending.size();
                pending.add(new PendingFileMutation(source, target, 0, mutation));
                collectAssetTypes(source, affectedTypes);
                int moved = moveAtomically(source, target);
                pending.set(index, new PendingFileMutation(source, target, moved, mutation));
            }
        } catch (IOException | RuntimeException exception) {
            rollbackMoves(pending, exception);
            throw exception;
        }

        int moved = 0;
        for (PendingFileMutation item : pending) {
            item.participant().commit();
            moved += item.affectedEntries();
        }
        return new RemoteAssetOperationResult(moved, affectedTypes, affectedPaths, directoryScope);
    }

    public static RemoteAssetOperationResult createDirectory(MinecraftServer server, String directoryPath)
            throws IOException {
        String normalized = ServerAssetPaths.normalizeRelativePath(directoryPath, false);
        Path directory = ServerAssetPaths.resolveUnderRoot(root(server), normalized, false);
        Path parent = directory.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Remote parent directory does not exist: " + normalized);
        }
        Files.createDirectory(directory);
        return new RemoteAssetOperationResult(1, Set.of(), Set.of(), true);
    }

    public static RemoteAssetOperationResult rename(
            MinecraftServer server,
            String sourcePath,
            String destinationPath
    ) throws IOException {
        String normalizedSource = ServerAssetPaths.normalizeRelativePath(sourcePath, false);
        String normalizedDestination = ServerAssetPaths.normalizeRelativePath(destinationPath, false);
        Path source = ServerAssetPaths.resolveUnderRoot(root(server), normalizedSource, false);
        Path destination = ServerAssetPaths.resolveUnderRoot(root(server), normalizedDestination, false);
        if (!Files.exists(source) || Files.isSymbolicLink(source) || isInsideTransactionArtifact(source)) {
            throw new IOException("Remote asset does not exist: " + normalizedSource);
        }
        if (Files.exists(destination)) {
            throw new IOException("Remote destination already exists: " + normalizedDestination);
        }
        if (source.getParent() == null || destination.getParent() == null
                || !source.getParent().equals(destination.getParent())) {
            throw new IOException("Rename destination must remain in the same directory");
        }
        validateSourceName(destination.getFileName().toString());
        if (!Files.isDirectory(source)) {
            validateAssetCandidatePath(destination);
            if (!AssetTypeCatalog.inspect(source, normalizedDestination).isKnown()) {
                throw new IOException("Renamed file would not be a recognized asset: " + normalizedDestination);
            }
        }

        Set<String> affectedTypes = new HashSet<>();
        boolean directoryScope = Files.isDirectory(source);
        Set<String> affectedPaths = directoryScope ? Set.of() : Set.of(normalizedSource, normalizedDestination);
        collectAssetTypes(source, affectedTypes);
        RemoteAssetMutationRegistry.PreparedMutation mutation = prepareMutation(
                server, RemoteAssetMutationRegistry.Operation.RENAME, source, destination);
        try {
            int affected = moveAtomically(source, destination);
            mutation.commit();
            collectAssetTypes(destination, affectedTypes);
            return new RemoteAssetOperationResult(affected, affectedTypes, affectedPaths, directoryScope);
        } catch (IOException | RuntimeException exception) {
            if (Files.exists(destination) && !Files.exists(source)) {
                try {
                    moveAtomically(destination, source);
                } catch (IOException | RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            try {
                mutation.rollback();
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    public static Path root(MinecraftServer server) {
        return ServerAssetPaths.root(server);
    }

    public static Path resolveDirectory(MinecraftServer server, String directoryPath) {
        return resolvePath(server, directoryPath);
    }

    public static Path resolveFile(MinecraftServer server, String filePath) {
        return resolvePath(server, filePath);
    }

    public static String normalizeDirectoryPath(String path) {
        return ServerAssetPaths.normalizeRelativePath(path == null ? "" : path, true);
    }

    private static Path resolvePath(MinecraftServer server, String relativePath) {
        return ServerAssetPaths.resolveUnderRoot(
                root(server), relativePath == null ? "" : relativePath, true);
    }

    private static void validateAssetCandidatePath(Path path) {
        if (!AssetTypeCatalog.isCandidatePath(path.toString())) {
            throw new IllegalArgumentException("Unsupported remote asset type: " + path.getFileName());
        }
    }

    private static int deleteRecursively(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            return Files.deleteIfExists(path) ? 1 : 0;
        }
        if (Files.isDirectory(path)) {
            int deleted = 0;
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleted += deleteRecursively(child);
                }
            }
            Files.deleteIfExists(path);
            return deleted + 1;
        }
        Files.deleteIfExists(path);
        return 1;
    }

    private static int copyRecursively(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            return 0;
        }
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            int copied = 0;
            try (var stream = Files.list(source)) {
                for (Path child : stream.toList()) {
                    if (Files.isSymbolicLink(child)) continue;
                    Path childTarget = target.resolve(child.getFileName().toString());
                    if (Files.isDirectory(child)) {
                        copied += copyRecursively(child, childTarget);
                    } else if (Files.isRegularFile(child)) {
                        copied += copyRecursively(child, resolveAvailableDestination(target, child.getFileName().toString(), false));
                    }
                }
            }
            return copied;
        }
        if (!Files.isRegularFile(source)) {
            return 0;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return 1;
    }

    private static int copyAtomically(Path source, Path target) throws IOException {
        Path staging = transactionSibling(target);
        try {
            int copied = copyRecursively(source, staging);
            commitStaging(staging, target);
            return copied;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(staging, exception);
            throw exception;
        }
    }

    private static int moveAtomically(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            return 0;
        }
        int moved = countRecursively(source);
        Files.createDirectories(target.getParent());
        if (sameFileStore(source, target.getParent())) {
            moveWithoutReplace(source, target);
            return moved;
        }

        // The staged fallback keeps incomplete cross-filesystem copies out of the repository namespace.
        Path staging = transactionSibling(target);
        Path backup = transactionSibling(source);
        boolean sourceBackedUp = false;
        boolean targetCommitted = false;
        try {
            copyRecursively(source, staging);
            moveWithoutReplace(source, backup);
            sourceBackedUp = true;
            commitStaging(staging, target);
            targetCommitted = true;
            try {
                deleteRecursively(backup);
            } catch (IOException cleanupException) {
                System.err.println("[RemoteAsset] Failed to clean committed move backup " + backup
                        + ": " + cleanupException.getMessage());
            }
        } catch (IOException | RuntimeException exception) {
            if (targetCommitted) deleteQuietly(target, exception);
            else deleteQuietly(staging, exception);
            if (sourceBackedUp && Files.exists(backup) && !Files.exists(source)) {
                try {
                    moveWithoutReplace(backup, source);
                } catch (IOException restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            throw exception;
        }
        return moved;
    }

    private static void commitStaging(Path staging, Path target) throws IOException {
        moveWithoutReplace(staging, target);
    }

    private static void moveWithoutReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static boolean sameFileStore(Path source, Path targetParent) {
        try {
            return Files.getFileStore(source).equals(Files.getFileStore(targetParent));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path transactionSibling(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IllegalArgumentException("path has no parent: " + path);
        return parent.resolve(".geometrynode-tx-" + UUID.randomUUID()).normalize();
    }

    private static boolean isTransactionArtifact(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(".geometrynode-tx-");
    }

    private static boolean isInsideTransactionArtifact(Path path) {
        for (Path segment : path) {
            if (segment.toString().startsWith(".geometrynode-tx-")) return true;
        }
        return false;
    }

    private static void deleteQuietly(Path path, Throwable primary) {
        if (path == null || !Files.exists(path)) return;
        try {
            deleteRecursively(path);
        } catch (IOException cleanupException) {
            primary.addSuppressed(cleanupException);
        }
    }

    private static RemoteAssetMutationRegistry.PreparedMutation prepareMutation(
            MinecraftServer server,
            RemoteAssetMutationRegistry.Operation operation,
            Path source,
            Path target
    ) throws IOException {
        try {
            return RemoteAssetMutationRegistry.INSTANCE.prepare(server, operation, source, target);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to prepare remote asset mutation", exception);
        }
    }

    private static List<Path> resolveSelectionRoots(MinecraftServer server, List<String> selectedPaths) {
        Path repositoryRoot = root(server);
        List<Path> candidates = selectedPaths.stream()
                .map(path -> resolvePath(server, path).toAbsolutePath().normalize())
                .filter(path -> Files.exists(path) && !path.equals(repositoryRoot)
                        && !isInsideTransactionArtifact(path))
                .distinct()
                .sorted(Comparator.comparingInt(Path::getNameCount))
                .toList();
        List<Path> roots = new ArrayList<>();
        for (Path candidate : candidates) {
            if (roots.stream().noneMatch(candidate::startsWith)) roots.add(candidate);
        }
        return roots;
    }

    private static void rollbackCopies(List<PendingFileMutation> pending, Throwable primary) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingFileMutation item = pending.get(i);
            deleteQuietly(item.target(), primary);
            rollbackParticipant(item, primary);
        }
    }

    private static void rollbackMoves(List<PendingFileMutation> pending, Throwable primary) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingFileMutation item = pending.get(i);
            if (Files.exists(item.target()) && !Files.exists(item.source())) {
                try {
                    moveAtomically(item.target(), item.source());
                } catch (IOException | RuntimeException restoreException) {
                    primary.addSuppressed(restoreException);
                }
            }
            rollbackParticipant(item, primary);
        }
    }

    private static void rollbackDeletes(List<PendingFileMutation> pending, Throwable primary) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingFileMutation item = pending.get(i);
            if (Files.exists(item.target()) && !Files.exists(item.source())) {
                try {
                    moveWithoutReplace(item.target(), item.source());
                } catch (IOException restoreException) {
                    primary.addSuppressed(restoreException);
                }
            }
            rollbackParticipant(item, primary);
        }
    }

    private static void rollbackParticipant(PendingFileMutation item, Throwable primary) {
        try {
            item.participant().rollback();
        } catch (RuntimeException rollbackException) {
            primary.addSuppressed(rollbackException);
        }
    }

    private record PendingFileMutation(
            Path source,
            Path target,
            int affectedEntries,
            RemoteAssetMutationRegistry.PreparedMutation participant
    ) {
    }

    private static int countRecursively(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.exists(path)) {
            return 0;
        }
        if (!Files.isDirectory(path)) {
            return 1;
        }

        int count = 1;
        try (var stream = Files.list(path)) {
            for (Path child : stream.toList()) {
                count += countRecursively(child);
            }
        }
        return count;
    }

    private static Path resolveAvailableDestination(Path directory, String sourceName, boolean directoryName) throws IOException {
        validateSourceName(sourceName);
        Path candidate = directory.resolve(sourceName).normalize();
        ensureInsideRoot(directory, candidate);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = sourceName;
        String extension = "";
        if (!directoryName) {
            int dotIndex = sourceName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = sourceName.substring(0, dotIndex);
                extension = sourceName.substring(dotIndex);
            }
        }

        int counter = 1;
        Path resolved;
        do {
            resolved = directory.resolve(baseName + "_" + counter + extension).normalize();
            ensureInsideRoot(directory, resolved);
            counter++;
        } while (Files.exists(resolved));
        return resolved;
    }

    private static void validateSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty() || sourceName.contains("/") || sourceName.contains("\\")) {
            throw new IllegalArgumentException("invalid source name: " + sourceName);
        }
    }

    private static void ensureInsideRoot(Path rootLike, Path candidate) throws IOException {
        Path root = rootLike.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(root)) {
            throw new IOException("Remote asset path escapes target directory: " + candidate);
        }
    }

    private static AssetDescriptor toEntry(Path root, Path path) {
        boolean directory = Files.isDirectory(path);
        long size = 0L;
        long lastModified = 0L;
        if (!directory) {
            try {
                size = Files.size(path);
            } catch (IOException ignored) {
                size = 0L;
            }
        }
        try {
            lastModified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            lastModified = 0L;
        }
        String assetPath = ServerAssetPaths.pathToId(root, path);
        AssetMetadata metadata = directory ? AssetMetadata.UNKNOWN : AssetTypeCatalog.inspect(path, assetPath);
        return new AssetDescriptor(
                assetPath,
                path.getFileName().toString(),
                directory,
                size,
                lastModified,
                metadata
        );
    }

    private static void collectAssetTypes(Path path, Set<String> destination) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.exists(path) || isInsideTransactionArtifact(path)) return;
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) collectAssetTypes(child, destination);
            }
            return;
        }
        if (!Files.isRegularFile(path)) return;
        AssetMetadata metadata = AssetTypeCatalog.inspect(path);
        if (metadata.isKnown()) destination.add(metadata.typeId());
    }

    /** A storage commit remains successful even when a dependent runtime refresh reports a warning. */
    public record UploadCommitResult(VerifiedAssetCommitter.CommitResult commit,
                                     Throwable refreshFailure) {
    }

}
