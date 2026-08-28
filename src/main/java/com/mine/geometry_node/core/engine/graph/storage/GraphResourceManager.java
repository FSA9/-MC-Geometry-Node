package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * [资源管理器] 集成 Minecraft 数据包系统。
 * 负责监听数据包重载事件，读取 `data/[modid]/graphs/` 路径下的 JSON 文件，
 * 并按图族编译为对应的不可变运行时产物。
 */
public class GraphResourceManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    private static final String FOLDER_NAME = "graphs";
    private static GraphResourceManager INSTANCE;

    // 核心缓存：图 ID (Identifier) -> 运行时索引
    private Map<String, GraphAssetDescriptor> graphCache = Collections.emptyMap();

    public GraphResourceManager() {
        INSTANCE = this;
    }

    public static GraphResourceManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GraphResourceManager();
        }
        return INSTANCE;
    }

    @Nullable
    public GraphAssetDescriptor getGraph(String graphId) {
        return graphCache.get(graphId);
    }

    @Nullable
    public CompiledGraph getArtifact(String graphId, GraphKind runtimeKind) {
        GraphAssetDescriptor descriptor = graphCache.get(graphId);
        return descriptor != null && descriptor.runtimeKind() == runtimeKind ? descriptor.artifact() : null;
    }

    /**
     * [重载触发]
     */
    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        FileToIdConverter lister = FileToIdConverter.json(FOLDER_NAME);
        Map<Identifier, JsonElement> objects = new HashMap<>();
        lister.listMatchingResources(resourceManager).forEach((fileId, resource) -> {
            Identifier id = lister.fileToId(fileId);
            try (BufferedReader reader = resource.openAsReader()) {
                objects.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                System.err.println("[GraphResourceManager]: Error loading graph " + fileId);
                e.printStackTrace();
            }
        });
        return objects;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, GraphAssetDescriptor> newCache = new HashMap<>();

        object.forEach((location, json) -> {
            try {
                String graphId = location.toString();

                CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(
                        graphId, json.getAsJsonObject());
                newCache.put(graphId, new GraphAssetDescriptor(graphId,
                        GraphTypeRegistry.INSTANCE.require(artifact.graphTypeId()), artifact));
            } catch (Exception e) {
                System.err.println("[GraphResourceManager]: Error parsing graph " + location);
                e.printStackTrace();
            }
        });

        // 缓存替换
        this.graphCache = Map.copyOf(newCache);
        GraphAssetLifecycleIndex.INSTANCE.replacePackagedGraphs(this.graphCache);
        System.out.println("[GraphResourceManager]: Loaded " + graphCache.size()
                + " source graph(s), " + GraphAssetLifecycleIndex.INSTANCE.getGraphIds().size()
                + " effective graph(s).");
    }

    /**
     * [前端/指令] 获取所有已加载图的 ID 列表。
     */
    public Set<String> getAllGraphIds() {
        return Collections.unmodifiableSet(graphCache.keySet());
    }

    public Set<String> getGraphIds(GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        Set<String> result = new java.util.HashSet<>();
        graphCache.forEach((graphId, descriptor) -> {
            if (descriptor.runtimeKind() == runtimeKind) result.add(graphId);
        });
        return Collections.unmodifiableSet(result);
    }
}
