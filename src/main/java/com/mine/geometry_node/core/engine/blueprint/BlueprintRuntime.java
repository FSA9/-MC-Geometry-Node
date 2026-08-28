package com.mine.geometry_node.core.engine.blueprint;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.Map;

/**
 * Blueprint runtime facade. The blueprint VM implementation lives under this
 * runtime package; cross-runtime code should depend on this facade when it
 * needs blueprint semantics.
 */
public final class BlueprintRuntime implements GraphRuntime {
    public static final BlueprintRuntime INSTANCE = new BlueprintRuntime();

    private BlueprintRuntime() {
    }

    @Override
    public GraphKind kind() {
        return GraphKind.BLUEPRINT;
    }

    @Override
    public String id() {
        return "geometry_node:blueprint";
    }

    @Override
    public void init() {
        GraphCompilationService.INSTANCE.register(BlueprintCompiler.INSTANCE);
        GraphAssetLifecycleIndex.INSTANCE.addChangeListener(
                GraphKind.BLUEPRINT, this::onGraphAssetsChanged);
    }

    @Nullable
    public RuntimeGraphIndex getGraphIndex(String graphId) {
        return GraphEngine.getGraphIndex(graphId);
    }

    public void bindGraph(Entity entity, String graphId) {
        GraphEngine.bindGraph(entity, graphId);
    }

    public void bindGlobalGraph(ServerLevel level, String graphId) {
        GraphEngine.bindGlobalGraph(level, graphId);
    }

    public void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGraph(Entity entity, String graphId, GraphCloseMode closeMode) {
        GraphEngine.unbindGraph(entity, graphId, closeMode);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId, GraphCloseMode closeMode) {
        GraphEngine.unbindGlobalGraph(level, graphId, closeMode);
    }

    public void unbindAllGraphs(Entity entity) {
        GraphEngine.unbindAllGraphs(entity);
    }

    public void unbindAllGlobalGraphs(ServerLevel level) {
        GraphEngine.unbindAllGlobalGraphs(level);
    }

    public Set<String> getBoundGraphs(Entity entity) {
        return GraphEngine.getBoundGraphs(entity);
    }

    public Set<String> getGlobalBoundGraphs(ServerLevel level) {
        return GraphEngine.getGlobalBoundGraphs(level);
    }

    public void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId,
                              @Nullable Map<String, Object> eventData) {
        GraphEngine.dispatchEvent(level, target, eventNodeId, eventData);
    }

    public void dispatchBoundGraphEvent(@NotNull ServerLevel level, @NotNull Entity target,
                                        String graphId, String eventNodeId,
                                        @Nullable Map<String, Object> eventData) {
        GraphEngine.dispatchBoundGraphEvent(level, target, graphId, eventNodeId, eventData);
    }

    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency,
                                    @Nullable Map<String, Object> eventData) {
        GraphEngine.dispatchCustomEvent(currentLevel, frequency, eventData);
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server, String graphId,
                                          @Nullable RuntimeGraphIndex newIndex) {
        GraphEngine.refreshGraphSubscriptions(server, graphId, newIndex);
    }

    private void onGraphAssetsChanged(GraphAssetLifecycleIndex.Change change) {
        for (String graphId : change.affectedAssetIds()) {
            RuntimeGraphIndex index = GraphAssetLifecycleIndex.INSTANCE
                    .getArtifact(graphId, GraphKind.BLUEPRINT) instanceof RuntimeGraphIndex value
                    ? value : null;
            refreshGraphSubscriptions(change.server(), graphId, index);
        }
    }

}
