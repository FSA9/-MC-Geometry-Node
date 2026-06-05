package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    @Nullable
    private static ReloadListener reloadListener;

    @FunctionalInterface
    public interface ReloadListener {
        void onDynamicGraphReload(MinecraftServer server, String graphId,
                                  @Nullable RuntimeGraphIndex oldIndex,
                                  RuntimeGraphIndex newIndex);
    }

    public static void setReloadListener(@Nullable ReloadListener listener) {
        reloadListener = listener;
    }

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

        Path folder = server.getWorldPath(GRAPH_DIR).toAbsolutePath().normalize();
        Path filePath = GraphPathMapper.resolveGraphPath(folder, graphId);
        File file = filePath.toFile();

        RuntimeGraphIndex index;
        try (StringReader reader = new StringReader(jsonContent)) {
            index = BlueprintCompiler.compile(reader);
        }

        // 创建目录
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new Exception("[DynamicGraphManager] Fail to create server content!");
        }

        Path tempPath = Files.createTempFile(parentDir.toPath(), ".graph_upload_", ".tmp");
        Files.writeString(tempPath, jsonContent, StandardCharsets.UTF_8);
        Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);

        // 热更新
        String normalizedId = GraphPathMapper.pathToId(folder, file.toPath().toAbsolutePath().normalize());
        RuntimeGraphIndex oldIndex = dynamicIndexCache.put(normalizedId, index);
        ReloadListener listener = reloadListener;
        if (listener != null) {
            listener.onDynamicGraphReload(server, normalizedId, oldIndex, index);
        }
    }

    public static void loadAllFromDisk(MinecraftServer server) {
        dynamicIndexCache.clear();
        if (server == null) return;

        try {
            Path folder = server.getWorldPath(GRAPH_DIR).toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(folder) || !java.nio.file.Files.isDirectory(folder)) {
                return;
            }

            // 递归遍历服务端文件
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(folder)) {
                walk.filter(p -> java.nio.file.Files.isRegularFile(p) && !java.nio.file.Files.isSymbolicLink(p))
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String graphId = GraphPathMapper.pathToId(folder, file);
                                try (java.io.FileReader reader = new java.io.FileReader(file.toFile())) {
                                    RuntimeGraphIndex index = BlueprintCompiler.compile(reader);
                                    dynamicIndexCache.put(graphId, index);
                                }
                            } catch (Exception e) {
                                GeometryNode.LOGGER.error("[DynamicGraphManager] Fail to load graph: {}", file, e);
                            }
                        });
            }
            GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs。", dynamicIndexCache.size());
        } catch (Exception e) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] Load content failed! ", e);
        }
    }
}
