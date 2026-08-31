package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;

import java.nio.file.Path;

/**
 * 负责处理 蓝图ID (A/B/C.json) 与 物理路径 (A/B/C.json) 之间的映射
 */
public class GraphPathMapper {

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

        // 容错：如果玩家在指令里偷懒没有敲 .json，我们自动帮他补上
        if (ensureJsonExtension && !pathStr.endsWith(".json")) {
            pathStr += ".json";
        }
        return ServerAssetPaths.normalizeRelativePath(pathStr, false);
    }

    /**
     * 将 物理路径 解析回 蓝图ID
     */
    public static String pathToId(Path rootDir, Path file) {
        // 直接返回原汁原味的相对路径，保留 .json 后缀
        return ServerAssetPaths.pathToId(rootDir, file);
    }

}
