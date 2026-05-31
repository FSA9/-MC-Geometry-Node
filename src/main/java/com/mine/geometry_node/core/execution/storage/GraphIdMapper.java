package com.mine.geometry_node.core.execution.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责处理 蓝图ID (A/B/C.json) 与 物理路径 (A/B/C.json) 之间的映射
 */
public class GraphIdMapper {

    /**
     * 将 蓝图ID 转化为 相对物理路径
     */
    public static Path idToRelativePath(String graphId) {
        String pathStr = normalizeRelativePath(graphId, true);
        return Path.of(pathStr);
    }

    public static Path resolveGraphPath(Path rootDir, String graphId) {
        Path root = rootDir.toAbsolutePath().normalize();
        Path relativePath = idToRelativePath(graphId);
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Graph path escapes graph root: " + graphId);
        }
        return resolved;
    }

    public static String normalizeId(String id) {
        if (id == null) return null;
        return normalizeRelativePath(id, true);
    }

    public static String normalizeRelativePath(String path, boolean ensureJsonExtension) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }

        String pathStr = path.replace('\\', '/').trim();
        if (pathStr.isEmpty()) {
            if (ensureJsonExtension) {
                throw new IllegalArgumentException("graph id must not be empty");
            }
            return "";
        }

        if (pathStr.startsWith("/") || pathStr.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("absolute paths are not allowed: " + path);
        }

        // 容错：如果玩家在指令里偷懒没有敲 .json，我们自动帮他补上
        if (ensureJsonExtension && !pathStr.endsWith(".json")) {
            pathStr += ".json";
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

    /**
     * 将 物理路径 解析回 蓝图ID
     */
    public static String pathToId(Path rootDir, Path file) {
        Path relativePath = rootDir.relativize(file);

        // 直接返回原汁原味的相对路径，保留 .json 后缀
        return relativePath.toString().replace('\\', '/');
    }

}
