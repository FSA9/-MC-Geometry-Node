package com.mine.geometry_node.core.engine.blueprint.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small helper for dispatcher event payloads.
 */
public final class GraphEventData {
    private GraphEventData() {
    }

    public static Map<String, Object> of(Object... entries) {
        if (entries == null || entries.length == 0) {
            return Map.of();
        }
        if ((entries.length & 1) != 0) {
            throw new IllegalArgumentException("Graph event data must be key/value pairs");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            Object key = entries[i];
            if (!(key instanceof String portId) || portId.isBlank()) {
                throw new IllegalArgumentException("Graph event data key must be a non-empty string");
            }
            Object value = entries[i + 1];
            if (value != null) {
                data.put(portId, value);
            }
        }
        return data.isEmpty() ? Map.of() : data;
    }
}
