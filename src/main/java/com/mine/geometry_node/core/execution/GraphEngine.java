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

import java.util.List;
import java.util.function.Consumer;

/**
 * [核心引擎门面] 系统对外提供的唯一 API 入口。
 * <p>
 * 负责协调事件触发、图查找、虚拟机实例化以及进程挂载。
 */
public class GraphEngine {
    /**
     * [事件触发 - 实体快捷版] 兼容以实体为主体触发的事件。
     */
    public static void dispatchEvent(@NotNull Entity target, String eventNodeId, @Nullable Consumer<GraphProcess> initializer) {
        if (target.level().isClientSide) return;
        dispatchEvent((ServerLevel) target.level(), target, eventNodeId, initializer);
    }

    /**
     * [事件触发 - 核心引擎] 支持有实体或无实体的事件分发。
     * <p>
     * - 全局图将被挂载到所在维度的 LevelGraphAttachment 中。
     * - 局部图将被挂载到触发实体的 EntityGraphAttachment 中。
     *
     * @param level       事件发生的维度世界
     * @param target      事件的主体实体 (如果纯世界事件如天气变化，可传入 null)
     * @param eventNodeId 触发的起始节点类型
     * @param initializer 初始数据注入器
     */
    public static void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId, @Nullable Consumer<GraphProcess> initializer) {
        // 触发全局绑定图
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);

        for (String graphId : storage.getGraphs()) {
            triggerAndMountEvent(level, target, graphId, eventNodeId, initializer, process -> {
                levelAttachment.addProcess(process);
            });
        }

        // 触发局部绑定图 (放入触发实体背包)
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : entityAttachment.getBoundGraphs()) {
                    triggerAndMountEvent(level, target, graphId, eventNodeId, initializer, process -> {
                        // 挂载到实体，并唤醒该实体的Tick驱动
                        entityAttachment.addProcess(process);
                        GraphEventHandler.markActive(target);
                    });
                }
            } else {
                log("  -> [Warning] Entity has no attachment. Local graph dropped for: " + target.getName().getString());
            }
        }
    }

    /**
     * [自定义事件分发] 专门用于跨蓝图通信。
     * 只有静态配置的 "frequency" 与发送端相匹配的接收节点才会被唤醒。
     *
     * @param level       事件发生的维度世界
     * @param target      事件的主体实体
     * @param frequency   目标频率名称
     * @param initializer 初始数据注入器（方便后续扩展传参）
     */
    public static void dispatchCustomEvent(@NotNull ServerLevel level, @Nullable Entity target, String frequency, @Nullable Consumer<GraphProcess> initializer) {
        if (frequency == null || frequency.trim().isEmpty()) return;

        String targetEventType = "communication_receive_blueprint";

        // 1. 触发全局绑定图 (当前维度)
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);

        for (String graphId : storage.getGraphs()) {
            triggerAndMountCustomEvent(level, target, graphId, targetEventType, frequency, initializer, process -> {
                levelAttachment.addProcess(process);
            });
        }

        // 2. 触发局部绑定图 (目标实体)
        if (target != null) {
            EntityGraphAttachment entityAttachment = getAttachment(target);
            if (entityAttachment != null) {
                for (String graphId : entityAttachment.getBoundGraphs()) {
                    triggerAndMountCustomEvent(level, target, graphId, targetEventType, frequency, initializer, process -> {
                        entityAttachment.addProcess(process);
                        GraphEventHandler.markActive(target);
                    });
                }
            } else {
                log("  -> [Warning] Entity has no attachment. Local custom event dropped for: " + target.getName().getString());
            }
        }
    }

    /**
     * [带过滤的挂载逻辑] 在实例化虚拟机之前，前置读取节点的静态输入进行匹配。
     */
    private static void triggerAndMountCustomEvent(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                                   String targetFrequency, @Nullable Consumer<GraphProcess> initializer, Consumer<GraphProcess> mountAction) {
        RuntimeGraphIndex index = getGraphIndex(graphId);
        if (index == null) return;

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId); // 这里变成了 int

        for (int startNodeId : startNodeIds) {
            Object staticFreqObj = index.getNodeStaticInput(startNodeId, "frequency");

            String nodeFrequency = "";

            if (staticFreqObj instanceof String s) {
                nodeFrequency = s;
            } else if (staticFreqObj != null) {
                nodeFrequency = String.valueOf(staticFreqObj);
            }

            if (!targetFrequency.equals(nodeFrequency)) {
                continue;
            }

            // 频率匹配&实例化
            GraphProcess newProcess = new GraphProcess(graphId, index, startNodeId);
            newProcess.setEnvironment(level, target);

            // 注入初始参数
            if (initializer != null) {
                initializer.accept(newProcess);
            }

            // 执行挂载回调并启动首帧
            mountAction.accept(newProcess);
            newProcess.tick(level.getGameTime());
        }
    }

    // ==========================================
    // Graph Command Helpers (指令辅助获取与解绑)
    // ==========================================

    public static void bindGraph(Entity entity, String graphId) {
        RuntimeGraphIndex index = GraphResourceManager.getInstance().getIndex(graphId);
        if (index == null) return;

        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
            attachment.bindGraph(graphId);
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
        }
    }

    public static void unbindGlobalGraph(ServerLevel level, String graphId) {
        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        storage.removeGraph(graphId);
    }

    public static void unbindAllGraphs(Entity entity) {
        EntityGraphAttachment attachment = getAttachment(entity);
        if (attachment != null) {
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

    // Internal Helpers

    /**
     * 统一的实例化与挂载逻辑
     */
    private static void triggerAndMountEvent(ServerLevel level, @Nullable Entity target, String graphId, String eventNodeId,
                                             @Nullable Consumer<GraphProcess> initializer, Consumer<GraphProcess> mountAction) {

        RuntimeGraphIndex index = GraphResourceManager.getInstance().getIndex(graphId);
        if (index == null) {
            log("  -> Graph '" + graphId + "' not found in ResourceManager.");
            return;
        }

        List<Integer> startNodeIds = index.findNodesByType(eventNodeId);
        for (int startNodeId : startNodeIds) {
            GraphProcess newProcess = new GraphProcess(graphId, index, startNodeId);

            newProcess.setEnvironment(level, target);

            // 注入初始参数
            if (initializer != null) {
                initializer.accept(newProcess);
            }

            // 执行挂载回调
            mountAction.accept(newProcess);

            newProcess.tick(level.getGameTime());
        }
    }

    private static EntityGraphAttachment getAttachment(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }

    /**
     * [双轨制查找] 核心图纸解析路由
     * 优先级：动态内存热更图 > 静态数据包图
     */
    @Nullable
    public static RuntimeGraphIndex getGraphIndex(String graphId) {
        // 1. 优先去动态图管理器中查找 (玩家 UI 实时保存的)
        RuntimeGraphIndex dynamicIndex = com.mine.geometry_node.core.execution.storage.DynamicGraphManager.getIndex(graphId);
        if (dynamicIndex != null) {
            return dynamicIndex;
        }

        // 2. 兜底去数据包资源管理器中查找 (模组自带或外部数据包)
        return GraphResourceManager.getInstance().getIndex(graphId);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}