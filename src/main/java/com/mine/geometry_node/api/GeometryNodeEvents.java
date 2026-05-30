package com.mine.geometry_node.api;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 事件派发 facade。
 * 这是给 Addon 使用的稳定入口，内部再桥接到 GraphEngine。
 */
public final class GeometryNodeEvents {
    private static final GeometryEventDispatcher DISPATCHER = new GeometryEventDispatcher() {
        @Override
        public void dispatch(ServerLevel level, @Nullable Entity target, String eventTypeId, EventPayload payload) {
            if (level == null || eventTypeId == null || eventTypeId.isBlank()) {
                return;
            }

            EventPayload safePayload = payload != null ? payload : EventPayload.empty();
            GraphEngine.dispatchEvent(level, target, eventTypeId, thread -> {
                for (var entry : safePayload.values().entrySet()) {
                    Object value = entry.getValue();
                    if (value != null && !VariableRegistry.isSupported(value)) {
                        System.err.println("[GeometryNodeEvents] Skip unsupported event payload value: event=" +
                                eventTypeId + ", key=" + entry.getKey() + ", type=" + value.getClass().getName());
                        continue;
                    }
                    thread.setEventData(entry.getKey(), entry.getValue());
                }
            });
        }
    };

    private GeometryNodeEvents() {
    }

    public static GeometryEventDispatcher dispatcher() {
        return DISPATCHER;
    }

    public static void dispatch(ServerLevel level, @Nullable Entity target, String eventTypeId, EventPayload payload) {
        DISPATCHER.dispatch(level, target, eventTypeId, payload);
    }

    public static void dispatch(Entity target, String eventTypeId, EventPayload payload) {
        DISPATCHER.dispatch(target, eventTypeId, payload);
    }
}
