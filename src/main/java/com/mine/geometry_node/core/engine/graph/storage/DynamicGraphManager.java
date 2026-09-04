package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [动态图管理器]
 * 负责管理玩家在 UI 编辑器中实时发布到服务器的图纸。
 */
public class DynamicGraphManager {
    // 核心内存缓存
    private static final ConcurrentHashMap<String, GraphAssetDescriptor> dynamicGraphCache = new ConcurrentHashMap<>();
    private static final Set<String> invalidDynamicGraphIds = ConcurrentHashMap.newKeySet();
    @Nullable
    public static GraphAssetDescriptor getGraph(String graphId) {
        return dynamicGraphCache.get(graphId);
    }

    @Nullable
    public static CompiledGraph getArtifact(String graphId, GraphKind runtimeKind) {
        GraphAssetDescriptor descriptor = dynamicGraphCache.get(graphId);
        return descriptor != null && descriptor.runtimeKind() == runtimeKind ? descriptor.artifact() : null;
    }

    /**
     * [API 2: 获取所有动态图 ID]
     */
    public static Set<String> getAllDynamicGraphIds() {
        return Collections.unmodifiableSet(dynamicGraphCache.keySet());
    }

    public static Set<String> getDynamicGraphIds(GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        Set<String> result = ConcurrentHashMap.newKeySet();
        dynamicGraphCache.forEach((graphId, descriptor) -> {
            if (descriptor.runtimeKind() == runtimeKind) result.add(graphId);
        });
        return Collections.unmodifiableSet(result);
    }

    public static void saveAndHotReload(MinecraftServer server, String graphId, String jsonContent) throws Exception {
        if (server == null) return;
        if (!AssetTypeCatalog.GRAPH_TYPE_ID.equals(
                AssetTypeCatalog.inspectGraphJson(jsonContent).typeId())) {
            throw new IllegalArgumentException("Uploaded content is not a registered graph document");
        }

        Path folder = ServerAssetPaths.root(server);
        Path filePath = GraphPathMapper.resolveGraphPath(folder, graphId);
        File file = filePath.toFile();
        String normalizedId = GraphPathMapper.pathToId(folder, filePath);

        // 创建目录
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new Exception("[DynamicGraphManager] Fail to create server content!");
        }

        Path tempPath = Files.createTempFile(parentDir.toPath(), ".graph_upload_", ".tmp");
        Files.writeString(tempPath, jsonContent, StandardCharsets.UTF_8);
        Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);

        try {
            JsonObject document = JsonParser.parseString(jsonContent).getAsJsonObject();
            CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(normalizedId, document);
            GraphAssetDescriptor descriptor = new GraphAssetDescriptor(normalizedId,
                    GraphTypeRegistry.INSTANCE.require(artifact.graphTypeId()), artifact,
                    GraphAssetFingerprint.of(document));
            dynamicGraphCache.put(normalizedId, descriptor);
            invalidDynamicGraphIds.remove(normalizedId);
            GraphAssetLifecycleIndex.INSTANCE.replaceDynamicGraphs(server, dynamicGraphCache,
                    invalidDynamicGraphIds);
        } catch (Exception exception) {
            dynamicGraphCache.remove(normalizedId);
            invalidDynamicGraphIds.add(normalizedId);
            GraphAssetLifecycleIndex.INSTANCE.replaceDynamicGraphs(server, dynamicGraphCache,
                    invalidDynamicGraphIds);
            GeometryNode.LOGGER.warn(
                    "[DynamicGraphManager] Graph saved without runtime artifact: {} ({})",
                    normalizedId, exception.getMessage());
            GeometryNode.LOGGER.debug(
                    "[DynamicGraphManager] Compilation failure for uploaded graph: " + normalizedId,
                    exception);
        }
    }

    public static void prepareForServerStart(MinecraftServer server) {
        loadAllFromDisk(server);
    }

    public static void loadAllFromDisk(MinecraftServer server) {
        dynamicGraphCache.clear();
        invalidDynamicGraphIds.clear();
        if (server == null) return;

        try {
            Path folder = ServerAssetPaths.root(server);
            if (!java.nio.file.Files.exists(folder) || !java.nio.file.Files.isDirectory(folder)) {
                publishDynamicSnapshot(server);
                return;
            }

            // 递归遍历服务端文件
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(folder)) {
                walk.filter(p -> java.nio.file.Files.isRegularFile(p) && !java.nio.file.Files.isSymbolicLink(p))
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> AssetTypeCatalog.GRAPH_TYPE_ID.equals(
                                AssetTypeCatalog.inspect(p).typeId()))
                        .forEach(file -> {
                            try {
                                String graphId = GraphPathMapper.pathToId(folder, file);
                                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                                    JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
                                    CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(graphId, document);
                                    dynamicGraphCache.put(graphId, new GraphAssetDescriptor(graphId,
                                            GraphTypeRegistry.INSTANCE.require(artifact.graphTypeId()), artifact,
                                            GraphAssetFingerprint.of(document)));
                                }
                            } catch (Exception e) {
                                try {
                                    invalidDynamicGraphIds.add(GraphPathMapper.pathToId(folder, file));
                                } catch (Exception ignored) {
                                }
                                GeometryNode.LOGGER.error("[DynamicGraphManager] Fail to load graph: {}", file, e);
                            }
                        });
            }
            publishDynamicSnapshot(server);
            GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs。", dynamicGraphCache.size());
        } catch (Exception e) {
            publishDynamicSnapshot(server);
            GeometryNode.LOGGER.error("[DynamicGraphManager] Load content failed! ", e);
        }
    }

    private static void publishDynamicSnapshot(MinecraftServer server) {
        GraphAssetLifecycleIndex.INSTANCE.replaceDynamicGraphs(server, dynamicGraphCache,
                invalidDynamicGraphIds);
    }
}
