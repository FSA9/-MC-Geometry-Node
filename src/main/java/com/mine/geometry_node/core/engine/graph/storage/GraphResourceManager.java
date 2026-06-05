package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * [资源管理器] 集成 Minecraft 数据包系统。
 * 负责监听数据包重载事件，读取 `data/[modid]/graphs/` 路径下的 JSON 文件，
 * 并将其编译为 {@link RuntimeGraphIndex}。
 */
public class GraphResourceManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FOLDER_NAME = "graphs";
    private static GraphResourceManager INSTANCE;

    // 核心缓存：图 ID (ResourceLocation) -> 运行时索引
    private Map<String, RuntimeGraphIndex> indexCache = Collections.emptyMap();

    public GraphResourceManager() {
        super(GSON, FOLDER_NAME);
        INSTANCE = this;
    }

    public static GraphResourceManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GraphResourceManager();
        }
        return INSTANCE;
    }

    /**
     * 根据 ID 获取图索引。
     * @param graphName 格式： "modid:path/to/graph"
     */
    @Nullable
    public RuntimeGraphIndex getIndex(String graphName) {
        return indexCache.get(graphName);
    }

    /**
     * [重载触发]
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, RuntimeGraphIndex> newCache = new HashMap<>();

        object.forEach((location, json) -> {
            try {
                String graphId = location.toString();
                if (json.isJsonObject() && json.getAsJsonObject().has("graph_name")) {

                }

                try (java.io.Reader reader = new java.io.StringReader(json.toString())) {
                    RuntimeGraphIndex index = BlueprintCompiler.compile(reader);
                    newCache.put(graphId, index);
                }
            } catch (Exception e) {
                System.err.println("[GraphResourceManager]: Error parsing graph " + location);
                e.printStackTrace();
            }
        });

        // 缓存替换
        this.indexCache = Map.copyOf(newCache);
        System.out.println("[GraphResourceManager]: Loaded " + indexCache.size() + " graph(s).");
    }

    /**
     * [前端/指令] 获取所有已加载图的 ID 列表。
     */
    public Set<String> getAllGraphIds() {
        return Collections.unmodifiableSet(indexCache.keySet());
    }
}
