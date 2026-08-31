package com.mine.geometry_node.api;

import com.mine.geometry_node.core.node.definition.port.PortDef;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 事件类型定义。
 * eventId 必须和事件节点的 typeId 保持一致。
 */
public record EventDef(
        String eventId,
        Component displayName,
        EventScope scope,
        List<PortDef> outputs
) {
    public EventDef {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event id cannot be null or blank");
        }
        if (displayName == null) {
            throw new IllegalArgumentException("Event displayName cannot be null");
        }
        if (scope == null) {
            scope = EventScope.LEVEL;
        }
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
