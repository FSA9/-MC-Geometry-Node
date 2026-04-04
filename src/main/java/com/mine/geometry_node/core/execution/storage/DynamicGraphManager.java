package com.mine.geometry_node.core.execution.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [动态图管理器]
 * 负责管理玩家在 UI 编辑器中实时发布到服务器的图纸。
 * 具备 File I/O 落盘能力与 0 毫秒内存热更新能力。
 */
public class DynamicGraphManager {

    // 定义当前世界存档下的专属子文件夹名称
    public static final LevelResource GRAPH_DIR = new LevelResource("geometry_nodes");

    // 核心内存缓存 (使用 ConcurrentHashMap 保证多线程读写安全)
    private static final ConcurrentHashMap<String, RuntimeGraphIndex> dynamicIndexCache = new ConcurrentHashMap<>();

    /**
     * [API 1: 获取图纸] 供 GraphEngine 在运行时极速调用
     */
    public static RuntimeGraphIndex getIndex(String graphId) {
        return dynamicIndexCache.get(graphId);
    }

    /**
     * [API 2: 获取所有动态图 ID] 供 UI 列表或指令补全使用
     */
    public static Set<String> getAllDynamicGraphIds() {
        return Collections.unmodifiableSet(dynamicIndexCache.keySet());
    }

    /**
     * [API 3: 发布并热更新] 当收到客户端的上传网络包时调用
     */
    public static void saveAndHotReload(MinecraftServer server, String graphId, String jsonContent) {
        if (server == null || graphId == null || jsonContent == null) return;

        try {
            // 1. 尝试在内存中编译图纸 (Dry Run 测试)
            // 这一步非常重要！如果 JSON 格式错乱，它会直接抛出异常，阻止烂数据污染硬盘和内存
            RuntimeGraphIndex newIndex;
            try (StringReader reader = new StringReader(jsonContent)) {
                newIndex = RuntimeGraphIndex.build(reader);
            }

            // 2. 编译成功，准备落盘写文件
            Path dirPath = server.getWorldPath(GRAPH_DIR);
            File folder = dirPath.toFile();
            if (!folder.exists() && !folder.mkdirs()) {
                GeometryNode.LOGGER.error("[DynamicGraphManager] 无法创建目录: {}", folder.getAbsolutePath());
                return;
            }

            File file = new File(folder, graphId + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonContent);
            }

            // 3. 落盘成功，瞬间替换内存缓存 (完成热更新)
            dynamicIndexCache.put(graphId, newIndex);
            GeometryNode.LOGGER.info("[DynamicGraphManager] 蓝图已实时发布并热重载: {}", graphId);

        } catch (Exception e) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] 蓝图发布失败 (可能存在语法错误): {}", graphId, e);
        }
    }

    /**
     * [API 4: 启动加载] 在服务器启动时调用，将硬盘里的草稿读回内存
     */
    public static void loadAllFromDisk(MinecraftServer server) {
        dynamicIndexCache.clear();
        if (server == null) return;

        try {
            File folder = server.getWorldPath(GRAPH_DIR).toFile();
            if (!folder.exists() || !folder.isDirectory()) {
                return; // 文件夹不存在，说明是新存档，直接跳过
            }

            File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) return;

            int count = 0;
            for (File file : files) {
                String fileName = file.getName();
                // 去掉 .json 后缀作为 graphId
                String graphId = fileName.substring(0, fileName.length() - 5);

                try (FileReader reader = new FileReader(file)) {
                    RuntimeGraphIndex index = RuntimeGraphIndex.build(reader);
                    dynamicIndexCache.put(graphId, index);
                    count++;
                } catch (Exception e) {
                    GeometryNode.LOGGER.error("[DynamicGraphManager] 无法加载本地蓝图文件: {}", fileName, e);
                }
            }

            GeometryNode.LOGGER.info("[DynamicGraphManager] 成功从本地加载了 {} 个动态蓝图。", count);

        } catch (Exception e) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] 读取本地目录失败", e);
        }
    }
}