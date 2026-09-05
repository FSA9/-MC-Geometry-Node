package com.mine.geometry_node.core.engine.blueprint.event;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.BlockDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.AreaTriggerDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.EntityDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.PlayerDispatcher;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.WorldDispatcher;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldTickService;
import com.mine.geometry_node.core.engine.graph.scheduling.DueTickScheduler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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

    private final Map<MinecraftServer, ServerSchedule> servers = new WeakHashMap<>();
    private final AreaTriggerDispatcher areaTriggers = new AreaTriggerDispatcher();
    private boolean registered;

    private record ScheduledEntity(UUID entityId, ResourceKey<Level> levelKey, WeakReference<Entity> entityRef, long nextTick) {}

    public BlueprintEventHandler() {
    }

    public void init() {
        if (registered) return;
        registered = true;
        // 初始化领域分发器
        EntityDispatcher.register();
        BlockDispatcher.register();
        PlayerDispatcher.register();
        WorldDispatcher.register();
    }

    /**
     * 标记实体存在待唤醒任务，使其按下一次唤醒时间进入调度队列。
     */
    public void markActive(Entity entity) {
        if (entity != null && !entity.level().isClientSide()) {
            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment != null) {
                attachment.attachOwner(entity);
            }
            scheduleEntityIfNeeded(entity, attachment);
        }
    }

    public void tickLevel(ServerLevel level) {
        // 1. 驱动全局蓝图
        LevelGraphAttachment.get(level).tick(level);
        ForceFieldTickService.INSTANCE.tickLevel(level);
        AreaResourceStore.INSTANCE.tickDebug(level);
        areaTriggers.tickLevel(level);
        // 2. 驱动到期实体的局部蓝图
        tickScheduledEntities(level);
    }

    public void tickEntityAreas(ServerLevel level, Entity owner, EntityGraphAttachment attachment, long currentTick) {
        areaTriggers.tickEntity(level, owner, attachment, currentTick);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
        areaTriggers.shutdown(server);
        AreaResourceStore.INSTANCE.shutdown(server);
        ForceFieldResourceStore.INSTANCE.shutdown(server);
    }

    public void forgetEntity(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return;
        ServerSchedule schedule = servers.get(level.getServer());
        if (schedule == null) return;
        UUID entityId = entity.getUUID();
        ResourceKey<Level> scheduledDimension = schedule.scheduledDimensions.get(entityId);
        if (!level.dimension().equals(scheduledDimension)) return;
        DueTickScheduler<UUID, ScheduledEntity> scheduler = schedule.activeEntityQueues.get(scheduledDimension);
        if (scheduler != null) {
            scheduler.cancel(entityId);
            if (scheduler.isEmpty()) schedule.activeEntityQueues.remove(scheduledDimension, scheduler);
        }
        schedule.scheduledDimensions.remove(entityId, scheduledDimension);
    }

    public void forgetLevel(ServerLevel level) {
        if (level == null) return;
        ServerSchedule schedule = servers.get(level.getServer());
        if (schedule == null) return;
        ResourceKey<Level> dimension = level.dimension();
        schedule.activeEntityQueues.remove(dimension);
        schedule.scheduledDimensions.entrySet().removeIf(entry -> dimension.equals(entry.getValue()));
    }

    private void tickScheduledEntities(ServerLevel level) {
        ServerSchedule schedule = servers.computeIfAbsent(level.getServer(), ignored -> new ServerSchedule());
        DueTickScheduler<UUID, ScheduledEntity> queue = schedule.activeEntityQueues.get(level.dimension());
        if (queue == null || queue.isEmpty()) return;

        long currentTime = level.getGameTime();
        DueTickScheduler.Scheduled<UUID, ScheduledEntity> due;
        while ((due = queue.pollDue(currentTime)) != null) {
            ScheduledEntity scheduled = due.value();
            schedule.scheduledDimensions.remove(scheduled.entityId(), level.dimension());

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
            schedule.activeEntityQueues.remove(level.dimension(), queue);
        }
    }

    private void scheduleEntityIfNeeded(Entity entity, EntityGraphAttachment attachment) {
        UUID entityId = entity.getUUID();
        if (entity.isRemoved() || entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            if (entity.level() instanceof ServerLevel level) {
                forgetEntity(level, entity);
            }
            return;
        }
        ServerSchedule schedule = servers.computeIfAbsent(level.getServer(), ignored -> new ServerSchedule());

        long nextTick = attachment != null ? attachment.getNextScheduledTick() : Long.MAX_VALUE;
        if (nextTick == Long.MAX_VALUE) {
            ResourceKey<Level> previousDimension = schedule.scheduledDimensions.remove(entityId);
            DueTickScheduler<UUID, ScheduledEntity> previous =
                    previousDimension != null ? schedule.activeEntityQueues.get(previousDimension) : null;
            if (previous != null) {
                previous.cancel(entityId);
                if (previous.isEmpty()) schedule.activeEntityQueues.remove(previousDimension, previous);
            }
            return;
        }

        ResourceKey<Level> levelKey = level.dimension();
        ResourceKey<Level> previousDimension = schedule.scheduledDimensions.put(entityId, levelKey);
        if (previousDimension != null && !previousDimension.equals(levelKey)) {
            DueTickScheduler<UUID, ScheduledEntity> previous = schedule.activeEntityQueues.get(previousDimension);
            if (previous != null) {
                previous.cancel(entityId);
                if (previous.isEmpty()) schedule.activeEntityQueues.remove(previousDimension, previous);
            }
        }

        ScheduledEntity scheduled = new ScheduledEntity(entityId, levelKey, new WeakReference<>(entity), nextTick);
        schedule.activeEntityQueues.computeIfAbsent(levelKey, ignored -> new DueTickScheduler<>())
                .schedule(entityId, scheduled, nextTick);
    }

    private static final class ServerSchedule {
        private final Map<ResourceKey<Level>, DueTickScheduler<UUID, ScheduledEntity>> activeEntityQueues =
                new HashMap<>();
        private final Map<UUID, ResourceKey<Level>> scheduledDimensions = new HashMap<>();
    }
}
