package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.*;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.EntityInventoryGainTracker;
import com.mine.geometry_node.core.engine.blueprint.attachment.GlobalGraphStorage;
import com.mine.geometry_node.core.engine.blueprint.event.subscription.EventSubscription;
import com.mine.geometry_node.core.engine.blueprint.event.subscription.GraphSubscriptionIndex;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingRuntimeIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintCloseMode;
import com.mine.geometry_node.core.node.nodes.events.entity.OnEntityGainItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * [核心引擎门面]
 * * 经过重构，支持“常驻进程 (Persistent VM)”架构。
 * 负责协调事件派发，将事件注入到已经存在的进程中，通过轻量级线程执行。
 */
public final class BlueprintEngine {

    // ==========================================
    // 高性能事件订阅字典 (保持现状)
    // ==========================================
    private static final String RECEIVE_BLUEPRINT_EVENT_TYPE = "receive_blueprint";
    private static final String MULTIBLOCK_BUILT_EVENT_TYPE = "on_multiblock_built";
    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();
    private final Consumer<Entity> activityMarker;
    private final EntityInventoryGainTracker inventoryGainTracker;

    public BlueprintEngine(Consumer<Entity> activityMarker, EntityInventoryGainTracker inventoryGainTracker) {
        this.activityMarker = Objects.requireNonNull(activityMarker, "activityMarker");
        this.inventoryGainTracker = Objects.requireNonNull(inventoryGainTracker, "inventoryGainTracker");
    }

    private ServerState state(MinecraftServer server) {
        return servers.computeIfAbsent(server, ignored -> new ServerState());
    }

    private ServerState state(ServerLevel level) {
        return state(level.getServer());
    }

