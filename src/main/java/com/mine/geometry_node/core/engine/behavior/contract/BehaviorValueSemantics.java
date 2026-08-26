package com.mine.geometry_node.core.engine.behavior.contract;

import com.mine.geometry_node.core.node.port.PortType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single type-validation, normalization and freezing policy for compiled inputs and blackboards. */
public final class BehaviorValueSemantics {
    private BehaviorValueSemantics() {
    }

    public static boolean matches(Object value, PortType type) {
        if (value == null || type == null) return false;
        PortType inferred = PortType.getTypeOf(value);
        if (!(value instanceof Number) && inferred != PortType.ANY && inferred == type) return true;
        return switch (type) {
            case INTEGER -> value instanceof Number number && integral(number)
                    && number.doubleValue() >= Integer.MIN_VALUE && number.doubleValue() <= Integer.MAX_VALUE;
            case LONG -> value instanceof Number number && integral(number);
            case FLOAT -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case STRING, PATH -> value instanceof String;
            case XYZ -> value instanceof List<?> list && list.size() == 3
                    && list.stream().allMatch(Number.class::isInstance);
            case LIST -> value instanceof List<?>;
            case DICT, SHOP -> value instanceof Map<?, ?>;
            case ANY -> true;
            case EXECUTION, BEHAVIOR_STRUCTURE -> false;
            default -> value instanceof String || value instanceof Number
                    || value instanceof Map<?, ?> || value instanceof List<?>;
        };
    }

    public static Object freezeAs(Object value, PortType type) {
        if (!matches(value, type)) {
            throw new IllegalArgumentException("Value does not match behavior type " + type);
        }
        return switch (type) {
            case INTEGER -> ((Number) value).intValue();
            case LONG -> ((Number) value).longValue();
            case FLOAT -> ((Number) value).floatValue();
            case XYZ -> value instanceof List<?> list ? list.stream()
                    .map(component -> ((Number) component).floatValue()).toList() : freeze(value);
            default -> freeze(value);
        };
    }

    private static boolean integral(Number number) {
        double value = number.doubleValue();
        return Double.isFinite(value) && value == Math.rint(value);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> frozen = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    frozen.put(key, freeze(entry.getValue()));
                }
            }
            return Collections.unmodifiableMap(frozen);
        }
        if (value instanceof List<?> list) {
            List<Object> frozen = new ArrayList<>(list.size());
            for (Object item : list) if (item != null) frozen.add(freeze(item));
            return Collections.unmodifiableList(frozen);
        }
        if (value instanceof Object[] array) {
            List<Object> frozen = new ArrayList<>(array.length);
            for (Object item : array) if (item != null) frozen.add(freeze(item));
            return Collections.unmodifiableList(frozen);
        }
        return value;
    }
}
