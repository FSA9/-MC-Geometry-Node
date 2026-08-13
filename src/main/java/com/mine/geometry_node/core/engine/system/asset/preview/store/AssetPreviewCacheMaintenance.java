package com.mine.geometry_node.core.engine.system.asset.preview.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AssetPreviewCacheMaintenance {
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

    public static void enforceLimit(Path root, long maximumBytes, Path protectedArtifact) throws IOException {
        if (maximumBytes <= 0L || !Files.isDirectory(root)) return;
        long total = size(root);
        if (total <= maximumBytes) return;
        Path protectedPath = protectedArtifact != null ? protectedArtifact.toAbsolutePath().normalize() : null;
        List<Path> candidates = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            candidates.addAll(paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(AssetPreviewCacheMaintenance::isPrimaryArtifact)
                    .filter(path -> protectedPath == null || !path.toAbsolutePath().normalize().equals(protectedPath))
                    .toList());
        }
        candidates.sort(Comparator.comparingLong(AssetPreviewCacheMaintenance::modifiedTime));
        for (Path artifact : candidates) {
            if (total <= maximumBytes) break;
            long removed = Files.size(artifact);
            String fileName = artifact.getFileName().toString();
            int dot = fileName.indexOf('.');
            String key = dot > 0 ? fileName.substring(0, dot) : fileName;
            Path cacheRoot = cacheRootForArtifact(artifact);
            if (cacheRoot == null || key.length() < 2) continue;
            boolean model = isModelArtifact(artifact);
            Path metadata = cacheRoot.resolve("metadata").toAbsolutePath().normalize();
            Path metadataFile = model ? artifact.resolveSibling(fileName + ".revision")
                    : metadata.resolve(key.substring(0, 2)).resolve(key + ".bin").normalize();
            Files.deleteIfExists(artifact);
            if ((model || metadataFile.startsWith(metadata)) && Files.isRegularFile(metadataFile)) {
                removed = Math.addExact(removed, Files.size(metadataFile));
                Files.deleteIfExists(metadataFile);
            }
            total = Math.max(0L, total - removed);
        }
    }

    private static Path cacheRootForArtifact(Path artifact) {
        Path current = artifact.toAbsolutePath().normalize().getParent();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && ("artifacts".equals(name.toString()) || "model-assets".equals(name.toString()))) {
                return current.getParent();
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isModelArtifact(Path path) {
        if (!path.getFileName().toString().endsWith(".glb")) return false;
        Path current = path.toAbsolutePath().normalize().getParent();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "model-assets".equals(name.toString())) return true;
            current = current.getParent();
        }
        return false;
    }

    private static boolean isPrimaryArtifact(Path path) {
        if (isModelArtifact(path)) return true;
        Path current = path.toAbsolutePath().normalize().getParent();
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "artifacts".equals(name.toString())) return true;
            if (name != null && ("metadata".equals(name.toString()) || "model-assets".equals(name.toString())
                    || "model-staging".equals(name.toString()))) return false;
            current = current.getParent();
        }
        return false;
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }
}
