package com.mine.geometry_node.core.metadata;

import java.util.*;

/** Supplies application value codecs without coupling the dictionary to an engine or UI. */
@FunctionalInterface
public interface DataValuePolicy {
    /** Validates and copies a value, including all mutable leaves. Unknown types must fail. */
    Object snapshot(Object value);

    default boolean equivalent(Object first, Object second) { return Objects.equals(first, second); }

    /** Owners may retain historical key rules without weakening other dictionary users. */
    default String validateKey(String key) { return DataDictionary.requireKey(key); }
    default int maxEntries() { return DataDictionary.MAX_ENTRIES; }
    default DataDictionary.ValidationLimits validationLimits() { return DataDictionary.ValidationLimits.DEFAULT; }

    /** Plain dictionaries/lists and immutable scalar values; no arbitrary Java objects. */
    DataValuePolicy PLAIN = new DataValuePolicy() {
        @Override public Object snapshot(Object value) {
            DataDictionary.validateStructure(value);
            return copy(value);
        }
        private Object copy(Object value) {
            if (value == null || value instanceof String || value instanceof Boolean
                    || value instanceof Integer || value instanceof Long || value instanceof Short
                    || value instanceof Byte || value instanceof Float || value instanceof Double
                    || value instanceof UUID) return value;
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>();
                list.forEach(item -> result.add(copy(item)));
                return Collections.unmodifiableList(result);
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, item) -> result.put((String) key, copy(item)));
                return Collections.unmodifiableMap(result);
            }
            throw new IllegalArgumentException("Unregistered data value: " + value.getClass().getName());
        }
    };
}
