package com.mine.geometry_node.core.metadata;

import java.lang.reflect.Array;
import java.util.*;

/** Shared detached dictionary snapshots and bounded value validation. */
public final class DataDictionary {
    public static final int MAX_ENTRIES = 1024, MAX_KEY_LENGTH = 256;
    public static final int MAX_DEPTH = 32, MAX_NODES = 16384, MAX_TEXT_LENGTH = 65536;
    private DataDictionary() {}

    public record ValidationLimits(int maxDepth, int maxNodes, int maxTextLength) {
        public static final ValidationLimits DEFAULT = new ValidationLimits(MAX_DEPTH, MAX_NODES, MAX_TEXT_LENGTH);
        public ValidationLimits {
            if (maxDepth < 0 || maxNodes < 1 || maxTextLength < 0)
                throw new IllegalArgumentException("Invalid dictionary validation limits");
        }
    }

    public static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.length() > MAX_KEY_LENGTH || key.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid data key");
        return key;
    }

    public static Map<String, Object> snapshot(Map<String, ?> values, DataValuePolicy policy) {
        Objects.requireNonNull(values); Objects.requireNonNull(policy);
        if (values.size() > policy.maxEntries()) throw new IllegalArgumentException("Too many data entries");
        validateStructure(values, policy);
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            policy.validateKey(key); Objects.requireNonNull(value, "Data value; remove the key explicitly instead");
            copy.put(key, Objects.requireNonNull(policy.snapshot(value), "snapshot"));
        });
        return Collections.unmodifiableMap(copy);
    }

    public static boolean equivalent(Map<String, ?> first, Map<String, ?> second, DataValuePolicy policy) {
        if (!first.keySet().equals(second.keySet())) return false;
        return first.entrySet().stream().allMatch(e -> policy.equivalent(e.getValue(), second.get(e.getKey())));
    }

    public static void validateStructure(Object value) {
        validateStructure(value, DataValuePolicy.PLAIN);
    }
    public static void validateStructure(Object value, DataValuePolicy policy) {
        Objects.requireNonNull(policy, "policy");
        validate(value, new IdentityHashMap<>(), new int[]{0}, 0, policy, policy.validationLimits());
    }
    private static void validate(Object value, IdentityHashMap<Object, Boolean> active, int[] count, int depth,
                                 DataValuePolicy policy, ValidationLimits limits) {
        if (++count[0] > limits.maxNodes() || depth > limits.maxDepth()) throw new IllegalArgumentException("Data value exceeds structural limits");
        if (value instanceof String text && text.length() > limits.maxTextLength()) throw new IllegalArgumentException("Data text too long");
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) throw new IllegalArgumentException("Non-finite data value");
        boolean container = value instanceof Map<?, ?> || value instanceof Iterable<?> || value != null && value.getClass().isArray();
        if (!container) return;
        if (active.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic data value");
        try {
            if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Data dictionary keys must be strings");
                    policy.validateKey(key);
                    validate(entry.getValue(), active, count, depth + 1, policy, limits);
                }
            } else if (value instanceof Iterable<?> items) {
                for (Object item : items) validate(item, active, count, depth + 1, policy, limits);
            } else {
                for (int i = 0; i < Array.getLength(value); i++) validate(Array.get(value, i), active, count, depth + 1, policy, limits);
            }
        } finally { active.remove(value); }
    }
}
