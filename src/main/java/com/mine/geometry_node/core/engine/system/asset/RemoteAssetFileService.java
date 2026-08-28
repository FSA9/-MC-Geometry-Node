package com.mine.geometry_node.core.engine.system.asset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetDescriptor;
import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.preview.ServerAssetPreviewAssociations;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class RemoteAssetFileService {
    private RemoteAssetFileService() {
    }

    public static List<RemoteGraphEntry> list(MinecraftServer server, String directoryPath) throws IOException {
        Path directory = resolveDirectory(server, directoryPath);
        Path root = root(server);
        if (!Files.exists(directory) && directory.equals(root)) {
            Files.createDirectories(directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("Remote path is not a directory: " + directoryPath);
        }

        List<RemoteGraphEntry> entries = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isDirectory(path)
                            || AssetTransferPolicy.isTransferablePath(path.toString()))
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> entries.add(toEntry(root, path)));
        }
        return entries;
    }

    public static List<RemoteGraphConflict> findUploadConflicts(MinecraftServer server, List<String> targetPaths) {
        Path root = root(server);
        List<RemoteGraphConflict> conflicts = new ArrayList<>();
        for (String targetPath : targetPaths) {
            Path resolved = resolveFile(server, targetPath);
            validateAssetFilePath(resolved);
            if (Files.exists(resolved)) {
                conflicts.add(new RemoteGraphConflict("", GraphPathMapper.pathToId(root, resolved), Files.isDirectory(resolved)));
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
        return root(server).resolveSibling(".geometrynode-preview-cache").toAbsolutePath().normalize();
    }

    public static CompletableFuture<VerifiedAssetCommitter.CommitResult> commitVerifiedUpload(
            MinecraftServer server,
            String targetPath,
            Path verifiedTemporaryFile,
            AssetTransferConflictPolicy conflictPolicy
    ) throws IOException {
        Path target = resolveFile(server, targetPath);
        validateAssetFilePath(target);
        if (!AssetTransferPolicy.isGraphPath(targetPath)) {
            return CompletableFuture.completedFuture(
                    VerifiedAssetCommitter.commit(verifiedTemporaryFile, target, conflictPolicy));
        }

        if (Files.exists(target)) {
            if (conflictPolicy == AssetTransferConflictPolicy.SKIP) {
                Files.deleteIfExists(verifiedTemporaryFile);
                return CompletableFuture.completedFuture(VerifiedAssetCommitter.CommitResult.SKIPPED);
            }
            if (conflictPolicy == AssetTransferConflictPolicy.FAIL_IF_EXISTS) {
                throw new VerifiedAssetCommitter.AssetTransferConflictException("Transfer target now exists");
            }
        }
        String graphJson = Files.readString(verifiedTemporaryFile, StandardCharsets.UTF_8);
        CompletableFuture<VerifiedAssetCommitter.CommitResult> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                DynamicGraphManager.saveAndHotReload(
                        server, GraphPathMapper.pathToId(root(server), target), graphJson);
                Files.deleteIfExists(verifiedTemporaryFile);
                result.complete(VerifiedAssetCommitter.CommitResult.COMMITTED);
            } catch (Exception exception) {
                try { Files.deleteIfExists(verifiedTemporaryFile); } catch (IOException ignored) { }
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    public static List<RemoteGraphEntry> flattenSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        Path root = root(server);
        List<RemoteGraphEntry> files = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (String selectedPath : selectedPaths) {
            Path path = resolvePath(server, selectedPath, false);
            if (!Files.exists(path)) continue;
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                                    && AssetTransferPolicy.isTransferablePath(p.toString()))
                            .map(p -> p.toAbsolutePath().normalize())
                            .filter(seen::add)
                            .forEach(p -> files.add(toEntry(root, p)));
                }
            } else if (Files.isRegularFile(path) && !Files.isSymbolicLink(path)
                    && AssetTransferPolicy.isTransferablePath(path.toString())) {
                Path normalized = path.toAbsolutePath().normalize();
                if (seen.add(normalized)) {
                    files.add(toEntry(root, normalized));
                }
            }
        }
        files.sort(Comparator.comparing(RemoteGraphEntry::path, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    public static int deleteSelection(MinecraftServer server, List<String> selectedPaths) throws IOException {
        int deleted = 0;
        Set<Path> roots = new HashSet<>();
        for (String selectedPath : selectedPaths) {
            Path path = resolvePath(server, selectedPath, false);
            if (!Files.exists(path) || path.equals(root(server))) continue;
            if (roots.add(path.toAbsolutePath().normalize())) {
                deleted += deleteRecursively(path);
            }
        }
        return deleted;
    }

    public static int copySelection(MinecraftServer server, List<String> sourcePaths, String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        int copied = 0;
        for (String sourcePath : sourcePaths) {
            Path source = resolvePath(server, sourcePath, false);
            if (!Files.exists(source) || source.equals(root(server))) continue;
            Path target = resolveAvailableDestination(targetDirectory, source.getFileName().toString(), Files.isDirectory(source));
            if (Files.isDirectory(source) && target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
                continue;
            }
            ServerAssetPreviewAssociations.Migration previews =
                    ServerAssetPreviewAssociations.capture(server, source, target);
            copied += copyRecursively(source, target);
            previews.apply();
        }
        return copied;
    }

    public static int moveSelection(MinecraftServer server, List<String> sourcePaths, String targetDirectoryPath) throws IOException {
        Path targetDirectory = resolveDirectory(server, targetDirectoryPath);
        Files.createDirectories(targetDirectory);
        if (!Files.isDirectory(targetDirectory)) {
            throw new IOException("Remote target is not a directory: " + targetDirectoryPath);
        }

        Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
        int moved = 0;
        for (String sourcePath : sourcePaths) {
            Path source = resolvePath(server, sourcePath, false);
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
            moved += moveRecursively(source, target);
            previews.apply();
        }
        return moved;
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(DynamicGraphManager.GRAPH_DIR).toAbsolutePath().normalize();
    }

    public static Path resolveDirectory(MinecraftServer server, String directoryPath) {
        return resolvePath(server, directoryPath, false);
    }

    public static Path resolveFile(MinecraftServer server, String filePath) {
        return resolvePath(server, filePath, false);
    }

    public static String normalizeDirectoryPath(String path) {
        return GraphPathMapper.normalizeRelativePath(path == null ? "" : path, false);
    }

    private static Path resolvePath(MinecraftServer server, String relativePath, boolean ensureJsonExtension) {
        Path root = root(server);
        String normalized = GraphPathMapper.normalizeRelativePath(relativePath == null ? "" : relativePath, ensureJsonExtension);
        Path resolved = normalized.isEmpty() ? root : root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Remote graph path escapes root: " + relativePath);
        }
        return resolved;
    }

    private static void validateAssetFilePath(Path path) {
        if (!AssetTransferPolicy.isTransferablePath(path.toString())) {
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
            throw new IOException("Remote graph path escapes target directory: " + candidate);
        }
    }

    private static RemoteGraphEntry toEntry(Path root, Path path) {
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
        String graphId = GraphPathMapper.pathToId(root, path);
        GraphAssetDescriptor graph = directory || !AssetTransferPolicy.isGraphPath(path.toString())
                ? null
                : DynamicGraphManager.getGraph(graphId);
        String graphTypeId = graph != null ? graph.type().id()
                : directory || !AssetTransferPolicy.isGraphPath(path.toString())
                ? "" : readGraphTypeId(path);
        return new RemoteGraphEntry(
                graphId,
                path.getFileName().toString(),
                directory,
                size,
                lastModified,
                graphTypeId
        );
    }

    private static String readGraphTypeId(Path path) {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return "";
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("graph_kind") && root.get("graph_kind").isJsonPrimitive()) {
                String explicitId = GraphType.normalizeId(root.get("graph_kind").getAsString());
                if (GraphTypeRegistry.INSTANCE.get(explicitId) != null) return explicitId;
            }
            if (root.has("tags") && root.get("tags").isJsonArray()) {
                for (JsonElement tag : root.getAsJsonArray("tags")) {
                    if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) continue;
                    GraphType legacyType = GraphTypeRegistry.INSTANCE.get(tag.getAsString());
                    if (legacyType != null) return legacyType.id();
                }
            }
            return GraphTypeRegistry.BLUEPRINT.id();
        } catch (Exception ignored) {
            return "";
        }
    }

}
