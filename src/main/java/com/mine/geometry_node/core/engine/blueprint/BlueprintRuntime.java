package com.mine.geometry_node.core.engine.blueprint;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintEngine;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.blueprint.event.BlueprintEventHandler;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputStateManager;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputKeys;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.EntityInventoryGainTracker;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.node.nodes.events.player.OnPlayerKeyEvent;
import com.mine.geometry_node.core.node.nodes.events.projectile.OnProjectileHit;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Blueprint runtime facade. The blueprint VM implementation lives under this
 * runtime package; cross-runtime code should depend on this facade when it
 * needs blueprint semantics.
 */
public final class BlueprintRuntime implements GraphRuntime {
    public static final BlueprintRuntime INSTANCE = new BlueprintRuntime();

    private final BlueprintEventHandler eventHandler;
    private final PlayerInputStateManager playerInput;
    private final EntityInventoryGainTracker inventoryGainTracker;
    private final BlueprintEngine engine;

    private BlueprintRuntime() {
        eventHandler = new BlueprintEventHandler();
        playerInput = new PlayerInputStateManager();
        inventoryGainTracker = new EntityInventoryGainTracker();
        engine = new BlueprintEngine(eventHandler::markActive, inventoryGainTracker);
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
        eventHandler.init();
    }

    @Override
    public int tickOrder() {
        return 300;
    }

    @Override
    public void tickLevel(ServerLevel level) {
        eventHandler.tickLevel(level);
        syncPlayerInputInterceptions(level);
    }

    @Override
    public void shutdown(MinecraftServer server) {
        eventHandler.shutdown(server);
        playerInput.shutdown(server);
        inventoryGainTracker.shutdown(server);
        engine.shutdown(server);
    }

    @Nullable
    public BlueprintPlan getGraphIndex(String graphId) {
        return engine.getGraphIndex(graphId);
    }

    public void bindGraph(Entity entity, String graphId) {
        engine.bindGraph(entity, graphId);
    }

    public void bindGlobalGraph(ServerLevel level, String graphId) {
        engine.bindGlobalGraph(level, graphId);
    }

    public void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGraph(Entity entity, String graphId, GraphCloseMode closeMode) {
        engine.unbindGraph(entity, graphId, closeMode);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId, GraphCloseMode closeMode) {
        engine.unbindGlobalGraph(level, graphId, closeMode);
    }

    public void unbindAllGraphs(Entity entity) {
        engine.unbindAllGraphs(entity);
    }

    public void unbindAllGlobalGraphs(ServerLevel level) {
        engine.unbindAllGlobalGraphs(level);
    }

    public Set<String> getBoundGraphs(Entity entity) {
        return engine.getBoundGraphs(entity);
    }

    public Set<String> getGlobalBoundGraphs(ServerLevel level) {
        return engine.getGlobalBoundGraphs(level);
    }

