package com.mine.geometry_node.core.engine.blueprint;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintEngine;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.blueprint.event.BlueprintEventHandler;
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

    @Override
    public int tickOrder() {
        return 300;
    }

    @Override
    public void tickLevel(ServerLevel level) {
        BlueprintEventHandler.tickLevel(level);
    }

    @Override
    public void shutdown(MinecraftServer server) {
        BlueprintEventHandler.shutdown();
    }

    @Nullable
    public BlueprintPlan getGraphIndex(String graphId) {
        return BlueprintEngine.getGraphIndex(graphId);
    }

    public void bindGraph(Entity entity, String graphId) {
        BlueprintEngine.bindGraph(entity, graphId);
    }

    public void bindGlobalGraph(ServerLevel level, String graphId) {
        BlueprintEngine.bindGlobalGraph(level, graphId);
    }

    public void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGraph(Entity entity, String graphId, GraphCloseMode closeMode) {
        BlueprintEngine.unbindGraph(entity, graphId, closeMode);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId, GraphCloseMode closeMode) {
        BlueprintEngine.unbindGlobalGraph(level, graphId, closeMode);
    }

    public void unbindAllGraphs(Entity entity) {
        BlueprintEngine.unbindAllGraphs(entity);
    }

    public void unbindAllGlobalGraphs(ServerLevel level) {
        BlueprintEngine.unbindAllGlobalGraphs(level);
    }

    public Set<String> getBoundGraphs(Entity entity) {
        return BlueprintEngine.getBoundGraphs(entity);
    }

    public Set<String> getGlobalBoundGraphs(ServerLevel level) {
        return BlueprintEngine.getGlobalBoundGraphs(level);
    }

    public void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId,
                              @Nullable Map<String, Object> eventData) {
        BlueprintEngine.dispatchEvent(level, target, eventNodeId, eventData);
    }

    public void dispatchBoundGraphEvent(@NotNull ServerLevel level, @NotNull Entity target,
                                        String graphId, String eventNodeId,
                                        @Nullable Map<String, Object> eventData) {
        BlueprintEngine.dispatchBoundGraphEvent(level, target, graphId, eventNodeId, eventData);
    }

    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency,
                                    @Nullable Map<String, Object> eventData) {
        BlueprintEngine.dispatchCustomEvent(currentLevel, frequency, eventData);
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server, String graphId,
                                          @Nullable BlueprintPlan newIndex) {
        BlueprintEngine.refreshGraphSubscriptions(server, graphId, newIndex);
    }

    private void onGraphAssetsChanged(GraphAssetLifecycleIndex.Change change) {
        for (String graphId : change.affectedAssetIds()) {
            BlueprintPlan index = GraphAssetLifecycleIndex.INSTANCE
                    .getArtifact(graphId, GraphKind.BLUEPRINT) instanceof BlueprintPlan value
                    ? value : null;
            refreshGraphSubscriptions(change.server(), graphId, index);
        }
    }

}
