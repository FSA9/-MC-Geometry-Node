package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads server-repository graph documents and publishes complete runtime snapshots.
 */
public final class DynamicGraphManager {
    private DynamicGraphManager() {
    }

    public static void prepareForServerStart(MinecraftServer server) {
        loadAllFromDisk(server);
    }

    public static void loadAllFromDisk(MinecraftServer server) {
        if (server == null) return;

        Map<String, GraphAssetDescriptor> loadedGraphs = new LinkedHashMap<>();
        try {
            Path folder = ServerAssetPaths.root(server);
            if (!java.nio.file.Files.exists(folder) || !java.nio.file.Files.isDirectory(folder)) {
                publishDynamicSnapshot(server, loadedGraphs);
                return;
            }

            // 递归遍历服务端文件
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(folder)) {
                walk.filter(p -> java.nio.file.Files.isRegularFile(p) && !java.nio.file.Files.isSymbolicLink(p))
                        .filter(p -> GraphPathMapper.isGraphJsonPath(p.toString()))
                        .forEach(file -> {
                            try {
                                String graphId = GraphPathMapper.pathToId(folder, file);
                                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                                    JsonElement parsed = JsonParser.parseReader(reader);
                                    if (!parsed.isJsonObject()) return;
                                    JsonObject document = parsed.getAsJsonObject();
                                    GraphType type;
                                    try {
                                        type = GraphDocumentType.require(document);
                                    } catch (RuntimeException ignored) {
                                        return;
                                    }
                                    CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(graphId, document);
                                    loadedGraphs.put(graphId, new GraphAssetDescriptor(graphId,
                                            type, artifact,
                                            GraphAssetFingerprint.of(document)));
                                }
                            } catch (Exception e) {
                                GeometryNode.LOGGER.error("[DynamicGraphManager] Fail to load graph: {}", file, e);
                            }
                        });
            }
            publishDynamicSnapshot(server, loadedGraphs);
            GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs。", loadedGraphs.size());
        } catch (Exception e) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] Load content failed! ", e);
        }
    }

    private static void publishDynamicSnapshot(MinecraftServer server,
                                               Map<String, GraphAssetDescriptor> loadedGraphs) {
        GraphAssetLifecycleIndex.INSTANCE.replaceDynamicGraphs(server, loadedGraphs);
    }
}
