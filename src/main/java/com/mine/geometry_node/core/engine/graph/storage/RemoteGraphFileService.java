package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RemoteGraphFileService {
    private RemoteGraphFileService() {
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
            validateGraphFilePath(resolved);
            if (Files.exists(resolved)) {
                conflicts.add(new RemoteGraphConflict("", GraphPathMapper.pathToId(root, resolved), Files.isDirectory(resolved)));
            }
        }
        return conflicts;
    }

    public static void saveUpload(MinecraftServer server, RemoteGraphUploadFile file, boolean overwrite) throws Exception {
        Path target = resolveFile(server, file.targetPath());
        validateGraphFilePath(target);
        if (Files.exists(target) && Files.isDirectory(target)) {
            throw new IOException("Remote target is a directory: " + file.targetPath());
        }
        if (Files.exists(target) && !overwrite) {
            throw new IOException("Remote graph already exists: " + file.targetPath());
        }
        DynamicGraphManager.saveAndHotReload(server, GraphPathMapper.pathToId(root(server), target), file.jsonContent());
    }

    public static String readGraph(MinecraftServer server, String graphPath) throws IOException {
        Path file = resolveFile(server, graphPath);
        validateGraphFilePath(file);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IOException("Remote graph file does not exist: " + graphPath);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
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
                    walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p) && isGraphFile(p))
                            .map(p -> p.toAbsolutePath().normalize())
                            .filter(seen::add)
                            .forEach(p -> files.add(toEntry(root, p)));
                }
            } else if (Files.isRegularFile(path) && !Files.isSymbolicLink(path) && isGraphFile(path)) {
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
            copied += copyRecursively(source, target);
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
            moved += moveRecursively(source, target);
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

    private static void validateGraphFilePath(Path path) {
        if (!isGraphFile(path)) {
            throw new IllegalArgumentException("Remote graph files must be .json: " + path.getFileName());
        }
    }

    private static boolean isGraphFile(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json");
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
        if (!directory) {
            try {
                size = Files.size(path);
            } catch (IOException ignored) {
                size = 0L;
            }
        }
        String graphId = GraphPathMapper.pathToId(root, path);
        RuntimeGraphIndex graphIndex = directory ? null : DynamicGraphManager.getIndex(graphId);
        return new RemoteGraphEntry(
                graphId,
                path.getFileName().toString(),
                directory,
                size,
                graphIndex != null ? graphIndex.getGraphTypeId() : ""
        );
    }

}
