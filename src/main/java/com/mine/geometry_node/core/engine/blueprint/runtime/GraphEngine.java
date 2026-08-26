package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.*;
import com.mine.geometry_node.core.engine.blueprint.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventHandler;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.EntityInventoryGainTracker;
import com.mine.geometry_node.core.engine.blueprint.attachment.GlobalGraphStorage;
import com.mine.geometry_node.core.engine.blueprint.event.subscription.EventSubscription;
import com.mine.geometry_node.core.engine.blueprint.event.subscription.GraphSubscriptionIndex;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraph;
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
public class GraphEngine {

    // ==========================================
    // 高性能事件订阅字典 (保持现状)
    // ==========================================
    private static final String RECEIVE_BLUEPRINT_EVENT_TYPE = "receive_blueprint";
    private static final String MULTIBLOCK_BUILT_EVENT_TYPE = "on_multiblock_built";
    private static final Map<String, Map<Entity, Set<String>>> eventSubscribers = new HashMap<>();
    private static final GraphSubscriptionIndex graphSubscriptions = new GraphSubscriptionIndex();

    private static void addSubscriber(String frequency, Entity entity, String graphId) {
        eventSubscribers
                .computeIfAbsent(frequency, k -> new WeakHashMap<>())
                .computeIfAbsent(entity, k -> new HashSet<>())
                .add(normalizeSubscriptionGraphId(graphId));
    }

    private static void removeSubscriber(String frequency, Entity entity, String graphId) {
        Map<Entity, Set<String>> entities = eventSubscribers.get(frequency);
        if (entities == null) return;

        Set<String> graphIds = entities.get(entity);
        if (graphIds != null) {
            graphIds.remove(normalizeSubscriptionGraphId(graphId));
            if (graphIds.isEmpty()) {
                entities.remove(entity);
            }
        }

        if (entities.isEmpty()) {
            eventSubscribers.remove(frequency);
        }
    }

    // ==========================================
    // 核心事件派发 API (重构点)
    // ==========================================

