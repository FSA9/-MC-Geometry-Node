package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.compile.GraphDependencyValidator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [动态图管理器]
 * 负责管理玩家在 UI 编辑器中实时发布到服务器的图纸。
 */
public class DynamicGraphManager {
    // 定义当前世界存档下的专属子文件夹名称
    public static final LevelResource GRAPH_DIR = new LevelResource("geometry_nodes");

    // 核心内存缓存
    private static final ConcurrentHashMap<String, GraphAssetDescriptor> dynamicGraphCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GraphKind, CopyOnWriteArrayList<ReloadListener>> reloadListeners =
            new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface ReloadListener {
        void onDynamicGraphReload(MinecraftServer server, String graphId,
                                  @Nullable CompiledGraph oldArtifact,
                                  @Nullable CompiledGraph newArtifact);
    }

    public static void addReloadListener(GraphKind runtimeKind, ReloadListener listener) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN || listener == null) return;
        reloadListeners.computeIfAbsent(runtimeKind, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(listener);
    }

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

        Path folder = server.getWorldPath(GRAPH_DIR).toAbsolutePath().normalize();
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
            CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(normalizedId, jsonContent);
            GraphAssetDescriptor descriptor = new GraphAssetDescriptor(normalizedId,
                    GraphTypeRegistry.INSTANCE.require(artifact.graphTypeId()), artifact);
            Map<String, CompiledGraph> dependencyView = new HashMap<>();
            dynamicGraphCache.forEach((id, existing) -> dependencyView.put(id, existing.artifact()));
            dependencyView.put(normalizedId, artifact);
            List<String> dependencyCycle = GraphDependencyValidator.findCycle(dependencyView);
            if (!dependencyCycle.isEmpty()) {
                throw new IllegalStateException("Recursive graph dependencies: "
                        + String.join(" -> ", dependencyCycle));
            }

            GraphAssetDescriptor oldDescriptor = dynamicGraphCache.put(normalizedId, descriptor);
            notifyReload(server, normalizedId, oldDescriptor, descriptor);
        } catch (Exception exception) {
            GraphAssetDescriptor oldDescriptor = dynamicGraphCache.remove(normalizedId);
            notifyReload(server, normalizedId, oldDescriptor, null);
            GeometryNode.LOGGER.info("[DynamicGraphManager] Graph saved without runtime artifact: {}", normalizedId);
        }
    }

    public static void loadAllFromDisk(MinecraftServer server) {
        dynamicGraphCache.clear();
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
                                    CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(graphId, reader);
                                    dynamicGraphCache.put(graphId, new GraphAssetDescriptor(graphId,
                                            GraphTypeRegistry.INSTANCE.require(artifact.graphTypeId()), artifact));
                                }
                            } catch (Exception e) {
                                GeometryNode.LOGGER.error("[DynamicGraphManager] Fail to load graph: {}", file, e);
                            }
                        });
            }
            removeInvalidDependencies();
            GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs。", dynamicGraphCache.size());
        } catch (Exception e) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] Load content failed! ", e);
        }
    }

    private static void removeInvalidDependencies() {
        Map<String, CompiledGraph> compiledGraphs = new HashMap<>();
        dynamicGraphCache.forEach((graphId, descriptor) -> compiledGraphs.put(graphId, descriptor.artifact()));
        List<String> dependencyCycle = GraphDependencyValidator.findCycle(compiledGraphs);
        Set<String> invalidGraphs = GraphDependencyValidator.findInvalidGraphs(compiledGraphs);
        if (invalidGraphs.isEmpty()) return;
        invalidGraphs.forEach(dynamicGraphCache::remove);
        GeometryNode.LOGGER.error("[DynamicGraphManager] Rejected recursive graph dependencies: {}; rejected={}",
                String.join(" -> ", dependencyCycle), invalidGraphs);
    }

    private static void notifyReload(MinecraftServer server, String graphId,
                                     @Nullable GraphAssetDescriptor oldDescriptor,
                                     @Nullable GraphAssetDescriptor newDescriptor) {
        if (oldDescriptor != null) {
            notifyKind(oldDescriptor.runtimeKind(), server, graphId, oldDescriptor.artifact(),
                    newDescriptor != null && newDescriptor.runtimeKind() == oldDescriptor.runtimeKind()
                            ? newDescriptor.artifact() : null);
        }
        if (newDescriptor != null && (oldDescriptor == null
                || oldDescriptor.runtimeKind() != newDescriptor.runtimeKind())) {
            notifyKind(newDescriptor.runtimeKind(), server, graphId, null, newDescriptor.artifact());
        }
    }

    private static void notifyKind(GraphKind kind, MinecraftServer server, String graphId,
                                   @Nullable CompiledGraph oldArtifact,
                                   @Nullable CompiledGraph newArtifact) {
        CopyOnWriteArrayList<ReloadListener> listeners = reloadListeners.get(kind);
        if (listeners == null) return;
        for (ReloadListener listener : listeners) {
            listener.onDynamicGraphReload(server, graphId, oldArtifact, newArtifact);
        }
    }
}
