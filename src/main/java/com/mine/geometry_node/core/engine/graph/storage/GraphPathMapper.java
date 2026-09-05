package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;

import java.nio.file.Path;

/**
 * 负责处理 蓝图ID (A/B/C.json) 与 物理路径 (A/B/C.json) 之间的映射
 */
public class GraphPathMapper {
    public static final String JSON_EXTENSION = ".json";

    /**
     * 将 蓝图ID 转化为 相对物理路径
     */
    public static Path idToRelativePath(String graphId) {
        String pathStr = normalizeRelativePath(graphId, true);
        return Path.of(pathStr);
    }

    public static Path resolveGraphPath(Path rootDir, String graphId) {
        return ServerAssetPaths.resolveUnderRoot(rootDir, normalizeRelativePath(graphId, true), false);
    }

    public static String normalizeId(String id) {
        if (id == null) return null;
        return normalizeRelativePath(id, true);
    }

    public static String normalizeRelativePath(String path, boolean ensureJsonExtension) {
        String pathStr = ServerAssetPaths.normalizeRelativePath(path, !ensureJsonExtension);
        if (pathStr.isEmpty()) return "";

        if (ensureJsonExtension) {
            if (hasJsonExtensionIgnoreCase(pathStr)) {
                pathStr = pathStr.substring(0, pathStr.length() - JSON_EXTENSION.length()) + JSON_EXTENSION;
            } else {
                pathStr += JSON_EXTENSION;
            }
        }
        return ServerAssetPaths.normalizeRelativePath(pathStr, false);
    }

    /** Returns whether an existing repository path uses the canonical graph extension. */
    public static boolean isGraphJsonPath(String path) {
        return path != null && path.endsWith(JSON_EXTENSION);
    }

    private static boolean hasJsonExtensionIgnoreCase(String path) {
        int offset = path.length() - JSON_EXTENSION.length();
        return offset >= 0 && path.regionMatches(true, offset, JSON_EXTENSION, 0, JSON_EXTENSION.length());
    }

    /**
     * 将 物理路径 解析回 蓝图ID
     */
    public static String pathToId(Path rootDir, Path file) {
        // 直接返回原汁原味的相对路径，保留 .json 后缀
        return ServerAssetPaths.pathToId(rootDir, file);
    }
}
