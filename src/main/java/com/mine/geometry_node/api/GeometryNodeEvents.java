package com.mine.geometry_node.api;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

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

            BlueprintRuntime.INSTANCE.dispatchEvent(level, target, eventTypeId, sanitizePayload(eventTypeId, payload));
        }

        private Map<String, Object> sanitizePayload(String eventTypeId, @Nullable EventPayload payload) {
            EventPayload safePayload = payload != null ? payload : EventPayload.empty();
            if (safePayload.values().isEmpty()) {
                return Map.of();
            }

            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (var entry : safePayload.values().entrySet()) {
                Object value = entry.getValue();
                if (value != null && !GraphValueCodecRegistry.isSupported(value)) {
                    System.err.println("[GeometryNodeEvents] Skip unsupported event payload value: event=" +
                            eventTypeId + ", input=" + entry.getKey() + ", type=" + value.getClass().getName());
                    continue;
                }
                sanitized.put(entry.getKey(), value);
            }
            return sanitized.isEmpty() ? Map.of() : sanitized;
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
