package com.mine.geometry_node.core.engine.blueprint.event;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.BlockDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.AreaTriggerDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.EntityDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.PlayerDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.WorldDispatcher;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * [蓝图事件系统枢纽]
 * 职责：
 * 1. 驱动每 Tick 的蓝图逻辑更新。
 * 2. 初始化各个领域的物理事件分发器。
 */
public final class BlueprintEventHandler {

    private static final Comparator<ScheduledEntity> ENTITY_SCHEDULE_ORDER = Comparator.comparingLong(ScheduledEntity::nextTick);
    private static final Map<ResourceKey<Level>, PriorityQueue<ScheduledEntity>> ACTIVE_ENTITY_QUEUES = new HashMap<>();
    private static final Map<UUID, ScheduledEntity> ACTIVE_ENTITY_SCHEDULES = new HashMap<>();

    private record ScheduledEntity(UUID entityId, ResourceKey<Level> levelKey, WeakReference<Entity> entityRef, long nextTick) {}

    private BlueprintEventHandler() {
    }

    public static void init() {
        // 初始化领域分发器
        EntityDispatcher.register();
        BlockDispatcher.register();
        PlayerDispatcher.register();
        WorldDispatcher.register();
    }

    /**
     * 标记实体存在待唤醒任务，使其按下一次唤醒时间进入调度队列。
     */
    public static void markActive(Entity entity) {
        if (entity != null && !entity.level().isClientSide()) {
            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment != null) {
                attachment.attachOwner(entity);
            }
            scheduleEntityIfNeeded(entity, attachment);
        }
    }

    public static void tickLevel(ServerLevel level) {
        // 1. 驱动全局蓝图
        LevelGraphAttachment.get(level).tick(level);
        AreaTriggerDispatcher.tickLevel(level);
        // 2. 驱动到期实体的局部蓝图
        tickScheduledEntities(level);
    }

    public static void shutdown() {
        ACTIVE_ENTITY_QUEUES.clear();
        ACTIVE_ENTITY_SCHEDULES.clear();
    }

    private static void tickScheduledEntities(ServerLevel level) {
        PriorityQueue<ScheduledEntity> queue = ACTIVE_ENTITY_QUEUES.get(level.dimension());
        if (queue == null || queue.isEmpty()) return;

        long currentTime = level.getGameTime();
        while (!queue.isEmpty()) {
            ScheduledEntity scheduled = queue.peek();
            if (ACTIVE_ENTITY_SCHEDULES.get(scheduled.entityId()) != scheduled) {
                queue.poll();
                continue;
            }
            if (scheduled.nextTick() > currentTime) {
                return;
            }

            queue.poll();
            ACTIVE_ENTITY_SCHEDULES.remove(scheduled.entityId(), scheduled);

            Entity entity = scheduled.entityRef().get();
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (entity.level() != level) {
                markActive(entity);
                continue;
            }

            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment == null) {
                continue;
            }

            attachment.tick(entity);
            scheduleEntityIfNeeded(entity, attachment);
        }

        if (queue.isEmpty()) {
            ACTIVE_ENTITY_QUEUES.remove(level.dimension(), queue);
        }
    }

    private static void scheduleEntityIfNeeded(Entity entity, EntityGraphAttachment attachment) {
        UUID entityId = entity.getUUID();
        if (entity.isRemoved() || entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            ACTIVE_ENTITY_SCHEDULES.remove(entityId);
            return;
        }

        long nextTick = attachment != null ? attachment.getNextScheduledTick() : Long.MAX_VALUE;
        if (nextTick == Long.MAX_VALUE) {
            ACTIVE_ENTITY_SCHEDULES.remove(entityId);
            return;
        }

        ResourceKey<Level> levelKey = level.dimension();
        ScheduledEntity current = ACTIVE_ENTITY_SCHEDULES.get(entityId);
        if (current != null
                && current.levelKey().equals(levelKey)
                && current.nextTick() == nextTick
                && current.entityRef().get() == entity) {
            return;
        }

        ScheduledEntity scheduled = new ScheduledEntity(entityId, levelKey, new WeakReference<>(entity), nextTick);
        ACTIVE_ENTITY_SCHEDULES.put(entityId, scheduled);
        ACTIVE_ENTITY_QUEUES.computeIfAbsent(levelKey, ignored -> new PriorityQueue<>(ENTITY_SCHEDULE_ORDER)).offer(scheduled);
    }
}