    public static void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Map<String, Object> eventData) {
        if (target.level().isClientSide()) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, eventData);
    }

    /**
     * [通用事件分发]
     * 逻辑：查找关联的常驻进程 -> 从进程中派发轻量级执行线程
     */
    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Map<String, Object> eventData) {
        Map<String, Object> eventPayload = snapshotEventData(eventData);

        // 处理全局图
        refreshGlobalSubscriptions(level);
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        GlobalGraphStorage globalStorage = GlobalGraphStorage.get(level.getServer().overworld());

        for (EventSubscription subscription : graphSubscriptions.globalSubscriptionsFor(eventNodeId)) {
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
                GraphEventHandler.markActive(target);
            }
        }
    }

    /**
     * [自定义事件派发] O(1) 广播
     */
    public static void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency, @Nullable Map<String, Object> eventData) {
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
        Map<Entity, Set<String>> entities = eventSubscribers.get(frequency);
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
                        GraphEventHandler.markActive(target);
                    }
                }
            }
        }
    }

    public static Set<String> getInterestedMultiblockStructureIds(@NotNull ServerLevel level, @Nullable Entity target) {
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

    public static void dispatchMultiblockBuilt(@NotNull ServerLevel level,
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
                GraphEventHandler.markActive(target);
            }
        }
    }

    // ==========================================
    // 内部处理逻辑 (底层重构)
    // ==========================================

    /**
     * 核心逻辑：确保进程存在，并执行指定的事件分支
     */
    private static void triggerSubscriptionOnProcess(ServerLevel level,
                                                     @Nullable Entity target,
                                                     EventSubscription subscription,
                                                     @Nullable Map<String, Object> eventData,
                                                     Function<String, GraphProcess> processFinder,
                                                     Consumer<GraphProcess> mountAction) {
        if (subscription == null || !subscription.shouldDispatch(level, target, eventData)) return;

        String graphId = subscription.graphId();
        RuntimeGraphIndex index = subscription.index();
        GraphProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new GraphProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        process.executeEvent(subscription.nodeId(), eventData);
    }

    private static void triggerCustomOnProcess(ServerLevel level, @Nullable Entity target, String graphId,
                                               String targetFrequency, @Nullable Map<String, Object> eventData,
                                               java.util.function.Function<String, GraphProcess> processFinder,
                                               Consumer<GraphProcess> mountAction) {

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findReceiveBlueprintNodes(targetFrequency);
        GraphProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new GraphProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        for (int nodeId : startNodeIds) {
            process.executeEvent(nodeId, eventData);
        }
    }

    private static void triggerMultiblockOnProcess(ServerLevel level, @Nullable Entity target, String graphId, String structureId,
                                                   @Nullable Map<String, Object> eventData,
                                                   java.util.function.Function<String, GraphProcess> processFinder,
                                                   Consumer<GraphProcess> mountAction) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findMultiblockBuiltNodes(structureId);
        if (startNodeIds.isEmpty()) return;

        GraphProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new GraphProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        for (int nodeId : startNodeIds) {
            process.executeEvent(nodeId, eventData);
        }
    }

    public static void executeEventNode(@NotNull ServerLevel level,
                                        @Nullable Entity target,
                                        String graphId,
                                        RuntimeGraphIndex index,
                                        int nodeId,
                                        @Nullable Map<String, Object> eventData,
                                        Function<String, GraphProcess> processFinder,
                                        Consumer<GraphProcess> mountAction) {
        if (index == null || nodeId < 0 || nodeId >= index.getNodeCount()) return;

        GraphProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new GraphProcess(graphId, index);
            mountAction.accept(process);
        }

        process.setEnvironment(level, target);
        process.executeEvent(nodeId, snapshotEventData(eventData));
        if (target != null) {
            GraphEventHandler.markActive(target);
        }
    }

    private static void collectMultiblockStructureIds(String graphId, Set<String> structureIds) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
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

    public static Set<String> getGlobalGraphsForEvent(@NotNull ServerLevel level, String eventType) {
        refreshGlobalSubscriptions(level);
        return graphSubscriptions.globalGraphsFor(eventType);
    }

    public static Set<String> getEntityGraphsForEvent(@NotNull Entity entity, String eventType) {
        registerEntityListeners(entity);
        return graphSubscriptions.entityGraphsFor(entity, eventType);
    }

    public static void dispatchBoundEntityEvent(@NotNull ServerLevel level,
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
        GraphEventHandler.markActive(target);
    }

    /**
     * Dispatches an event only to one graph bound to the target entity.
     * This is used by graph-owned domains such as quests where broadcasting the
     * same lifecycle event to every bound graph would violate instance ownership.
     */
    public static void dispatchBoundGraphEvent(@NotNull ServerLevel level,
                                               @NotNull Entity target,
                                               String graphId,
                                               String eventNodeId,
                                               @Nullable Map<String, Object> eventData) {
        String resolvedGraphId = resolveGraphId(graphId);
        if (resolvedGraphId.isEmpty() || eventNodeId == null || eventNodeId.isBlank()) return;

        EntityGraphAttachment attachment = getAttachment(target);
        if (attachment == null || !attachment.getBoundGraphs().contains(resolvedGraphId)) return;

        RuntimeGraphIndex index = getGraphIndex(resolvedGraphId);
        if (index == null) return;

        Map<String, Object> eventPayload = snapshotEventData(eventData);
        for (int nodeId : index.findNodesByType(eventNodeId)) {
            if (!attachment.getBoundGraphs().contains(resolvedGraphId)) break;
            executeEventNode(level, target, resolvedGraphId, index, nodeId, eventPayload,
                    attachment::getProcess,
                    attachment::addProcess);
        }
    }

    private static List<EventSubscription> getEntitySubscriptionsForEvent(@NotNull Entity entity, String eventType) {
        registerEntityListeners(entity);
        return graphSubscriptions.entitySubscriptionsFor(entity, eventType);
    }

    private static void refreshGlobalSubscriptions(@NotNull ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        for (String graphId : storage.getGraphs()) {
            RuntimeGraphIndex index = getGraphIndex(graphId);
            if (index != null) {
                graphSubscriptions.registerGlobalGraph(graphId, index);
            }
        }
    }

    // ==========================================
    // 绑定管理 (绑定即预热)
    // ==========================================

    public static void bindGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.attachOwner(entity);
            attachment.bindGraph(graphId);

            GraphProcess process = attachment.getProcess(graphId);
            if (process == null || process.isDraining()) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }

            registerEntityForGraph(entity, graphId);
            if (!index.findNodesByType(OnEntityGainItem.TYPE_ID).isEmpty()) {
                EntityInventoryGainTracker.beginTracking(entity);
            }
            GraphEventHandler.markActive(entity);
            DebugRendererSessionManager.markDirty();
        }
    }

    public static void bindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index != null) {
            graphSubscriptions.registerGlobalGraph(graphId, index);
            LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
            GraphProcess process = attachment.getProcess(graphId);
            if (process == null || process.isDraining()) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }
        }
        DebugRendererSessionManager.markDirty();
    }

    public static void unbindGraph(Entity entity, String graphId) {
        unbindGraph(entity, graphId, GraphCloseMode.IMMEDIATE);
    }

    public static void unbindGraph(Entity entity, String graphId, GraphCloseMode closeMode) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId, closeMode);
            unregisterEntityForGraph(entity, graphId);
            if (getEntityGraphsForEvent(entity, OnEntityGainItem.TYPE_ID).isEmpty()) {
                EntityInventoryGainTracker.clear(entity);
            }
            if (entity.level() instanceof ServerLevel level) {
                DebugRendererSessionManager.removeSourceShapes(level, DebugRendererSessionManager.entitySourceKey(level, entity, graphId));
            }
            DebugRendererSessionManager.markDirty();
        }
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId) {
        unbindGlobalGraph(level, graphId, GraphCloseMode.IMMEDIATE);
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId, GraphCloseMode closeMode) {
        graphSubscriptions.unregisterGlobalGraph(graphId, getGraphIndex(graphId));
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            LevelGraphAttachment.get(loadedLevel).removeProcess(graphId, closeMode);
            DebugRendererSessionManager.removeSourceShapes(loadedLevel, DebugRendererSessionManager.levelSourceKey(loadedLevel, graphId));
        }
        DebugRendererSessionManager.markDirty();
    }

    public static void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            for (String graphId : attachment.getBoundGraphs()) {
                if (entity.level() instanceof ServerLevel level) {
                    DebugRendererSessionManager.removeSourceShapes(level, DebugRendererSessionManager.entitySourceKey(level, entity, graphId));
                }
                unregisterEntityForGraph(entity, graphId);
            }
            attachment.clearGraphs();
            EntityInventoryGainTracker.clear(entity);
            DebugRendererSessionManager.markDirty();
        }
    }

    public static void unbindAllGlobalGraphs(ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        Set<String> graphIds = new HashSet<>(storage.getGraphs());
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            for (GraphProcess process : LevelGraphAttachment.get(loadedLevel).getProcesses()) {
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

    public static Set<String> getBoundGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        return attachment != null ? attachment.getBoundGraphs() : Collections.emptySet();
    }

    public static Set<String> getGlobalBoundGraphs(ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        return storage.getGraphs();
    }

    // ==========================================
    // 监听器注册
    // ==========================================

    public static void registerEntityListeners(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        attachment.attachOwner(entity);

        for (String graphId : attachment.getBoundGraphs()) {
            registerEntityForGraph(entity, graphId);
        }
    }

    private static void registerEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;
        graphSubscriptions.registerEntityGraph(entity, graphId, index);
        for (String frequency : index.getReceiveBlueprintFrequencies()) {
            addSubscriber(frequency, entity, graphId);
        }
    }

    private static void unregisterEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        unregisterEntityForGraph(entity, graphId, index);
    }

    private static void unregisterEntityForGraph(Entity entity, String graphId, @Nullable RuntimeGraphIndex index) {
        graphSubscriptions.unregisterEntityGraph(entity, graphId, index);
        if (index != null) {
            for (String frequency : index.getReceiveBlueprintFrequencies()) {
                removeSubscriber(frequency, entity, graphId);
            }
        }
    }

    public static void refreshGraphSubscriptions(MinecraftServer server, String graphId,
                                                 @Nullable RuntimeGraphIndex oldIndex,
                                                 @Nullable RuntimeGraphIndex newIndex) {
        if (server == null) return;

        GlobalGraphStorage storage = GlobalGraphStorage.get(server.overworld());
        if (storage.getGraphs().contains(graphId)) {
            graphSubscriptions.unregisterGlobalGraph(graphId, oldIndex);
            if (newIndex != null) {
                graphSubscriptions.registerGlobalGraph(graphId, newIndex);
            }
            for (ServerLevel level : server.getAllLevels()) {
                LevelGraphAttachment.get(level).removeProcess(graphId);
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;

                attachment.removeProcess(graphId);
                unregisterEntityForGraph(entity, graphId, oldIndex);
                if (newIndex != null) {
                    graphSubscriptions.registerEntityGraph(entity, graphId, newIndex);
                    for (String frequency : newIndex.getReceiveBlueprintFrequencies()) {
                        addSubscriber(frequency, entity, graphId);
                    }
                }
            }
        }
        DebugRendererSessionManager.markDirty();
    }

    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        String finalId = GraphPathMapper.normalizeId(graphId);
        RuntimeGraphIndex dynamicIndex = asBlueprintIndex(
                DynamicGraphManager.getArtifact(finalId, GraphKind.BLUEPRINT));
        if (dynamicIndex != null) return dynamicIndex;
        return asBlueprintIndex(GraphResourceManager.getInstance()
                .getArtifact(graphId, GraphKind.BLUEPRINT));
    }

    public static String resolveGraphId(@Nullable String graphId) {
        if (graphId == null || graphId.isBlank()) return "";
        String trimmedId = graphId.trim();
        String dynamicId = GraphPathMapper.normalizeId(trimmedId);
        return DynamicGraphManager.getArtifact(dynamicId, GraphKind.BLUEPRINT) != null ? dynamicId : trimmedId;
    }

    @Nullable
    private static RuntimeGraphIndex asBlueprintIndex(@Nullable CompiledGraph artifact) {
        return artifact instanceof RuntimeGraphIndex index ? index : null;
    }

    private static EntityGraphAttachment getAttachment(Entity entity) {
        EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
        if (attachment != null) {
            attachment.attachOwner(entity);
        }
        return attachment;
    }

    private static String normalizeSubscriptionGraphId(String graphId) {
        return resolveGraphId(graphId);
    }
}