    public void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId,
                              @Nullable Map<String, Object> eventData) {
        engine.dispatchEvent(level, target, eventNodeId, eventData);
    }

    public void dispatchBoundGraphEvent(@NotNull ServerLevel level, @NotNull Entity target,
                                        String graphId, String eventNodeId,
                                        @Nullable Map<String, Object> eventData) {
        engine.dispatchBoundGraphEvent(level, target, graphId, eventNodeId, eventData);
    }

    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency,
                                    @Nullable Map<String, Object> eventData) {
        engine.dispatchCustomEvent(currentLevel, frequency, eventData);
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server, String graphId,
                                          @Nullable BlueprintPlan newIndex) {
        engine.refreshGraphSubscriptions(server, graphId, newIndex);
    }

    public String resolveGraphId(@Nullable String graphId) {
        return engine.resolveGraphId(graphId);
    }

    public Set<String> getGlobalGraphsForEvent(ServerLevel level, String eventType) {
        return engine.getGlobalGraphsForEvent(level, eventType);
    }

    public Set<String> getEntityGraphsForEvent(Entity entity, String eventType) {
        return engine.getEntityGraphsForEvent(entity, eventType);
    }

    public void dispatchBoundEntityEvent(ServerLevel level, Entity target, String eventNodeId,
                                         @Nullable Map<String, Object> eventData) {
        engine.dispatchBoundEntityEvent(level, target, eventNodeId, eventData);
    }

    public Set<String> getInterestedMultiblockStructureIds(ServerLevel level, @Nullable Entity target) {
        return engine.getInterestedMultiblockStructureIds(level, target);
    }

    public void dispatchMultiblockBuilt(ServerLevel level, @Nullable Entity target, String structureId,
                                        @Nullable Map<String, Object> eventData) {
        engine.dispatchMultiblockBuilt(level, target, structureId, eventData);
    }

    public void registerEntityListeners(Entity entity) {
        engine.registerEntityListeners(entity);
    }

    public void executeEventNode(ServerLevel level, @Nullable Entity target, String graphId,
                                 BlueprintPlan index, int nodeId,
                                 @Nullable Map<String, Object> eventData,
                                 Function<String, BlueprintProcess> processFinder,
                                 Consumer<BlueprintProcess> mountAction) {
        engine.executeEventNode(level, target, graphId, index, nodeId, eventData, processFinder, mountAction);
    }

    public void markActive(Entity entity) {
        eventHandler.markActive(entity);
    }

    public void tickEntityAreas(ServerLevel level, Entity owner, EntityGraphAttachment attachment,
                                long currentTick) {
        eventHandler.tickEntityAreas(level, owner, attachment, currentTick);
    }

    public void handlePlayerInput(ServerPlayer player, String keyId, String action, Vec3 clientVelocity) {
        playerInput.handleInput(player, keyId, action, clientVelocity);
    }

    public boolean isKeyPressed(Entity player, String keyId) {
        return playerInput.isKeyPressed(player, keyId);
    }

    public void clearPlayerInput(Entity player) {
        playerInput.clearPlayer(player);
    }

    private void syncPlayerInputInterceptions(ServerLevel level) {
        Set<String> globalGraphs = engine.getGlobalGraphsForEvent(level, OnPlayerKeyEvent.TYPE_ID);
        for (ServerPlayer player : level.players()) {
            Set<String> interceptedKeys = new HashSet<>();
            collectInterceptedKeys(globalGraphs, interceptedKeys);
            collectInterceptedKeys(engine.getEntityGraphsForEvent(player, OnPlayerKeyEvent.TYPE_ID), interceptedKeys);
            playerInput.syncInterceptions(player, interceptedKeys);
        }
    }

    private void collectInterceptedKeys(Set<String> graphIds, Set<String> destination) {
        for (String graphId : graphIds) {
            BlueprintPlan plan = engine.getGraphIndex(graphId);
            if (plan == null) continue;
            for (int nodeId : plan.findNodesByType(OnPlayerKeyEvent.TYPE_ID)) {
                if (!plan.getNodeStaticInput(nodeId, StandardPorts.INTERCEPT.getId(), Boolean.class, false)) continue;
                String keyId = plan.getNodeStaticInput(nodeId, StandardPorts.NAME.getId(), String.class, "");
                if (keyId == null || keyId.isBlank()) {
                    destination.addAll(PlayerInputKeys.ALL_KEYS);
                } else {
                    destination.add(keyId);
                }
            }
        }
    }

    public void tickEntityInventory(ServerLevel level, Entity entity, boolean listening) {
        inventoryGainTracker.tick(level, entity, listening);
    }

    public void clearEntityInventoryTracking(Entity entity) {
        inventoryGainTracker.clear(entity);
    }

    public boolean shouldInterceptProjectileHit(ServerLevel level, Entity target,
                                                Map<String, Object> eventData) {
        return engine.hasMatchingStaticEventFlag(level, target,
                OnProjectileHit.TYPE_ID,
                eventData, StandardPorts.INTERCEPT.getId());
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
