package com.mine.geometry_node.core.execution.event_handler;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.execution.event_handler.dispatcher.BlockDispatcher;
import com.mine.geometry_node.core.execution.event_handler.dispatcher.EntityDispatcher;
import com.mine.geometry_node.core.execution.event_handler.dispatcher.PlayerDispatcher;
import com.mine.geometry_node.core.execution.event_handler.dispatcher.WorldDispatcher;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * [蓝图事件系统枢纽]
 * 职责：
 * 1. 驱动每 Tick 的蓝图逻辑更新。
 * 2. 初始化各个领域的物理事件分发器。
 */
public class GraphEventHandler {

    // 活跃实体清单，使用 WeakHashMap 防止内存泄漏
    private static final Set<Entity> ACTIVE_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init() {
        // 注册核心 Tick 驱动
        TickEvent.SERVER_LEVEL_POST.register(GraphEventHandler::onLevelTick);

        // 初始化领域分发器
        EntityDispatcher.register();
        BlockDispatcher.register();
        PlayerDispatcher.register();
        WorldDispatcher.register();
    }

    /**
     * 标记实体为活跃，使其进入每 Tick 的逻辑轮询
     */
    public static void markActive(Entity entity) {
        if (entity != null && !entity.level().isClientSide) {
            ACTIVE_ENTITIES.add(entity);
        }
    }

    private static void onLevelTick(ServerLevel level) {
        // 1. 驱动全局蓝图
        LevelGraphAttachment.get(level).tick(level);

        // 2. 驱动活跃实体的局部蓝图
        if (ACTIVE_ENTITIES.isEmpty()) return;

        Iterator<Entity> iterator = ACTIVE_ENTITIES.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();

            if (entity == null || entity.isRemoved()) {
                iterator.remove();
                continue;
            }

            if (entity.level() != level) continue;

            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment != null && !attachment.getProcesses().isEmpty()) {
                attachment.tick(entity);
            } else {
                iterator.remove();
            }
        }
    }
}