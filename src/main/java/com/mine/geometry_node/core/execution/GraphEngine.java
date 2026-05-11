package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.*;
import com.mine.geometry_node.core.execution.storage.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<String, Set<Entity>> eventSubscribers = new ConcurrentHashMap<>();

    private static void addSubscriber(String frequency, Entity entity) {
        eventSubscribers.computeIfAbsent(frequency, k -> Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>())))
                .add(entity);
    }

    private static void removeSubscriber(String frequency, Entity entity) {
        Set<Entity> set = eventSubscribers.get(frequency);
        if (set != null) set.remove(entity);
    }

    // ==========================================
    // 核心事件派发 API (重构点)
    // ==========================================

    public static void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        if (target.level().isClientSide) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, initializer);
    }

    /**
     * [通用事件分发]
     * 逻辑：查找关联的常驻进程 -> 从进程中派发轻量级执行线程
     */
    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Consumer<GraphProcess.ExecutionThread> initializer) {
        // 1. 处理全局图 (维度级)
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);

        for (String graphId : storage.getGraphs()) {
            triggerOnProcess(level, target, graphId, eventNodeId, initializer,
                    id -> findProcess(levelAttachment.getProcesses(), id),
                    levelAttachment::addProcess);
        }

        // 2. 处理局部图 (实体级)
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : entityAttachment.getBoundGraphs()) {
                    triggerOnProcess(level, target, graphId, eventNodeId, initializer,
                            id -> findProcess(entityAttachment.getProcesses(), id),
                            process -> {
                                entityAttachment.addProcess(process);
                                GraphEventHandler.markActive(target);
                            });
                }
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
                        id -> findProcess(levelAttachment.getProcesses(), id),
                        levelAttachment::addProcess);
            }
        }

        // 实体作用域
        Set<Entity> entities = eventSubscribers.get(frequency);
        if (entities != null) {
            for (Entity target : entities) {
                if (target.isRemoved()) continue;
                if (target.level() instanceof ServerLevel targetLevel) {
                    EntityGraphAttachment entityAttachment = getAttachment(target);
                    if (entityAttachment != null) {
                        for (String graphId : entityAttachment.getBoundGraphs()) {
                            triggerCustomOnProcess(targetLevel, target, graphId, targetEventType, frequency, initializer,
                                    id -> findProcess(entityAttachment.getProcesses(), id),
                                    process -> {
                                        entityAttachment.addProcess(process);
                                        GraphEventHandler.markActive(target);
                                    });
                        }
                    }
                }
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

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId);
        for (int nodeId : startNodeIds) {
            Object staticFreq = index.getNodeStaticInput(nodeId, "frequency");
            if (targetFrequency.equals(String.valueOf(staticFreq))) {

                GraphProcess process = processFinder.apply(graphId);
                if (process == null) {
                    process = new GraphProcess(graphId, index);
                    mountAction.accept(process);
                }

                process.setEnvironment(level, target);
                process.executeEvent(nodeId, initializer);
            }
        }
    }

    /**
     * 【修复点】将 List 改为 Collection，兼容新版 Attachment 的 Map.values()
     */
    private static GraphProcess findProcess(Collection<GraphProcess> processes, String graphId) {
        if (processes == null) return null;
        for (GraphProcess p : processes) {
            if (p.getGraphId().equals(graphId)) return p;
        }
        return null;
    }

    // ==========================================
    // 绑定管理 (优化：绑定即预热)
    // ==========================================

    public static void bindGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.bindGraph(graphId);

            // 【优化】预热进程：确保在绑定时就创建实例，避免第一次事件触发时的开销
            if (findProcess(attachment.getProcesses(), graphId) == null) {
                attachment.addProcess(new GraphProcess(graphId, index));
            }

            registerEntityForGraph(entity, graphId);
        }
    }

    public static void bindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);

        // 全局图也一并预热
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index != null) {
            LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
            if (findProcess(attachment.getProcesses(), graphId) == null) {
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
        List<Integer> nodes = index.findNodesByType("receive_blueprint");
        for (int nodeId : nodes) {
            Object freq = index.getNodeStaticInput(nodeId, "frequency");
            if (freq != null && !String.valueOf(freq).isEmpty()) {
                addSubscriber(String.valueOf(freq), entity);
            }
        }
    }

    private static void unregisterEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;
        List<Integer> nodes = index.findNodesByType("receive_blueprint");
        for (int nodeId : nodes) {
            Object freq = index.getNodeStaticInput(nodeId, "frequency");
            if (freq != null && !String.valueOf(freq).isEmpty()) {
                removeSubscriber(String.valueOf(freq), entity);
            }
        }
    }

    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        String finalId = GraphIdMapper.normalizeId(graphId);
        RuntimeGraphIndex dynamicIndex = DynamicGraphManager.getIndex(finalId);
        if (dynamicIndex != null) return dynamicIndex;
        return GraphResourceManager.getInstance().getIndex(graphId);
    }

    private static EntityGraphAttachment getAttachment(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }
}