package com.mine.geometry_node.core.execution.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [动态图管理器]
 * 负责管理玩家在 UI 编辑器中实时发布到服务器的图纸。
 */
public class DynamicGraphManager {
    // 定义当前世界存档下的专属子文件夹名称
    public static final LevelResource GRAPH_DIR = new LevelResource("geometry_nodes");

    // 核心内存缓存
    private static final ConcurrentHashMap<String, RuntimeGraphIndex> dynamicIndexCache = new ConcurrentHashMap<>();

    /**
     * [API 1: 获取图纸]
     */
    public static RuntimeGraphIndex getIndex(String graphId) {
        return dynamicIndexCache.get(graphId);
    }

    /**
     * [API 2: 获取所有动态图 ID]
     */
    public static Set<String> getAllDynamicGraphIds() {
        return Collections.unmodifiableSet(dynamicIndexCache.keySet());
    }

    public static void saveAndHotReload(MinecraftServer server, String graphId, String jsonContent) throws Exception {
        if (server == null) return;

        Path folder = server.getWorldPath(GRAPH_DIR);
        Path relativePath = GraphIdMapper.idToRelativePath(graphId);
        File file = folder.resolve(relativePath).toFile();

        // 创建目录
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new Exception("[DynamicGraphManager] Fail to create server content!");
        }

        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(jsonContent);
        }

        // 热更新
        try (java.io.StringReader reader = new java.io.StringReader(jsonContent)) {
            com.mine.geometry_node.core.execution.RuntimeGraphIndex index =
                    com.mine.geometry_node.core.execution.RuntimeGraphIndex.build(reader);

            String normalizedId = GraphIdMapper.pathToId(folder, file.toPath());
            RuntimeGraphIndex oldIndex = dynamicIndexCache.put(normalizedId, index);
            GraphEngine.refreshGraphSubscriptions(server, normalizedId, oldIndex, index);
        }
    }

    public static void loadAllFromDisk(MinecraftServer server) {
        dynamicIndexCache.clear();
        if (server == null) return;

        try {
            Path folder = server.getWorldPath(GRAPH_DIR);
            if (!java.nio.file.Files.exists(folder) || !java.nio.file.Files.isDirectory(folder)) {
                return;
            }

            // 递归遍历服务端文件
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(folder)) {
                walk.filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String graphId = GraphIdMapper.pathToId(folder, file);
                                try (java.io.FileReader reader = new java.io.FileReader(file.toFile())) {
                                    com.mine.geometry_node.core.execution.RuntimeGraphIndex index =
                                            com.mine.geometry_node.core.execution.RuntimeGraphIndex.build(reader);
                                    dynamicIndexCache.put(graphId, index);
                                }
                            } catch (Exception e) {
                                com.mine.geometry_node.GeometryNode.LOGGER.error("[DynamicGraphManager] Fail to load graph: {}", file, e);
                            }
                        });
            }
            com.mine.geometry_node.GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs。", dynamicIndexCache.size());
        } catch (Exception e) {
            com.mine.geometry_node.GeometryNode.LOGGER.error("[DynamicGraphManager] Load content failed! ", e);
        }
    }
}
