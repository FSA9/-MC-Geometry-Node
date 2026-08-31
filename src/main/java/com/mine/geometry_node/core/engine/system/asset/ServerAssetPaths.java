package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Canonical path policy and world-owned root for server assets. */
public final class ServerAssetPaths {
    public static final LevelResource ROOT = new LevelResource("geometry_nodes");

    private ServerAssetPaths() {
    }

    public static Path root(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("server must not be null");
        return server.getWorldPath(ROOT).toAbsolutePath().normalize();
    }

    public static String normalizeRelativePath(String path, boolean allowEmpty) {
        return AssetRelativePath.normalize(path, allowEmpty);
    }

    public static Path resolveUnderRoot(Path rootDir, String relativePath, boolean allowEmpty) {
        Path root = rootDir.toAbsolutePath().normalize();
        String normalized = normalizeRelativePath(relativePath, allowEmpty);
        Path resolved = normalized.isEmpty() ? root : root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("asset path escapes root: " + relativePath);
        rejectSymbolicLinkSegments(root, resolved, relativePath);
        return resolved;
    }

    private static void rejectSymbolicLinkSegments(Path root, Path resolved, String originalPath) {
        Path current = root;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException("asset root must not be a symbolic link");
        }
        for (Path segment : root.relativize(resolved)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("symbolic links are not allowed in asset paths: " + originalPath);
            }
        }
    }

    public static String pathToId(Path rootDir, Path file) {
        Path root = rootDir.toAbsolutePath().normalize();
        Path resolved = file.toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("asset path escapes root: " + file);
        return root.relativize(resolved).toString().replace('\\', '/');
    }
}
