package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.*;
import com.mine.geometry_node.core.engine.blueprint.debug.AreaDebugSessionManager;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventHandler;
import com.mine.geometry_node.core.engine.blueprint.attachment.GlobalGraphStorage;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
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

        for (String graphId : graphSubscriptions.globalGraphsFor(eventNodeId)) {
            triggerOnProcess(level, target, graphId, eventNodeId, eventPayload,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        // 处理局部图
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : getEntityGraphsForEvent(target, eventNodeId)) {
                    triggerOnProcess(level, target, graphId, eventNodeId, eventPayload,
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
                        for (String graphId : entityAttachment.getBoundGraphs()) {
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
            triggerMultiblockOnProcess(level, target, graphId, structureId, eventPayload,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : getEntityGraphsForEvent(target, MULTIBLOCK_BUILT_EVENT_TYPE)) {
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
    private static void triggerOnProcess(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                         @Nullable Map<String, Object> eventData,
                                         java.util.function.Function<String, GraphProcess> processFinder,
                                         Consumer<GraphProcess> mountAction) {

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId);
        if (startNodeIds.isEmpty()) return;

        // 获取或创建常驻进程
        GraphProcess process = processFinder.apply(graphId);
        if (process == null || process.getIndex() != index) {
            process = new GraphProcess(graphId, index);
            mountAction.accept(process);
        }

        // 注入环境并启动线程
        process.setEnvironment(level, target);
        for (int nodeId : startNodeIds) {
            process.executeEvent(nodeId, eventData);
        }
    }

    private static void triggerCustomOnProcess(ServerLevel level, @Nullable Entity target, String graphId,
                                               String targetFrequency, @Nullable Map<String, Object> eventData,
                                               java.util.function.Function<String, GraphProcess> processFinder,
                                               Consumer<GraphProcess> mountAction) {

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findReceiveBlueprintNodes(targetFrequency);
        for (int nodeId : startNodeIds) {
            GraphProcess process = processFinder.apply(graphId);
            if (process == null) {
                process = new GraphProcess(graphId, index);
                mountAction.accept(process);
            }

            process.setEnvironment(level, target);
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
            attachment.bindGraph(graphId);

            if (attachment.getProcess(graphId) == null) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }

            registerEntityForGraph(entity, graphId);
            GraphEventHandler.markActive(entity);
            AreaDebugSessionManager.markDirty();
        }
    }

    public static void bindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index != null) {
            graphSubscriptions.registerGlobalGraph(graphId, index);
            LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
            if (attachment.getProcess(graphId) == null) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }
        }
        AreaDebugSessionManager.markDirty();
    }

    public static void unbindGraph(Entity entity, String graphId) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId);
            unregisterEntityForGraph(entity, graphId);
            if (entity.level() instanceof ServerLevel level) {
                AreaDebugSessionManager.removeSourceBoxes(level, AreaDebugSessionManager.entitySourceKey(level, entity, graphId));
            }
            AreaDebugSessionManager.markDirty();
        }
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId) {
        graphSubscriptions.unregisterGlobalGraph(graphId, getGraphIndex(graphId));
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            LevelGraphAttachment.get(loadedLevel).removeProcess(graphId);
            AreaDebugSessionManager.removeSourceBoxes(loadedLevel, AreaDebugSessionManager.levelSourceKey(loadedLevel, graphId));
        }
        AreaDebugSessionManager.markDirty();
    }

    public static void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            for (String graphId : attachment.getBoundGraphs()) {
                if (entity.level() instanceof ServerLevel level) {
                    AreaDebugSessionManager.removeSourceBoxes(level, AreaDebugSessionManager.entitySourceKey(level, entity, graphId));
                }
                unregisterEntityForGraph(entity, graphId);
            }
            attachment.clearGraphs();
            AreaDebugSessionManager.markDirty();
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
                AreaDebugSessionManager.removeSourceBoxes(loadedLevel, AreaDebugSessionManager.levelSourceKey(loadedLevel, graphId));
            }
        }
        AreaDebugSessionManager.markDirty();
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
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;

                unregisterEntityForGraph(entity, graphId, oldIndex);
                if (newIndex != null) {
                    graphSubscriptions.registerEntityGraph(entity, graphId, newIndex);
                    for (String frequency : newIndex.getReceiveBlueprintFrequencies()) {
                        addSubscriber(frequency, entity, graphId);
                    }
                }
            }
        }
        AreaDebugSessionManager.markDirty();
    }

    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        String finalId = GraphPathMapper.normalizeId(graphId);
        RuntimeGraphIndex dynamicIndex = DynamicGraphManager.getIndex(finalId);
        if (dynamicIndex != null) return dynamicIndex;
        return GraphResourceManager.getInstance().getIndex(graphId);
    }

    private static EntityGraphAttachment getAttachment(Entity entity) {
        EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
        if (attachment != null) {
            attachment.attachOwner(entity);
        }
        return attachment;
    }

    private static String normalizeSubscriptionGraphId(String graphId) {
        RuntimeGraphIndex dynamicIndex = DynamicGraphManager.getIndex(GraphPathMapper.normalizeId(graphId));
        return dynamicIndex != null ? GraphPathMapper.normalizeId(graphId) : graphId;
    }
}
