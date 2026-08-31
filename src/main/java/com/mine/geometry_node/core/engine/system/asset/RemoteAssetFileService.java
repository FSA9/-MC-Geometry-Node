package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.preview.ServerAssetPreviewAssociations;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class RemoteAssetFileService {
    private RemoteAssetFileService() {
    }

    public static List<RemoteAssetEntry> list(MinecraftServer server, String directoryPath) throws IOException {
        Path directory = resolveDirectory(server, directoryPath);
        Path root = root(server);
        if (!Files.exists(directory) && directory.equals(root)) {
            Files.createDirectories(directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("Remote path is not a directory: " + directoryPath);
        }

        List<RemoteAssetEntry> entries = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isDirectory(path)
                            || AssetTypeCatalog.isTransferablePath(path.toString()))
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
            validateAssetFilePath(resolved);
            if (Files.exists(resolved)) {
                conflicts.add(new RemoteAssetConflict("", ServerAssetPaths.pathToId(root, resolved), Files.isDirectory(resolved)));
            }
        }
        return conflicts;
    }

    public static Path resolveTransferSource(MinecraftServer server, String assetPath) throws IOException {
        Path file = resolveFile(server, assetPath);
        validateAssetFilePath(file);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("Remote asset file does not exist: " + assetPath);
        }
        return file;
    }

    public static Path transferTemporaryDirectory(MinecraftServer server) {
        return root(server).resolveSibling(".geometrynode-transfer").normalize();
    }

    public static Path previewCacheRoot(MinecraftServer server) {
        return root(server).resolveSibling(".geometrynode-nativepreview-cache").toAbsolutePath().normalize();
    }

    public static CompletableFuture<VerifiedAssetCommitter.CommitResult> commitVerifiedUpload(
            MinecraftServer server,
            String targetPath,
            Path verifiedTemporaryFile,
            AssetTransferConflictPolicy conflictPolicy
    ) throws IOException {
        Path target = resolveFile(server, targetPath);
        validateAssetFilePath(target);
        AssetMetadata oldMetadata = AssetTypeCatalog.inspect(target, targetPath);
        AssetMetadata newMetadata = AssetTypeCatalog.inspect(verifiedTemporaryFile, targetPath);
        VerifiedAssetCommitter.CommitResult commit =
                VerifiedAssetCommitter.commit(verifiedTemporaryFile, target, conflictPolicy);
        if (commit != VerifiedAssetCommitter.CommitResult.COMMITTED) {
            return CompletableFuture.completedFuture(commit);
        }

        Set<String> affectedTypeIds = new HashSet<>();
        if (oldMetadata.isKnown()) affectedTypeIds.add(oldMetadata.typeId());
        if (newMetadata.isKnown()) affectedTypeIds.add(newMetadata.typeId());
        if (affectedTypeIds.isEmpty()) return CompletableFuture.completedFuture(commit);

        CompletableFuture<VerifiedAssetCommitter.CommitResult> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                AssetLifecycleRegistry.INSTANCE.refresh(server, affectedTypeIds);
                result.complete(commit);
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    public static List<RemoteAssetEntry> flattenSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        Path root = root(server);
        List<RemoteAssetEntry> files = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (String selectedPath : selectedPaths) {
            Path path = resolvePath(server, selectedPath);
            if (!Files.exists(path)) continue;
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                                    && AssetTypeCatalog.isTransferablePath(p.toString()))
                            .map(p -> p.toAbsolutePath().normalize())
                            .filter(seen::add)
                            .forEach(p -> files.add(toEntry(root, p)));
                }
            } else if (Files.isRegularFile(path) && !Files.isSymbolicLink(path)
                    && AssetTypeCatalog.isTransferablePath(path.toString())) {
                Path normalized = path.toAbsolutePath().normalize();
                if (seen.add(normalized)) {
                    files.add(toEntry(root, normalized));
                }
            }
        }
        files.sort(Comparator.comparing(RemoteAssetEntry::path, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    public static RemoteAssetOperationResult deleteSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        int deleted = 0;
        Set<Path> roots = new HashSet<>();
        Set<String> affectedTypes = new HashSet<>();
        for (String selectedPath : selectedPaths) {
            Path path = resolvePath(server, selectedPath);
            if (!Files.exists(path) || path.equals(root(server))) continue;
            if (roots.add(path.toAbsolutePath().normalize())) {
                collectAssetTypes(path, affectedTypes);
                deleted += deleteRecursively(path);
            }
        }
        return new RemoteAssetOperationResult(deleted, affectedTypes);
    }

    public static RemoteAssetOperationResult copySelection(MinecraftServer server, List<String> sourcePaths,
                                                           String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        int copied = 0;
        Set<String> affectedTypes = new HashSet<>();
        for (String sourcePath : sourcePaths) {
            Path source = resolvePath(server, sourcePath);
            if (!Files.exists(source) || source.equals(root(server))) continue;
            Path target = resolveAvailableDestination(targetDirectory, source.getFileName().toString(), Files.isDirectory(source));
            if (Files.isDirectory(source) && target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
                continue;
            }
            ServerAssetPreviewAssociations.Migration previews =
                    ServerAssetPreviewAssociations.capture(server, source, target);
            collectAssetTypes(source, affectedTypes);
            copied += copyRecursively(source, target);
            previews.apply();
        }
        return new RemoteAssetOperationResult(copied, affectedTypes);
    }

    public static RemoteAssetOperationResult moveSelection(MinecraftServer server, List<String> sourcePaths,
                                                           String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
        int moved = 0;
        Set<String> affectedTypes = new HashSet<>();
        for (String sourcePath : sourcePaths) {
            Path source = resolvePath(server, sourcePath);
            Path normalizedSource = source.toAbsolutePath().normalize();
            if (!Files.exists(source) || source.equals(root(server))) continue;
            if (source.getParent() != null && source.getParent().toAbsolutePath().normalize().equals(normalizedTargetDirectory)) {
                continue;
            }

            Path target = resolveAvailableDestination(targetDirectory, source.getFileName().toString(), Files.isDirectory(source));
            if (Files.isDirectory(source) && target.toAbsolutePath().normalize().startsWith(normalizedSource)) {
                continue;
            }
            ServerAssetPreviewAssociations.Migration previews =
                    ServerAssetPreviewAssociations.capture(server, source, target);
            collectAssetTypes(source, affectedTypes);
            moved += moveRecursively(source, target);
            previews.apply();
        }
        return new RemoteAssetOperationResult(moved, affectedTypes);
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
        Path root = root(server);
        String normalized = ServerAssetPaths.normalizeRelativePath(relativePath == null ? "" : relativePath, true);
        Path resolved = normalized.isEmpty() ? root : root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Remote asset path escapes root: " + relativePath);
        }
        return resolved;
    }

    private static void validateAssetFilePath(Path path) {
        if (!AssetTypeCatalog.isTransferablePath(path.toString())) {
            throw new IllegalArgumentException("Unsupported remote asset type: " + path.getFileName());
        }
    }

    private static int deleteRecursively(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            return 0;
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

    private static int moveRecursively(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            return 0;
        }
        int moved = countRecursively(source);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target);
        } catch (IOException moveException) {
            copyRecursively(source, target);
            deleteRecursively(source);
        }
        return moved;
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

    private static RemoteAssetEntry toEntry(Path root, Path path) {
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
        return new RemoteAssetEntry(
                assetPath,
                path.getFileName().toString(),
                directory,
                size,
                lastModified,
                metadata.typeId(),
                metadata.variantId()
        );
    }

    private static void collectAssetTypes(Path path, Set<String> destination) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.exists(path)) return;
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

}
