package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.*;
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

/**
 * [核心引擎门面]
 * * 经过重构，支持“常驻进程 (Persistent VM)”架构。
 * 负责协调事件派发，将事件注入到已经存在的进程中，通过轻量级线程执行。
 */
public class GraphEngine {

    // ==========================================
    // 高性能事件订阅字典 (保持现状)
    // ==========================================
    private static final Map<String, Map<Entity, Set<String>>> eventSubscribers = new HashMap<>();

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
        if (target.level().isClientSide) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, eventData);
    }

    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Map<String, Object> eventData) {
        dispatchEvent(level, target, eventNodeId, applyEventData(eventData));
    }

    /**
     * @deprecated 外部 Addon 应使用 {@link com.mine.geometry_node.api.GeometryNodeEvents}。
     * 这个入口暴露了 VM 内部线程，只保留给现有内部 dispatcher 过渡使用。
     */
    @Deprecated
    public static void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        if (target.level().isClientSide) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, initializer);
    }

    /**
     * [通用事件分发]
     * 逻辑：查找关联的常驻进程 -> 从进程中派发轻量级执行线程
     *
     * @deprecated 外部 Addon 应使用 {@link com.mine.geometry_node.api.GeometryNodeEvents}。
     * 这个入口暴露了 VM 内部线程，只保留给现有内部 dispatcher 过渡使用。
     */
    @Deprecated
    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        // 处理全局图
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);

        for (String graphId : storage.getGraphs()) {
            triggerOnProcess(level, target, graphId, eventNodeId, initializer,
                    id -> levelAttachment.getProcess(id),
                    levelAttachment::addProcess);
        }

        // 处理局部图
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : entityAttachment.getBoundGraphs()) {
                    triggerOnProcess(level, target, graphId, eventNodeId, initializer,
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
    public static void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency, @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        if (frequency == null || frequency.trim().isEmpty()) return;

        String targetEventType = "receive_blueprint";

        // 全局作用域
        GlobalGraphStorage storage = GlobalGraphStorage.get(currentLevel.getServer().overworld());
        for (ServerLevel level : currentLevel.getServer().getAllLevels()) {
            LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
            for (String graphId : storage.getGraphs()) {
                triggerCustomOnProcess(level, null, graphId, targetEventType, frequency, initializer,
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
                            triggerCustomOnProcess(targetLevel, target, graphId, targetEventType, frequency, initializer,
                                    id -> entityAttachment.getProcess(id),
                                    entityAttachment::addProcess);
                        }
                        GraphEventHandler.markActive(target);
                    }
                }
            }
        }
    }

    public static void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency, @Nullable Map<String, Object> eventData) {
        dispatchCustomEvent(currentLevel, frequency, applyEventData(eventData));
    }

    // ==========================================
    // 内部处理逻辑 (底层重构)
    // ==========================================

    /**
     * 核心逻辑：确保进程存在，并执行指定的事件分支
     */
    private static void triggerOnProcess(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                         @Nullable Consumer<GraphProcess.ExecutionThread> initializer,
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
            process.executeEvent(nodeId, initializer);
        }
    }

    private static void triggerCustomOnProcess(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                               String targetFrequency, @Nullable Consumer<GraphProcess.ExecutionThread> initializer,
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
            process.executeEvent(nodeId, initializer);
        }
    }

    @Nullable
    private static Consumer<GraphProcess.ExecutionThread> applyEventData(@Nullable Map<String, Object> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(eventData);
        return thread -> {
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                thread.setEventData(entry.getKey(), entry.getValue());
            }
        };
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
        }
    }

    public static void bindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index != null) {
            LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
            if (attachment.getProcess(graphId) == null) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }
        }
    }

    public static void unbindGraph(Entity entity, String graphId) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId);
            unregisterEntityForGraph(entity, graphId);
        }
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
        // 如果你的 LevelGraphAttachment 也实现了解绑进程，可以在这里调用
    }

    public static void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            for (String graphId : attachment.getBoundGraphs()) {
                unregisterEntityForGraph(entity, graphId);
            }
            attachment.clearGraphs();
        }
    }

    public static void unbindAllGlobalGraphs(ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.clearGraphs();
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
        for (String frequency : index.getReceiveBlueprintFrequencies()) {
            addSubscriber(frequency, entity, graphId);
        }
    }

    private static void unregisterEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        unregisterEntityForGraph(entity, graphId, index);
    }

    private static void unregisterEntityForGraph(Entity entity, String graphId, @Nullable RuntimeGraphIndex index) {
        if (index == null) return;
        for (String frequency : index.getReceiveBlueprintFrequencies()) {
            removeSubscriber(frequency, entity, graphId);
        }
    }

    public static void refreshGraphSubscriptions(MinecraftServer server, String graphId,
                                                 @Nullable RuntimeGraphIndex oldIndex,
                                                 @Nullable RuntimeGraphIndex newIndex) {
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                EntityGraphAttachment attachment = getAttachment(entity);
                if (attachment == null || !attachment.getBoundGraphs().contains(graphId)) continue;

                unregisterEntityForGraph(entity, graphId, oldIndex);
                if (newIndex != null) {
                    for (String frequency : newIndex.getReceiveBlueprintFrequencies()) {
                        addSubscriber(frequency, entity, graphId);
                    }
                }
            }
        }
    }

    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        String finalId = GraphPathMapper.normalizeId(graphId);
        RuntimeGraphIndex dynamicIndex = DynamicGraphManager.getIndex(finalId);
        if (dynamicIndex != null) return dynamicIndex;
        return GraphResourceManager.getInstance().getIndex(graphId);
    }

    private static EntityGraphAttachment getAttachment(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }

    private static String normalizeSubscriptionGraphId(String graphId) {
        RuntimeGraphIndex dynamicIndex = DynamicGraphManager.getIndex(GraphPathMapper.normalizeId(graphId));
        return dynamicIndex != null ? GraphPathMapper.normalizeId(graphId) : graphId;
    }
}
