package com.mine.geometry_node.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 标准事件派发入口。
 * Addon 不应直接接触 GraphProcess.ExecutionThread。
 */
public interface GeometryEventDispatcher {
    void dispatch(ServerLevel level, @Nullable Entity target, String eventTypeId, EventPayload payload);

    default void dispatch(Entity target, String eventTypeId, EventPayload payload) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        dispatch((ServerLevel) target.level(), target, eventTypeId, payload);
    }
}
