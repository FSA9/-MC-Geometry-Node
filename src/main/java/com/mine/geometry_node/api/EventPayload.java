package com.mine.geometry_node.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件数据包。
 * input 必须与事件节点输出端口 ID 保持一致。
 */
public final class EventPayload {
    private static final EventPayload EMPTY = new EventPayload(Map.of());

    private final Map<String, Object> values;

    private EventPayload(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static EventPayload empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> values() {
        return values;
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Event payload input cannot be null or blank");
            }
            values.put(key, value);
            return this;
        }

        public EventPayload build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new EventPayload(values);
        }
    }
}