    private ServerState state(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Blueprint entity must belong to a server level");
        }
        return state(level);
    }

    // ==========================================
    // 核心事件派发 API (重构点)
    // ==========================================

    public void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Map<String, Object> eventData) {
        if (target.level().isClientSide()) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, eventData);
    }

    /**
     * [通用事件分发]
     * 逻辑：查找关联的常驻进程 -> 从进程中派发轻量级执行线程
     */
    public void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Map<String, Object> eventData) {
        ServerState serverState = state(level);

        // 处理全局图
        ensureGlobalSubscriptions(level);
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        GlobalGraphStorage globalStorage = GlobalGraphStorage.get(level.getServer().overworld());

        for (EventSubscription subscription : serverState.graphSubscriptions.globalSubscriptionsFor(eventNodeId)) {
            if (!globalStorage.getGraphs().contains(subscription.graphId())) continue;
            triggerSubscriptionOnProcess(level, target, subscription, eventData,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        // 处理局部图
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (EventSubscription subscription : getEntitySubscriptionsForEvent(target, eventNodeId)) {
                    if (!entityAttachment.getBoundGraphs().contains(subscription.graphId())) continue;
                    triggerSubscriptionOnProcess(level, target, subscription, eventData,
                            id -> entityAttachment.getProcess(id),
                            process -> {
                                entityAttachment.addProcess(process);
                            });
                }
                activityMarker.accept(target);
            }
        }
    }

    /**
     * [自定义事件派发] O(1) 广播
     */
    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency, @Nullable Map<String, Object> eventData) {
        if (frequency == null || frequency.trim().isEmpty()) return;

        // 全局作用域
        Set<String> globalGraphIds = getGlobalGraphsForEvent(currentLevel, RECEIVE_BLUEPRINT_EVENT_TYPE);
        for (ServerLevel level : currentLevel.getServer().getAllLevels()) {
            LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
            for (String graphId : globalGraphIds) {
                if (!GlobalGraphStorage.get(level.getServer().overworld()).getGraphs().contains(graphId)) continue;
                triggerCustomOnProcess(level, null, graphId, frequency, eventData,
                        id -> levelAttachment.getProcess(id),
                        levelAttachment::addProcess);
            }
        }

        // 实体作用域
        Map<Entity, Set<String>> entities =
                state(currentLevel).graphSubscriptions.receiveSubscribersFor(frequency);
        if (entities != null) {
            Entity[] snapshot = entities.keySet().toArray(new Entity[0]);
            for (Entity target : snapshot) {
                if (target.isRemoved()) continue;
                if (target.level() instanceof ServerLevel targetLevel) {
                    EntityGraphAttachment entityAttachment = getAttachment(target);
                    if (entityAttachment != null) {
                        Set<String> graphIds = entities.get(target);
                        if (graphIds == null || graphIds.isEmpty()) continue;
                        for (String graphId : new ArrayList<>(entityAttachment.getBoundGraphs())) {
                            if (!graphIds.contains(graphId)) continue;
                            triggerCustomOnProcess(targetLevel, target, graphId, frequency, eventData,
                                    id -> entityAttachment.getProcess(id),
                                    entityAttachment::addProcess);
                        }
                        activityMarker.accept(target);
                    }
                }
            }
        }
    }

    public Set<String> getInterestedMultiblockStructureIds(@NotNull ServerLevel level, @Nullable Entity target) {
        Set<String> structureIds = new HashSet<>();

        for (String graphId : getGlobalGraphsForEvent(level, MULTIBLOCK_BUILT_EVENT_TYPE)) {
            collectMultiblockStructureIds(graphId, structureIds);
        }

        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : getEntityGraphsForEvent(target, MULTIBLOCK_BUILT_EVENT_TYPE)) {
                    collectMultiblockStructureIds(graphId, structureIds);
                }
            }
        }

        return structureIds.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(structureIds);
    }

    public void dispatchMultiblockBuilt(@NotNull ServerLevel level,
                                               @Nullable Entity target,
                                               String structureId,
                                               @Nullable Map<String, Object> eventData) {
        if (structureId == null || structureId.isBlank()) return;

        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        for (String graphId : getGlobalGraphsForEvent(level, MULTIBLOCK_BUILT_EVENT_TYPE)) {
            if (!GlobalGraphStorage.get(level.getServer().overworld()).getGraphs().contains(graphId)) continue;
            triggerMultiblockOnProcess(level, target, graphId, structureId, eventData,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : getEntityGraphsForEvent(target, MULTIBLOCK_BUILT_EVENT_TYPE)) {
                    if (!entityAttachment.getBoundGraphs().contains(graphId)) continue;
                    triggerMultiblockOnProcess(level, target, graphId, structureId, eventData,
                            id -> entityAttachment.getProcess(id),
                            entityAttachment::addProcess);
                }
                activityMarker.accept(target);
            }
        }
    }

    // ==========================================
    // 内部处理逻辑 (底层重构)
    // ==========================================

    /**
     * 核心逻辑：确保进程存在，并执行指定的事件分支
     */
    private void triggerSubscriptionOnProcess(ServerLevel level,
                                                     @Nullable Entity target,
                                                     EventSubscription subscription,
                                                     @Nullable Map<String, Object> eventData,
                                                     Function<String, BlueprintProcess> processFinder,
                                                     Consumer<BlueprintProcess> mountAction) {
        if (subscription == null || !subscription.shouldDispatch(level, target, eventData)) return;

        String graphId = subscription.graphId();
        BlueprintPlan index = subscription.index();
        BlueprintProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new BlueprintProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        process.executeEvent(subscription.nodeId(), eventData);
    }

    private void triggerCustomOnProcess(ServerLevel level, @Nullable Entity target, String graphId,
                                               String targetFrequency, @Nullable Map<String, Object> eventData,
                                               java.util.function.Function<String, BlueprintProcess> processFinder,
                                               Consumer<BlueprintProcess> mountAction) {

        BlueprintPlan index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findReceiveBlueprintNodes(targetFrequency);
        BlueprintProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new BlueprintProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        for (int nodeId : startNodeIds) {
            process.executeEvent(nodeId, eventData);
        }
    }

    private void triggerMultiblockOnProcess(ServerLevel level, @Nullable Entity target, String graphId, String structureId,
                                                   @Nullable Map<String, Object> eventData,
                                                   java.util.function.Function<String, BlueprintProcess> processFinder,
                                                   Consumer<BlueprintProcess> mountAction) {
        BlueprintPlan index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findMultiblockBuiltNodes(structureId);
        if (startNodeIds.isEmpty()) return;

        BlueprintProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new BlueprintProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        for (int nodeId : startNodeIds) {
            process.executeEvent(nodeId, eventData);
        }
    }

    public void executeEventNode(@NotNull ServerLevel level,
                                        @Nullable Entity target,
                                        String graphId,
                                        BlueprintPlan index,
                                        int nodeId,
                                        @Nullable Map<String, Object> eventData,
                                        Function<String, BlueprintProcess> processFinder,
                                        Consumer<BlueprintProcess> mountAction) {
        if (index == null || nodeId < 0 || nodeId >= index.getNodeCount()) return;

        BlueprintProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new BlueprintProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        process.executeEvent(nodeId, eventData);
        if (target != null) {
            activityMarker.accept(target);
        }
    }

    private void collectMultiblockStructureIds(String graphId, Set<String> structureIds) {
        BlueprintPlan index = getGraphIndex(graphId);
        if (index != null) {
            structureIds.addAll(index.getMultiblockStructureIds());
        }
    }

    public Set<String> getGlobalGraphsForEvent(@NotNull ServerLevel level, String eventType) {
        ensureGlobalSubscriptions(level);
        return state(level).graphSubscriptions.globalGraphsFor(eventType);
    }

    public Set<String> getEntityGraphsForEvent(@NotNull Entity entity, String eventType) {
        return state(entity).graphSubscriptions.entityGraphsFor(entity, eventType);
    }

    public boolean hasEntityEventSubscription(@NotNull Entity entity, String eventType) {
        return state(entity).graphSubscriptions.hasEntitySubscriptions(entity, eventType);
    }

    public void dispatchBoundEntityEvent(@NotNull ServerLevel level,
                                                @NotNull Entity target,
                                                String eventNodeId,
                                                @Nullable Map<String, Object> eventData) {
        EntityGraphAttachment entityAttachment = getAttachment(target);
        if (entityAttachment == null) return;

        for (EventSubscription subscription : getEntitySubscriptionsForEvent(target, eventNodeId)) {
            if (!entityAttachment.getBoundGraphs().contains(subscription.graphId())) continue;
            triggerSubscriptionOnProcess(level, target, subscription, eventData,
                    entityAttachment::getProcess,
                    entityAttachment::addProcess);
        }
        activityMarker.accept(target);
    }

    /**
     * Dispatches an event only to one graph bound to the target entity.
     * This is used by graph-owned domains such as quests where broadcasting the
     * same lifecycle event to every bound graph would violate instance ownership.
     */
    public void dispatchBoundGraphEvent(@NotNull ServerLevel level,
                                               @NotNull Entity target,
                                               String graphId,
                                               String eventNodeId,
                                               @Nullable Map<String, Object> eventData) {
        String resolvedGraphId = resolveGraphId(graphId);
        if (resolvedGraphId.isEmpty() || eventNodeId == null || eventNodeId.isBlank()) return;

        EntityGraphAttachment attachment = getAttachment(target);
        if (attachment == null || !attachment.getBoundGraphs().contains(resolvedGraphId)) return;

        BlueprintPlan index = getGraphIndex(resolvedGraphId);
        if (index == null) return;

        for (int nodeId : index.findNodesByType(eventNodeId)) {
            if (!attachment.getBoundGraphs().contains(resolvedGraphId)) break;
            executeEventNode(level, target, resolvedGraphId, index, nodeId, eventData,
                    attachment::getProcess,
                    attachment::addProcess);
        }
    }

    private List<EventSubscription> getEntitySubscriptionsForEvent(@NotNull Entity entity, String eventType) {
        return state(entity).graphSubscriptions.entitySubscriptionsFor(entity, eventType);
    }

    private void ensureGlobalSubscriptions(@NotNull ServerLevel level) {
        ServerState state = state(level);
        if (state.globalSubscriptionsInitialized) return;
        GraphSubscriptionIndex graphSubscriptions = state.graphSubscriptions;
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        for (String graphId : storage.getGraphs()) {
            BlueprintPlan index = getGraphIndex(graphId);
            if (index != null) {
                graphSubscriptions.registerGlobalGraph(graphId, index);
            }
        }
        state.globalSubscriptionsInitialized = true;
    }

    // ==========================================
    // 绑定管理 (绑定即预热)
    // ==========================================

    public void bindGraph(Entity entity, String graphId) {
        graphId = GraphAssetId.require(graphId);
        BlueprintPlan index = getGraphIndex(graphId);
        if (index == null) return;

        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.attachOwner(entity);
            attachment.bindGraph(graphId);
            GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);

            BlueprintProcess process = attachment.getProcess(graphId);
            if (process == null || process.isDraining()) {
                attachment.addProcess(new BlueprintProcess(graphId, index));
            }

            registerEntityForGraph(entity, graphId);
            if (!index.findNodesByType(OnEntityGainItem.TYPE_ID).isEmpty()) {
                inventoryGainTracker.beginTracking(entity);
            }
            activityMarker.accept(entity);
            DebugRendererSessionManager.markDirty(entity.level().getServer());
        }
    }

    public void bindGlobalGraph(ServerLevel level, String graphId) {
        graphId = GraphAssetId.require(graphId);
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);

        BlueprintPlan index = getGraphIndex(graphId);
        if (index != null) {
            state(level).graphSubscriptions.registerGlobalGraph(graphId, index);
            LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
            BlueprintProcess process = attachment.getProcess(graphId);
            if (process == null || process.isDraining()) {
                attachment.addProcess(new BlueprintProcess(graphId, index));
            }
        }
        DebugRendererSessionManager.markDirty(level.getServer());
    }

    public void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, BlueprintCloseMode.IMMEDIATE);
    }

    public void unbindGraph(Entity entity, String graphId, BlueprintCloseMode closeMode) {
        graphId = GraphAssetId.require(graphId);
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId, closeMode);
            GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
            unregisterEntityForGraph(entity, graphId);
            if (getEntityGraphsForEvent(entity, OnEntityGainItem.TYPE_ID).isEmpty()) {
                inventoryGainTracker.clear(entity);
            }
            DebugRendererSessionManager.markDirty(entity.level().getServer());
        }
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, BlueprintCloseMode.IMMEDIATE);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId, BlueprintCloseMode closeMode) {
        graphId = GraphAssetId.require(graphId);
        state(level).graphSubscriptions.unregisterGlobalGraph(graphId, getGraphIndex(graphId));
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            LevelGraphAttachment.get(loadedLevel).removeProcess(graphId, closeMode);
        }
        DebugRendererSessionManager.markDirty(level.getServer());
    }

    public void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            for (String graphId : attachment.getBoundGraphs()) {
                unregisterEntityForGraph(entity, graphId);
            }
            attachment.clearGraphs();
            GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
            inventoryGainTracker.clear(entity);
            DebugRendererSessionManager.markDirty(entity.level().getServer());
        }
    }

    public void unbindAllGlobalGraphs(ServerLevel level) {
        GraphSubscriptionIndex graphSubscriptions = state(level).graphSubscriptions;
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        Set<String> graphIds = new HashSet<>(storage.getGraphs());
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            for (BlueprintProcess process : LevelGraphAttachment.get(loadedLevel).getProcesses()) {
                graphIds.add(process.getGraphId());
            }
        }
        for (String graphId : graphIds) {
            graphSubscriptions.unregisterGlobalGraph(graphId, getGraphIndex(graphId));
        }
        storage.clearGraphs();
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            LevelGraphAttachment attachment = LevelGraphAttachment.get(loadedLevel);
            for (String graphId : graphIds) {
                attachment.removeProcess(graphId);
            }
        }
        DebugRendererSessionManager.markDirty(level.getServer());
    }

    public Set<String> getBoundGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        return attachment != null ? attachment.getBoundGraphs() : Collections.emptySet();
    }

    public Set<String> getGlobalBoundGraphs(ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        return storage.getGraphs();
    }

    // ==========================================
    // 监听器注册
    // ==========================================

    public void registerEntityListeners(Entity entity) {
        GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        attachment.attachOwner(entity);

        for (String graphId : attachment.getBoundGraphs()) {
            registerEntityForGraph(entity, graphId);
        }
        if (state(entity).graphSubscriptions.hasEntitySubscriptions(entity, OnEntityGainItem.TYPE_ID)) {
            inventoryGainTracker.beginTracking(entity);
        }
    }

    private void registerEntityForGraph(Entity entity, String graphId) {
        BlueprintPlan index = getGraphIndex(graphId);
        if (index == null) return;
        state(entity).graphSubscriptions.registerEntityGraph(entity, graphId, index);
    }

    private void unregisterEntityForGraph(Entity entity, String graphId) {
        BlueprintPlan index = getGraphIndex(graphId);
        unregisterEntityForGraph(entity, graphId, index);
    }

    private void unregisterEntityForGraph(Entity entity, String graphId, @Nullable BlueprintPlan index) {
        state(entity).graphSubscriptions.unregisterEntityGraph(entity, graphId, index);
    }

    public void unregisterEntityListeners(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel)) return;
        ServerState serverState = servers.get(((ServerLevel) entity.level()).getServer());
        if (serverState != null) serverState.graphSubscriptions.unregisterEntity(entity);
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server, String graphId,
                                                 @Nullable BlueprintPlan newIndex) {
        Map<String, BlueprintPlan> newIndexes = new LinkedHashMap<>(1);
        newIndexes.put(GraphAssetId.require(graphId), newIndex);
        refreshGraphSubscriptions(server, newIndexes);
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server,
                                          Map<String, @Nullable BlueprintPlan> newIndexes) {
        Objects.requireNonNull(newIndexes, "newIndexes");
        if (newIndexes.isEmpty()) return;

        Map<String, BlueprintPlan> normalizedIndexes = new LinkedHashMap<>(newIndexes.size());
        newIndexes.forEach((graphId, newIndex) ->
                normalizedIndexes.put(GraphAssetId.require(graphId), newIndex));
        if (server != null) {
            refreshGraphSubscriptions(state(server), server, normalizedIndexes);
            return;
        }
        for (Map.Entry<MinecraftServer, ServerState> entry : new ArrayList<>(servers.entrySet())) {
            refreshGraphSubscriptions(entry.getValue(), entry.getKey(), normalizedIndexes);
        }
    }

    public boolean hasMatchingStaticEventFlag(@NotNull ServerLevel level, @Nullable Entity target,
                                              String eventNodeId, @Nullable Map<String, Object> eventData,
                                              String staticInputId) {
        ServerState serverState = state(level);

        ensureGlobalSubscriptions(level);
        GlobalGraphStorage globalStorage = GlobalGraphStorage.get(level.getServer().overworld());
        for (EventSubscription subscription : serverState.graphSubscriptions.globalSubscriptionsFor(eventNodeId)) {
            if (!globalStorage.getGraphs().contains(subscription.graphId())
                    || !subscription.shouldDispatch(level, target, eventData)) continue;
            if (subscription.index().getStaticInput(subscription.nodeId(), staticInputId,
                    Boolean.class, false)) return true;
        }

        if (target == null) return false;
        EntityGraphAttachment attachment = getAttachment(target);
        if (attachment == null) return false;
        for (EventSubscription subscription : getEntitySubscriptionsForEvent(target, eventNodeId)) {
            if (!attachment.getBoundGraphs().contains(subscription.graphId())
                    || !subscription.shouldDispatch(level, target, eventData)) continue;
            if (subscription.index().getStaticInput(subscription.nodeId(), staticInputId,
                    Boolean.class, false)) return true;
        }
        return false;
    }

    private void refreshGraphSubscriptions(ServerState state, MinecraftServer server,
                                           Map<String, BlueprintPlan> newIndexes) {
        GraphSubscriptionIndex graphSubscriptions = state.graphSubscriptions;
        Set<String> registeredGlobals = new HashSet<>();
        Map<String, Set<Entity>> registeredEntitiesByGraph = new LinkedHashMap<>(newIndexes.size());
        Set<Entity> affectedEntities = new HashSet<>();
        Map<String, Set<Entity>> indexedEntitiesByGraph =
                graphSubscriptions.registeredEntitiesForGraphs(newIndexes.keySet());

        for (String graphId : newIndexes.keySet()) {
            if (graphSubscriptions.isGlobalGraphRegistered(graphId)) {
                registeredGlobals.add(graphId);
            }
            Set<Entity> registeredEntities = new HashSet<>(
                    indexedEntitiesByGraph.getOrDefault(graphId, Collections.emptySet()));
            registeredEntitiesByGraph.put(graphId, registeredEntities);
            affectedEntities.addAll(registeredEntities);

            graphSubscriptions.unregisterGlobalGraph(graphId, null);
            for (Entity entity : registeredEntities) {
                graphSubscriptions.unregisterEntityGraph(entity, graphId, null);
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment != null) attachment.removeProcess(graphId);
            }
            graphSubscriptions.discardTemplate(graphId);
        }
        boolean worldReady = server.overworld() != null;
        if (worldReady) {
            GlobalGraphStorage storage = GlobalGraphStorage.get(server.overworld());
            for (String graphId : newIndexes.keySet()) {
                if (storage.getGraphs().contains(graphId)) {
                    registeredGlobals.add(graphId);
                } else {
                    registeredGlobals.remove(graphId);
                }
            }
            for (ServerLevel level : server.getAllLevels()) {
                LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
                for (String graphId : newIndexes.keySet()) {
                    levelAttachment.removeProcess(graphId);
                }
            }
            for (String graphId : newIndexes.keySet()) {
                Set<Entity> registeredEntities = registeredEntitiesByGraph.get(graphId);
                Set<Entity> boundEntities = GraphBindingRuntimeIndex.INSTANCE.entities(
                        server, GraphBindingKey.blueprint(graphId));
                registeredEntities.addAll(boundEntities);
                affectedEntities.addAll(boundEntities);
                for (Entity entity : boundEntities) {
                    EntityGraphAttachment attachment = getAttachment(entity);
                    if (attachment != null) attachment.removeProcess(graphId);
                }
            }
        }

        for (Map.Entry<String, BlueprintPlan> entry : newIndexes.entrySet()) {
            String graphId = entry.getKey();
            BlueprintPlan newIndex = entry.getValue();
            if (newIndex != null && registeredGlobals.contains(graphId)) {
                graphSubscriptions.registerGlobalGraph(graphId, newIndex);
            }
            if (newIndex == null) continue;
            Set<Entity> registeredEntities = registeredEntitiesByGraph.get(graphId);
            for (Entity entity : registeredEntities) {
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;
                graphSubscriptions.registerEntityGraph(entity, graphId, newIndex);
            }
        }
        for (Entity entity : affectedEntities) {
            if (graphSubscriptions.hasEntitySubscriptions(entity, OnEntityGainItem.TYPE_ID)) {
                inventoryGainTracker.beginTracking(entity);
            } else {
                inventoryGainTracker.clear(entity);
            }
        }
        DebugRendererSessionManager.markDirty(server);
    }

    @Nullable
    public BlueprintPlan getGraphIndex(String graphId) {
        String normalizedId = GraphAssetId.canonicalize(graphId);
        if (normalizedId.isEmpty()) return null;
        return asBlueprintIndex(GraphAssetLifecycleIndex.INSTANCE
                .getArtifact(normalizedId, GraphKind.BLUEPRINT));
    }

    public String resolveGraphId(@Nullable String graphId) {
        return GraphAssetId.canonicalize(graphId);
    }

    @Nullable
    private static BlueprintPlan asBlueprintIndex(@Nullable CompiledGraph artifact) {
        return artifact instanceof BlueprintPlan index ? index : null;
    }

    private EntityGraphAttachment getAttachment(Entity entity) {
        EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
        if (attachment != null) {
            attachment.attachOwner(entity);
        }
        return attachment;
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private static final class ServerState {
        private final GraphSubscriptionIndex graphSubscriptions = new GraphSubscriptionIndex();
        private boolean globalSubscriptionsInitialized;
    }
}
