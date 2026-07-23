package com.mine.geometry_node.core.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ServerAssetPaths {
    private ServerAssetPaths() {
    }

    public static String normalizeRelativePath(String path, boolean allowEmpty) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }

        String pathStr = path.replace('\\', '/').trim();
        if (pathStr.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("path must not contain null characters");
        }
        if (pathStr.isEmpty()) {
            if (allowEmpty) {
                return "";
            }
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
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("asset path escapes root: " + relativePath);
        }
        return resolved;
    }

    public static String pathToId(Path rootDir, Path file) {
        return rootDir.relativize(file).toString().replace('\\', '/');
    }
}
