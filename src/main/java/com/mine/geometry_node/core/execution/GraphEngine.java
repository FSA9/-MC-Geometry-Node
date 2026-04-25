package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.execution.storage.GlobalGraphStorage;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * [核心引擎门面] 系统对外提供的唯一 API 入口。
 * <p>
 * 负责协调事件触发、图查找、虚拟机实例化以及进程挂载。
 */
public class GraphEngine {

    // ==========================================
    // 高性能事件总线 (Pub/Sub Registry)
    // ==========================================

    /**
     * [事件订阅字典] 频段名称 (Frequency) -> 监听该频段的弱引用实体集合
     * 采用 WeakHashMap 包装的 Set，当实体被卸载或销毁时，会自动从字典中被垃圾回收，避免内存泄漏。
     */
    private static final Map<String, Set<Entity>> eventSubscribers = new ConcurrentHashMap<>();

    private static void addSubscriber(String frequency, Entity entity) {
        eventSubscribers.computeIfAbsent(frequency, k -> Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>())))
                .add(entity);
    }

    private static void removeSubscriber(String frequency, Entity entity) {
        Set<Entity> set = eventSubscribers.get(frequency);
        if (set != null) {
            set.remove(entity);
        }
    }

    /**
     * [扫描注册] 扫描给定的蓝图图纸，找出所有的 receive_blueprint 节点并将其静态频段注册到字典。
     */
    private static void registerEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> nodes = index.findNodesByType("receive_blueprint");
        for (int nodeId : nodes) {
            Object freqObj = index.getNodeStaticInput(nodeId, "frequency");
            String freq = freqObj != null ? String.valueOf(freqObj) : "";
            if (!freq.trim().isEmpty()) {
                addSubscriber(freq, entity);
            }
        }
    }

    /**
     * [扫描注销] 反向解除绑定
     */
    private static void unregisterEntityForGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> nodes = index.findNodesByType("receive_blueprint");
        for (int nodeId : nodes) {
            Object freqObj = index.getNodeStaticInput(nodeId, "frequency");
            String freq = freqObj != null ? String.valueOf(freqObj) : "";
            if (!freq.trim().isEmpty()) {
                removeSubscriber(freq, entity);
            }
        }
    }

    /**
     * [公开接口] 初始化实体时调用。扫描实体身上绑定的所有蓝图并注册监听器。
     */
    public static void registerEntityListeners(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;

        for (String graphId : attachment.getBoundGraphs()) {
            registerEntityForGraph(entity, graphId);
        }
    }

    // ==========================================
    // 核心触发逻辑
    // ==========================================

    public static void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Consumer<GraphProcess> initializer) {
        if (target.level().isClientSide) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, initializer);
    }

    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Consumer<GraphProcess> initializer) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);

        for (String graphId : storage.getGraphs()) {
            triggerAndMountEvent(level, target, graphId, eventNodeId, initializer, levelAttachment::addProcess);
        }

        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : entityAttachment.getBoundGraphs()) {
                    triggerAndMountEvent(level, target, graphId, eventNodeId, initializer, process -> {
                        entityAttachment.addProcess(process);
                        GraphEventHandler.markActive(target);
                    });
                }
            }
        }
    }

    /**
     * [自定义事件分发] O(1) 极速广播架构
     */
    public static void dispatchCustomEvent(@NotNull ServerLevel currentLevel, @Nullable Entity source, String frequency, @Nullable Consumer<GraphProcess> initializer) {
        if (frequency == null || frequency.trim().isEmpty()) return;

        String targetEventType = "receive_blueprint";

        // 1. 全局维度作用域：直接遍历所有维度的全局绑定图 (维度数量极少，直接遍历即可)
        GlobalGraphStorage storage = GlobalGraphStorage.get(currentLevel.getServer().overworld());
        for (ServerLevel level : currentLevel.getServer().getAllLevels()) {
            LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
            for (String graphId : storage.getGraphs()) {
                triggerAndMountCustomEvent(level, null, graphId, targetEventType, frequency, initializer, levelAttachment::addProcess);
            }
        }

        // 2. 局部实体作用域：使用字典进行 O(1) 获取，避开全服遍历
        Set<Entity> entities = eventSubscribers.get(frequency);
        if (entities != null) {
            // 利用迭代器安全遍历，避开已被标记删除或无效的实体
            for (Entity target : entities) {
                if (target.isRemoved()) continue;

                if (target.level() instanceof ServerLevel targetLevel) {
                    EntityGraphAttachment entityAttachment = getAttachment(target);
                    if (entityAttachment != null) {
                        for (String graphId : entityAttachment.getBoundGraphs()) {
                            triggerAndMountCustomEvent(targetLevel, target, graphId, targetEventType, frequency, initializer, process -> {
                                entityAttachment.addProcess(process);
                                GraphEventHandler.markActive(target);
                            });
                        }
                    }
                }
            }
        }
    }

    private static void triggerAndMountCustomEvent(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                                   String targetFrequency, @Nullable Consumer<GraphProcess> initializer, Consumer<GraphProcess> mountAction) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId);

        for (int startNodeId : startNodeIds) {
            Object staticFreqObj = index.getNodeStaticInput(startNodeId, "frequency");
            String nodeFrequency = staticFreqObj != null ? String.valueOf(staticFreqObj) : "";

            // 二次校验，防止同一图纸内混入其他频段节点
            if (!targetFrequency.equals(nodeFrequency)) {
                continue;
            }

            GraphProcess newProcess = new GraphProcess(graphId, index, startNodeId);
            newProcess.setEnvironment(level, target);

            if (initializer != null) initializer.accept(newProcess);
            mountAction.accept(newProcess);
            newProcess.tick(level.getGameTime());
        }
    }

    private static void triggerAndMountEvent(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                             @Nullable Consumer<GraphProcess> initializer, Consumer<GraphProcess> mountAction) {

        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) {
            log("  -> Graph '" + graphId + "' not found in Memory or Datapack.");
            return;
        }

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId);
        for (int startNodeId : startNodeIds) {
            GraphProcess newProcess = new GraphProcess(graphId, index, startNodeId);
            newProcess.setEnvironment(level, target);

            if (initializer != null) initializer.accept(newProcess);
            mountAction.accept(newProcess);
            newProcess.tick(level.getGameTime());
        }
    }

    // ==========================================
    // Graph Command Helpers (指令辅助获取与解绑)
    // ==========================================

    public static void bindGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) {
            log("  -> [Warning] Failed to bind: Graph '" + graphId + "' not found.");
            return;
        }

        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.bindGraph(graphId);
            registerEntityForGraph(entity, graphId); // 绑定时自动注册到高频字典
        }
    }

    public static void bindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.addGraph(graphId);
    }

    public static void unbindGraph(Entity entity, String graphId) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.unbindGraph(graphId);
            unregisterEntityForGraph(entity, graphId); // 解绑时从字典移出
        }
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
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

    public static java.util.Set<String> getBoundGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        return attachment != null ? attachment.getBoundGraphs() : java.util.Collections.emptySet();
    }

    public static java.util.Set<String> getGlobalBoundGraphs(ServerLevel level) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        return storage.getGraphs();
    }

    // ==========================================
    // Internal Helpers
    // ==========================================

    private static EntityGraphAttachment getAttachment(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }

    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        RuntimeGraphIndex dynamicIndex = com.mine.geometry_node.core.execution.storage.DynamicGraphManager.getIndex(graphId);
        if (dynamicIndex != null) return dynamicIndex;
        return GraphResourceManager.getInstance().getIndex(graphId);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}