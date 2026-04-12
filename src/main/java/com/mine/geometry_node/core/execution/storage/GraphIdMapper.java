package com.mine.geometry_node.core.execution.storage;

import java.nio.file.Path;

/**
 * 负责处理 蓝图ID (A/B/C.json) 与 物理路径 (A/B/C.json) 之间的映射
 */
public class GraphIdMapper {

    /**
     * 将 蓝图ID 转化为 相对物理路径
     */
    public static Path idToRelativePath(String graphId) {
        String pathStr = graphId.replace('\\', '/');

        // 容错：如果玩家在指令里偷懒没有敲 .json，我们自动帮他补上
        if (!pathStr.endsWith(".json")) {
            pathStr += ".json";
        }

        return Path.of(pathStr);
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