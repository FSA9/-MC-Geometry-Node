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
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
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

    private void addSubscriber(String frequency, Entity entity, String graphId) {
        state(entity).eventSubscribers
                .computeIfAbsent(frequency, k -> new WeakHashMap<>())
                .computeIfAbsent(entity, k -> new HashSet<>())
                .add(normalizeSubscriptionGraphId(graphId));
    }

    private void removeSubscriber(String frequency, Entity entity, String graphId) {
        Map<String, Map<Entity, Set<String>>> subscribers = state(entity).eventSubscribers;
        Map<Entity, Set<String>> entities = subscribers.get(frequency);
        if (entities == null) return;

        Set<String> graphIds = entities.get(entity);
        if (graphIds != null) {
            graphIds.remove(normalizeSubscriptionGraphId(graphId));
            if (graphIds.isEmpty()) {
                entities.remove(entity);
            }
        }

        if (entities.isEmpty()) {
            subscribers.remove(frequency);
        }
    }

    private void removeSubscribersForGraph(ServerState state, String graphId) {
        String normalizedId = normalizeSubscriptionGraphId(graphId);
        state.eventSubscribers.entrySet().removeIf(frequencyEntry -> {
            Map<Entity, Set<String>> entities = frequencyEntry.getValue();
            entities.entrySet().removeIf(entityEntry -> {
                entityEntry.getValue().remove(normalizedId);
                return entityEntry.getValue().isEmpty();
            });
            return entities.isEmpty();
        });
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
        Map<String, Object> eventPayload = snapshotEventData(eventData);
        ServerState serverState = state(level);

        // 处理全局图
        refreshGlobalSubscriptions(level);
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        GlobalGraphStorage globalStorage = GlobalGraphStorage.get(level.getServer().overworld());

        for (EventSubscription subscription : serverState.graphSubscriptions.globalSubscriptionsFor(eventNodeId)) {
            if (!globalStorage.getGraphs().contains(subscription.graphId())) continue;
            triggerSubscriptionOnProcess(level, target, subscription, eventPayload,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        // 处理局部图
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (EventSubscription subscription : getEntitySubscriptionsForEvent(target, eventNodeId)) {
                    if (!entityAttachment.getBoundGraphs().contains(subscription.graphId())) continue;
                    triggerSubscriptionOnProcess(level, target, subscription, eventPayload,
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
        Map<String, Object> eventPayload = snapshotEventData(eventData);

        // 全局作用域
        Set<String> globalGraphIds = getGlobalGraphsForEvent(currentLevel, RECEIVE_BLUEPRINT_EVENT_TYPE);
        for (ServerLevel level : currentLevel.getServer().getAllLevels()) {
            LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
            for (String graphId : globalGraphIds) {
                if (!GlobalGraphStorage.get(level.getServer().overworld()).getGraphs().contains(graphId)) continue;
                triggerCustomOnProcess(level, null, graphId, frequency, eventPayload,
                        id -> levelAttachment.getProcess(id),
                        levelAttachment::addProcess);
            }
        }

        // 实体作用域
        Map<Entity, Set<String>> entities = state(currentLevel).eventSubscribers.get(frequency);
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
                            if (!graphIds.contains(normalizeSubscriptionGraphId(graphId))) continue;
                            triggerCustomOnProcess(targetLevel, target, graphId, frequency, eventPayload,
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
        Map<String, Object> eventPayload = snapshotEventData(eventData);

        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        for (String graphId : getGlobalGraphsForEvent(level, MULTIBLOCK_BUILT_EVENT_TYPE)) {
            if (!GlobalGraphStorage.get(level.getServer().overworld()).getGraphs().contains(graphId)) continue;
            triggerMultiblockOnProcess(level, target, graphId, structureId, eventPayload,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : getEntityGraphsForEvent(target, MULTIBLOCK_BUILT_EVENT_TYPE)) {
                    if (!entityAttachment.getBoundGraphs().contains(graphId)) continue;
                    triggerMultiblockOnProcess(level, target, graphId, structureId, eventPayload,
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
        process.executeEvent(nodeId, snapshotEventData(eventData));
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

    @Nullable
    private static Map<String, Object> snapshotEventData(@Nullable Map<String, Object> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(eventData);
    }

    public Set<String> getGlobalGraphsForEvent(@NotNull ServerLevel level, String eventType) {
        refreshGlobalSubscriptions(level);
        return state(level).graphSubscriptions.globalGraphsFor(eventType);
    }

    public Set<String> getEntityGraphsForEvent(@NotNull Entity entity, String eventType) {
        registerEntityListeners(entity);
        return state(entity).graphSubscriptions.entityGraphsFor(entity, eventType);
    }

    public void dispatchBoundEntityEvent(@NotNull ServerLevel level,
                                                @NotNull Entity target,
                                                String eventNodeId,
                                                @Nullable Map<String, Object> eventData) {
        Map<String, Object> eventPayload = snapshotEventData(eventData);

        EntityGraphAttachment entityAttachment = getAttachment(target);
        if (entityAttachment == null) return;

        for (EventSubscription subscription : getEntitySubscriptionsForEvent(target, eventNodeId)) {
            if (!entityAttachment.getBoundGraphs().contains(subscription.graphId())) continue;
            triggerSubscriptionOnProcess(level, target, subscription, eventPayload,
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

        Map<String, Object> eventPayload = snapshotEventData(eventData);
        for (int nodeId : index.findNodesByType(eventNodeId)) {
            if (!attachment.getBoundGraphs().contains(resolvedGraphId)) break;
            executeEventNode(level, target, resolvedGraphId, index, nodeId, eventPayload,
                    attachment::getProcess,
                    attachment::addProcess);
        }
    }

    private List<EventSubscription> getEntitySubscriptionsForEvent(@NotNull Entity entity, String eventType) {
        registerEntityListeners(entity);
        return state(entity).graphSubscriptions.entitySubscriptionsFor(entity, eventType);
    }

    private void refreshGlobalSubscriptions(@NotNull ServerLevel level) {
        GraphSubscriptionIndex graphSubscriptions = state(level).graphSubscriptions;
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        for (String graphId : storage.getGraphs()) {
            BlueprintPlan index = getGraphIndex(graphId);
            if (index != null) {
                graphSubscriptions.registerGlobalGraph(graphId, index);
            }
        }
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

            BlueprintProcess process = attachment.getProcess(graphId);
            if (process == null || process.isDraining()) {
                attachment.addProcess(new BlueprintProcess(graphId, index));
            }

            registerEntityForGraph(entity, graphId);
            if (!index.findNodesByType(OnEntityGainItem.TYPE_ID).isEmpty()) {
                inventoryGainTracker.beginTracking(entity);
            }
            activityMarker.accept(entity);
            DebugRendererSessionManager.markDirty();
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
        DebugRendererSessionManager.markDirty();
    }

    public void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGraph(Entity entity, String graphId, GraphCloseMode closeMode) {
        graphId = GraphAssetId.require(graphId);
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId, closeMode);
            unregisterEntityForGraph(entity, graphId);
            if (getEntityGraphsForEvent(entity, OnEntityGainItem.TYPE_ID).isEmpty()) {
                inventoryGainTracker.clear(entity);
            }
            if (entity.level() instanceof ServerLevel level) {
                DebugRendererSessionManager.removeSourceShapes(level, DebugRendererSessionManager.entitySourceKey(level, entity, graphId));
            }
            DebugRendererSessionManager.markDirty();
        }
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId, GraphCloseMode closeMode) {
        graphId = GraphAssetId.require(graphId);
        state(level).graphSubscriptions.unregisterGlobalGraph(graphId, getGraphIndex(graphId));
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            LevelGraphAttachment.get(loadedLevel).removeProcess(graphId, closeMode);
            DebugRendererSessionManager.removeSourceShapes(loadedLevel, DebugRendererSessionManager.levelSourceKey(loadedLevel, graphId));
        }
        DebugRendererSessionManager.markDirty();
    }

    public void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            for (String graphId : attachment.getBoundGraphs()) {
                if (entity.level() instanceof ServerLevel level) {
                    DebugRendererSessionManager.removeSourceShapes(level, DebugRendererSessionManager.entitySourceKey(level, entity, graphId));
                }
                unregisterEntityForGraph(entity, graphId);
            }
            attachment.clearGraphs();
            inventoryGainTracker.clear(entity);
            DebugRendererSessionManager.markDirty();
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
                DebugRendererSessionManager.removeSourceShapes(loadedLevel, DebugRendererSessionManager.levelSourceKey(loadedLevel, graphId));
            }
        }
        DebugRendererSessionManager.markDirty();
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
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        attachment.attachOwner(entity);

        for (String graphId : attachment.getBoundGraphs()) {
            registerEntityForGraph(entity, graphId);
        }
    }

    private void registerEntityForGraph(Entity entity, String graphId) {
        BlueprintPlan index = getGraphIndex(graphId);
        if (index == null) return;
        state(entity).graphSubscriptions.registerEntityGraph(entity, graphId, index);
        for (String frequency : index.getReceiveBlueprintFrequencies()) {
            addSubscriber(frequency, entity, graphId);
        }
    }

    private void unregisterEntityForGraph(Entity entity, String graphId) {
        BlueprintPlan index = getGraphIndex(graphId);
        unregisterEntityForGraph(entity, graphId, index);
    }

    private void unregisterEntityForGraph(Entity entity, String graphId, @Nullable BlueprintPlan index) {
        state(entity).graphSubscriptions.unregisterEntityGraph(entity, graphId, index);
        if (index != null) {
            for (String frequency : index.getReceiveBlueprintFrequencies()) {
                removeSubscriber(frequency, entity, graphId);
            }
        }
    }

    public void refreshGraphSubscriptions(@Nullable MinecraftServer server, String graphId,
                                                 @Nullable BlueprintPlan newIndex) {
        graphId = GraphAssetId.require(graphId);
        if (server != null) {
            refreshGraphSubscriptions(state(server), server, graphId, newIndex);
            return;
        }
        for (Map.Entry<MinecraftServer, ServerState> entry : new ArrayList<>(servers.entrySet())) {
            refreshGraphSubscriptions(entry.getValue(), entry.getKey(), graphId, newIndex);
        }
    }

    private void refreshGraphSubscriptions(ServerState state, MinecraftServer server, String graphId,
                                           @Nullable BlueprintPlan newIndex) {
        GraphSubscriptionIndex graphSubscriptions = state.graphSubscriptions;
        boolean registeredGlobal = graphSubscriptions.isGlobalGraphRegistered(graphId);
        Set<Entity> registeredEntities = new HashSet<>(
                graphSubscriptions.registeredEntitiesForGraph(graphId));
        graphSubscriptions.unregisterGlobalGraph(graphId, null);
        for (Entity entity : registeredEntities) {
            graphSubscriptions.unregisterEntityGraph(entity, graphId, null);
            EntityGraphAttachment attachment = getAttachment(entity);
            if (attachment != null) attachment.removeProcess(graphId);
        }
        removeSubscribersForGraph(state, graphId);

        boolean worldReady = server.overworld() != null;
        if (worldReady) {
            GlobalGraphStorage storage = GlobalGraphStorage.get(server.overworld());
            registeredGlobal = storage.getGraphs().contains(graphId);
            for (ServerLevel level : server.getAllLevels()) {
                LevelGraphAttachment.get(level).removeProcess(graphId);
                for (Entity entity : level.getAllEntities()) {
                    EntityGraphAttachment attachment = getAttachment(entity);
                    if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;
                    registeredEntities.add(entity);
                    attachment.removeProcess(graphId);
                }
            }
        }

        if (newIndex != null && registeredGlobal) {
            graphSubscriptions.registerGlobalGraph(graphId, newIndex);
        }
        if (newIndex != null) {
            for (Entity entity : registeredEntities) {
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;
                graphSubscriptions.registerEntityGraph(entity, graphId, newIndex);
                for (String frequency : newIndex.getReceiveBlueprintFrequencies()) {
                    addSubscriber(frequency, entity, graphId);
                }
            }
        }
        DebugRendererSessionManager.markDirty();
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

    private String normalizeSubscriptionGraphId(String graphId) {
        return resolveGraphId(graphId);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private static final class ServerState {
        private final Map<String, Map<Entity, Set<String>>> eventSubscribers = new HashMap<>();
        private final GraphSubscriptionIndex graphSubscriptions = new GraphSubscriptionIndex();
    }
}
