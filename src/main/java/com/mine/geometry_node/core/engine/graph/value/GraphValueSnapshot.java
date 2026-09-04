package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Creates detached snapshots of mutable graph values. */
public final class GraphValueSnapshot {
    private GraphValueSnapshot() {
    }

    public static Object snapshot(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return GraphNumberNormalizer.normalize(number);
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof RichTextValue richText) return snapshotRichText(richText);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(snapshot(entry.getKey()), snapshot(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(snapshot(item));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : set) copy.add(snapshot(item));
            return Collections.unmodifiableSet(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(snapshot(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static RichTextValue snapshotRichText(RichTextValue value) {
        List<RichTextValue.Segment> segments = new ArrayList<>(value.segments().size());
        for (RichTextValue.Segment segment : value.segments()) {
            Map<String, Object> style = (Map<String, Object>) snapshot(segment.style());
            segments.add(new RichTextValue.Segment(
                    segment.kind(), segment.text(), segment.source(), segment.display(), style));
        }
        return new RichTextValue(value.type(), value.version(), value.plain(), segments);
    }

    /** Whether a frozen value still contains a mutable leaf that must be copied for readers. */
    public static boolean requiresReadCopy(Object value) {
        if (value instanceof ItemStack) return true;
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry ->
                    requiresReadCopy(entry.getKey()) || requiresReadCopy(entry.getValue()));
        }
        if (value instanceof Iterable<?> values) {
            for (Object entry : values) {
                if (requiresReadCopy(entry)) return true;
            }
        }
        if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                if (requiresReadCopy(Array.get(value, index))) return true;
            }
        }
        return false;
    }

    /** Compares detached graph values without relying on mutable-object identity. */
    public static boolean equivalent(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null || first.getClass() != second.getClass()) return false;
        if (first instanceof ItemStack left && second instanceof ItemStack right) {
            return ItemStack.matches(left, right);
        }
        if (first instanceof Entity left && second instanceof Entity right) {
            return left.getUUID().equals(right.getUUID());
        }
        if (first instanceof List<?> left && second instanceof List<?> right) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                if (!equivalent(left.get(index), right.get(index))) return false;
            }
            return true;
        }
        if (first instanceof Map<?, ?> left && second instanceof Map<?, ?> right) {
            if (left.size() != right.size()) return false;
            for (Map.Entry<?, ?> leftEntry : left.entrySet()) {
                if (!right.containsKey(leftEntry.getKey())
                        || !equivalent(leftEntry.getValue(), right.get(leftEntry.getKey()))) return false;
            }
            return true;
        }
        if (first instanceof Set<?> left && second instanceof Set<?> right) {
            return left.equals(right);
        }
        if (first.getClass().isArray()) {
            int length = Array.getLength(first);
            if (length != Array.getLength(second)) return false;
            for (int index = 0; index < length; index++) {
                if (!equivalent(Array.get(first, index), Array.get(second, index))) return false;
            }
            return true;
        }
        return Objects.equals(first, second);
    }
}
