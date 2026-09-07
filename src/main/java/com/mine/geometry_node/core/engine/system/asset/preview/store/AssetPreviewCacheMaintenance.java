package com.mine.geometry_node.core.engine.system.asset.preview.store;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewCachePaths;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewMetadataCodec;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Maintains complete metadata/artifact cache entries rather than unrelated files. */
public final class AssetPreviewCacheMaintenance {
    private static final long ORPHAN_GRACE_MILLIS = TimeUnit.HOURS.toMillis(1L);

    private AssetPreviewCacheMaintenance() {
    }

    public static long size(Path root) throws IOException {
        if (!Files.exists(root)) return 0L;
        long total = 0L;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> !Files.isSymbolicLink(file)).toList()) {
                total = Math.addExact(total, Files.size(path));
            }
        }
        return total;
    }

    public static void touch(Path artifact) {
        try {
            Files.setLastModifiedTime(artifact, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    public static void enforceLimit(Path root, long maximumBytes, Path protectedArtifact, int metadataMagic)
            throws IOException {
        if (!Files.isDirectory(root)) return;

        long staleBefore = System.currentTimeMillis() - ORPHAN_GRACE_MILLIS;
        EntryId protectedEntry = entryIdForArtifact(protectedArtifact);
        List<CompleteEntry> completeEntries = new ArrayList<>();
        for (Path cacheRoot : findCacheRoots(root)) {
            inspectCacheRoot(cacheRoot, metadataMagic, staleBefore, completeEntries);
        }
        removeEmptyCacheDirectories(root);

        if (maximumBytes <= 0L) return;
        long total = 0L;
        for (CompleteEntry entry : completeEntries) {
            total = Math.addExact(total, entry.encodedBytes());
        }
        if (total <= maximumBytes) return;

        completeEntries.sort(Comparator.comparingLong(CompleteEntry::lastAccess));
        for (CompleteEntry entry : completeEntries) {
            if (total <= maximumBytes) break;
            if (entry.id().equals(protectedEntry)) continue;
            total = Math.max(0L, total - deleteEntry(entry));
        }
        removeEmptyCacheDirectories(root);
    }

    private static void inspectCacheRoot(Path cacheRoot, int metadataMagic, long staleBefore,
                                         List<CompleteEntry> completeEntries) throws IOException {
        Map<String, Path> metadataByKey = new HashMap<>();
        Map<String, List<Path>> artifactsByKey = new HashMap<>();
        List<Path> unrecognized = new ArrayList<>();

        collectFiles(cacheRoot.resolve("metadata"), true, metadataByKey, artifactsByKey, unrecognized);
        collectFiles(cacheRoot.resolve("artifacts"), false, metadataByKey, artifactsByKey, unrecognized);
        deleteStale(unrecognized, staleBefore);

        Set<String> keys = new HashSet<>(metadataByKey.keySet());
        keys.addAll(artifactsByKey.keySet());
        for (String key : keys) {
            Path metadata = metadataByKey.get(key);
            List<Path> artifacts = artifactsByKey.getOrDefault(key, List.of());
            AssetPreviewDescriptor descriptor = readMetadata(metadata, metadataMagic);
            Path expectedArtifact = descriptor != null
                    ? AssetPreviewCachePaths.resolveArtifact(cacheRoot.resolve("artifacts"), key, descriptor.format())
                    : null;
            boolean complete = descriptor != null
                    && descriptor.revision().cacheKey().equals(key)
                    && expectedArtifact != null
                    && artifacts.contains(expectedArtifact)
                    && Files.size(expectedArtifact) == descriptor.encodedBytes();

            if (!complete) {
                List<Path> invalidEntry = new ArrayList<>(artifacts);
                if (metadata != null) invalidEntry.add(metadata);
                deleteStaleGroup(invalidEntry, staleBefore);
                continue;
            }

            for (Path artifact : artifacts) {
                if (!artifact.equals(expectedArtifact) && modifiedTime(artifact) <= staleBefore) {
                    Files.deleteIfExists(artifact);
                }
            }
            completeEntries.add(new CompleteEntry(new EntryId(cacheRoot, key), metadata, expectedArtifact,
                    modifiedTime(expectedArtifact), Math.addExact(Files.size(metadata), Files.size(expectedArtifact))));
        }
    }

    private static void collectFiles(Path directory, boolean metadataDirectory,
                                     Map<String, Path> metadataByKey, Map<String, List<Path>> artifactsByKey,
                                     List<Path> unrecognized) throws IOException {
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path)).toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    unrecognized.add(file);
                    continue;
                }
                String key = metadataDirectory ? metadataKey(name) : artifactKey(name);
                if (key == null) {
                    unrecognized.add(file);
                } else if (metadataDirectory) {
                    Path previous = metadataByKey.putIfAbsent(key, file);
                    if (previous != null) unrecognized.add(file);
                } else {
                    artifactsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file);
                }
            }
        }
    }

    private static String metadataKey(String name) {
        if (!name.endsWith(".bin")) return null;
        return validKey(name.substring(0, name.length() - 4));
    }

    private static String artifactKey(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || AssetPreviewFormat.fromId(name.substring(dot + 1)) == null) return null;
        return validKey(name.substring(0, dot));
    }

    private static String validKey(String value) {
        try {
            return AssetPreviewCachePaths.validateCacheKey(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static AssetPreviewDescriptor readMetadata(Path metadata, int metadataMagic) {
        if (metadata == null) return null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(metadata)))) {
            return AssetPreviewMetadataCodec.read(input, metadataMagic);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Set<Path> findCacheRoots(Path root) throws IOException {
        Set<Path> cacheRoots = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory).filter(path -> !Files.isSymbolicLink(path)).toList()) {
                Path name = directory.getFileName();
                if (name != null && ("artifacts".equals(name.toString()) || "metadata".equals(name.toString()))) {
                    cacheRoots.add(directory.toAbsolutePath().normalize().getParent());
                }
            }
        }
        return cacheRoots;
    }

    private static void deleteStale(List<Path> files, long staleBefore) throws IOException {
        for (Path file : files) {
            if (modifiedTime(file) <= staleBefore) Files.deleteIfExists(file);
        }
    }

    private static void deleteStaleGroup(List<Path> files, long staleBefore) throws IOException {
        if (files.isEmpty() || files.stream().anyMatch(file -> modifiedTime(file) > staleBefore)) return;
        for (Path file : files) Files.deleteIfExists(file);
    }

    private static long deleteEntry(CompleteEntry entry) {
        long removed = 0L;
        for (Path file : List.of(entry.artifact(), entry.metadata())) {
            try {
                if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) continue;
                long size = Files.size(file);
                if (Files.deleteIfExists(file)) removed = Math.addExact(removed, size);
            } catch (IOException | ArithmeticException ignored) {
            }
        }
        return removed;
    }

    private static void removeEmptyCacheDirectories(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder()).toList()) {
                Path parent = directory.getParent();
                if (parent == null || !("artifacts".equals(parent.getFileName().toString())
                        || "metadata".equals(parent.getFileName().toString()))) continue;
                try (var children = Files.list(directory)) {
                    if (children.findAny().isEmpty()) Files.deleteIfExists(directory);
                }
            }
        }
    }

    private static EntryId entryIdForArtifact(Path artifact) {
        if (artifact == null) return null;
        Path normalized = artifact.toAbsolutePath().normalize();
        Path cacheRoot = cacheRootForArtifact(normalized);
        String key = artifactKey(normalized.getFileName().toString());
        return cacheRoot != null && key != null ? new EntryId(cacheRoot, key) : null;
    }

    private static Path cacheRootForArtifact(Path artifact) {
        Path current = artifact.getParent();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "artifacts".equals(name.toString())) return current.getParent();
            current = current.getParent();
        }
        return null;
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private record EntryId(Path cacheRoot, String key) {
        private EntryId {
            cacheRoot = cacheRoot.toAbsolutePath().normalize();
        }
    }

    private record CompleteEntry(EntryId id, Path metadata, Path artifact, long lastAccess, long encodedBytes) {
    }

}
