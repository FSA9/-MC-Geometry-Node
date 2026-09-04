package com.mine.geometry_node.api;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件派发 facade。
 * 这是给 Addon 使用的稳定入口，内部再桥接到 BlueprintEngine。
 */
public final class GeometryNodeEvents {
    private GeometryNodeEvents() {
    }

    public static void dispatch(ServerLevel level, @Nullable Entity target, String eventTypeId, EventPayload payload) {
        if (level == null || eventTypeId == null || eventTypeId.isBlank()) {
            return;
        }

        String canonicalEventType = NodeDef.canonicalTypeId(eventTypeId);
        BlueprintRuntime.INSTANCE.dispatchEvent(
                level, target, canonicalEventType, sanitizePayload(canonicalEventType, payload));
    }

    public static void dispatch(Entity target, String eventTypeId, EventPayload payload) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        dispatch((ServerLevel) target.level(), target, eventTypeId, payload);
    }

    private static Map<String, Object> sanitizePayload(String eventTypeId, @Nullable EventPayload payload) {
        EventPayload safePayload = payload != null ? payload : EventPayload.empty();
        Map<String, Object> values = safePayload.values();
        if (values.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = null;
        for (var entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value != null && !GraphValueCodecRegistry.isSupported(value)) {
                System.err.println("[GeometryNodeEvents] Skip unsupported event payload value: event=" +
                        eventTypeId + ", input=" + entry.getKey() + ", type=" + value.getClass().getName());
                if (sanitized == null) {
                    sanitized = new LinkedHashMap<>(values);
                }
                sanitized.remove(entry.getKey());
            }
        }
        return sanitized == null ? values : (sanitized.isEmpty() ? Map.of() : sanitized);
    }
}
