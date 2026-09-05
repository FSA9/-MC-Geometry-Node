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
import java.util.UUID;

/**
 * Creates detached graph-value snapshots. Live entity objects are represented by UUIDs,
 * including when nested inside lists, maps, sets, or arrays.
 */
public final class GraphValueSnapshot {
    private GraphValueSnapshot() {
    }

    public static Object snapshot(Object value) {
        return snapshot(value, null);
    }

    /**
     * Freezes a producer-owned value and records whether readers still need an
     * independent copy of mutable leaves.
     */
    public static FrozenValue freeze(Object value) {
        SnapshotState state = new SnapshotState();
        Object frozen = snapshot(value, state);
        return new FrozenValue(frozen, state.copyOnRead);
    }

    private static Object snapshot(Object value, SnapshotState state) {
        if (value == null) return null;
        if (value instanceof Entity entity) return entity.getUUID();
        if (value instanceof Number number) return GraphNumberNormalizer.normalize(number);
        if (value instanceof ItemStack stack) {
            if (state != null) state.copyOnRead = true;
            return stack.copy();
        }
        if (value instanceof RichTextValue richText) return snapshotRichText(richText, state);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(snapshot(entry.getKey(), state), snapshot(entry.getValue(), state));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(snapshot(item, state));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : set) copy.add(snapshot(item, state));
            return Collections.unmodifiableSet(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(snapshot(Array.get(value, index), state));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /** Creates an independently owned register array and snapshots every stored value. */
    public static Object[] snapshotElements(Object[] values) {
        if (values == null || values.length == 0) {
            return new Object[0];
        }
        Object[] copy = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            copy[index] = snapshot(values[index]);
        }
        return copy;
    }

    /** Creates a mutable state map whose values are detached graph-value snapshots. */
    public static Map<String, Object> snapshotValues(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return copy;
        }
        values.forEach((key, value) -> copy.put(key, snapshot(value)));
        return copy;
    }

    /** Adds detached values to an existing mutable runtime-state map. */
    public static void putSnapshotValues(Map<String, Object> target, Map<String, ?> values) {
        if (target == null || values == null || values.isEmpty()) {
            return;
        }
        values.forEach((key, value) -> target.put(key, snapshot(value)));
    }

    @SuppressWarnings("unchecked")
    private static RichTextValue snapshotRichText(RichTextValue value, SnapshotState state) {
        List<RichTextValue.Segment> segments = new ArrayList<>(value.segments().size());
        for (RichTextValue.Segment segment : value.segments()) {
            Map<String, Object> style = (Map<String, Object>) snapshot(segment.style(), state);
            segments.add(new RichTextValue.Segment(
                    segment.kind(), segment.text(), segment.source(), segment.display(), style));
        }
        return new RichTextValue(value.type(), value.version(), value.plain(), segments);
    }

    /** Whether a frozen value still contains a mutable leaf that must be copied for readers. */
    public static boolean requiresReadCopy(Object value) {
        if (value instanceof ItemStack) return true;
        if (value instanceof RichTextValue richText) {
            for (RichTextValue.Segment segment : richText.segments()) {
                if (requiresReadCopy(segment.style())) return true;
            }
            return false;
        }
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

    public record FrozenValue(Object value, boolean copyOnRead) {
    }

    /** Returns an isolated reader value while sharing fully immutable snapshots. */
    public static Object read(FrozenValue frozenValue) {
        Objects.requireNonNull(frozenValue, "frozenValue");
        return frozenValue.copyOnRead() ? snapshot(frozenValue.value()) : frozenValue.value();
    }

    private static final class SnapshotState {
        private boolean copyOnRead;
    }

    /** Compares detached graph values without relying on mutable-object identity. */
    public static boolean equivalent(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        UUID firstEntityId = entityReferenceId(first);
        UUID secondEntityId = entityReferenceId(second);
        if (firstEntityId != null || secondEntityId != null) {
            return Objects.equals(firstEntityId, secondEntityId);
        }
        if (first.getClass() != second.getClass()) return false;
        if (first instanceof ItemStack left && second instanceof ItemStack right) {
            return ItemStack.matches(left, right);
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

    private static UUID entityReferenceId(Object value) {
        if (value instanceof Entity entity) return entity.getUUID();
        return value instanceof UUID entityId ? entityId : null;
    }
}
