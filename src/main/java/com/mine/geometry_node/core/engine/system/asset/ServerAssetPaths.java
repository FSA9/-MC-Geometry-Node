package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (path == null) throw new IllegalArgumentException("path must not be null");

        String pathStr = path.replace('\\', '/').trim();
        if (pathStr.indexOf('\0') >= 0) throw new IllegalArgumentException("path must not contain null characters");
        if (pathStr.isEmpty()) {
            if (allowEmpty) return "";
            throw new IllegalArgumentException("path must not be empty");
        }
        if (pathStr.startsWith("/") || pathStr.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("absolute paths are not allowed: " + path);
        }
        if (pathStr.matches("^[A-Za-z][A-Za-z0-9+.-]*:/.*")) {
            throw new IllegalArgumentException("path prefixes are not allowed: " + path);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : pathStr.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("invalid path segment: " + path);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    public static Path resolveUnderRoot(Path rootDir, String relativePath, boolean allowEmpty) {
        Path root = rootDir.toAbsolutePath().normalize();
        String normalized = normalizeRelativePath(relativePath, allowEmpty);
        Path resolved = normalized.isEmpty() ? root : root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("asset path escapes root: " + relativePath);
        return resolved;
    }

    public static String pathToId(Path rootDir, Path file) {
        Path root = rootDir.toAbsolutePath().normalize();
        Path resolved = file.toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("asset path escapes root: " + file);
        return root.relativize(resolved).toString().replace('\\', '/');
    }
}
