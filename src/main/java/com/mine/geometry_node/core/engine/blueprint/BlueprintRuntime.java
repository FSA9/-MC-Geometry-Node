package com.mine.geometry_node.core.engine.blueprint;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.blueprint.execution.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.execution.GraphProcess;
import com.mine.geometry_node.core.engine.blueprint.execution.RuntimeGraphIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

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
        GraphEngine.unbindGraph(entity, graphId);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        GraphEngine.unbindGlobalGraph(level, graphId);
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
                              @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        GraphEngine.dispatchEvent(level, target, eventNodeId, initializer);
    }

    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency,
                                    @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        GraphEngine.dispatchCustomEvent(currentLevel, frequency, initializer);
    }

    public void refreshGraphSubscriptions(MinecraftServer server, String graphId,
                                          @Nullable RuntimeGraphIndex oldIndex,
                                          @Nullable RuntimeGraphIndex newIndex) {
        GraphEngine.refreshGraphSubscriptions(server, graphId, oldIndex, newIndex);
    }
}
