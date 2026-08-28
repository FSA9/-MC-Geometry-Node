package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict wrapper around the graph codec: persistent containers may not silently drop members. */
public final class ScopedStateValueCodec {
    private ScopedStateValueCodec() {
    }

    public static void validate(Object value, String location) {
        requireLossless(value, location, 0, new IdentityHashMap<>());
    }

    public static Tag encode(Object value, HolderLookup.Provider registries, String location) {
        validate(value, location);
        Object comparable = value instanceof Entity entity ? entity.getUUID() : value;
        Tag encoded;
        try {
            encoded = GraphValueCodecRegistry.toTag(value, registries);
        } catch (RuntimeException exception) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value cannot be encoded: " + location);
        }
        if (encoded == null) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value cannot be encoded: " + location);
        }
        Object decoded;
        try {
            decoded = GraphValueCodecRegistry.fromTag(encoded, registries);
        } catch (RuntimeException exception) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value cannot be decoded after encoding: " + location);
        }
        if (decoded == null || !equivalent(comparable, decoded)) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value does not round-trip losslessly: " + location);
        }
        return encoded;
    }

    private static boolean equivalent(Object expected, Object actual) {
        if (expected instanceof ItemStack expectedStack) {
            return actual instanceof ItemStack actualStack
                    && ItemStack.matches(expectedStack, actualStack);
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList)
                    || expectedList.size() != actualList.size()) return false;
            for (int index = 0; index < expectedList.size(); index++) {
                if (!equivalent(expectedList.get(index), actualList.get(index))) return false;
            }
            return true;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)
                    || expectedMap.size() != actualMap.size()) return false;
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                Object key = entry.getKey();
                if (!actualMap.containsKey(key)
                        || !equivalent(entry.getValue(), actualMap.get(key))) return false;
            }
            return true;
        }
        return Objects.equals(expected, actual);
    }

    public static Object freeze(Object value) {
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
        return value;
    }

    private static void requireLossless(Object value, String location, int depth,
                                        IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || depth > 16 || !GraphValueCodecRegistry.isSupported(value)) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value is not losslessly serializable: " + location);
        }
        if (value instanceof List<?> list) {
            enterContainer(value, location, visiting);
            try {
                for (Object item : list) {
                    requireLossless(item, location, depth + 1, visiting);
                }
            } finally {
                visiting.remove(value);
            }
        } else if (value instanceof Map<?, ?> map) {
            enterContainer(value, location, visiting);
            try {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String) || entry.getValue() == null) {
                        throw new ScopedStateAccessException(
                                "Persistent blackboard map is not losslessly serializable: " + location);
                    }
                    requireLossless(entry.getValue(), location, depth + 1, visiting);
                }
            } finally {
                visiting.remove(value);
            }
        }
    }

    private static void enterContainer(Object value, String location,
                                       IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value contains a cycle: " + location);
        }
    }
}
