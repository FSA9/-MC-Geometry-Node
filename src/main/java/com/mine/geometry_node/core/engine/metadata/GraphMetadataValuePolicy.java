package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.metadata.DataValuePolicy;
import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.util.*;

/** Graph value semantics at the neutral metadata boundary; no graph types enter the kernel. */
public final class GraphMetadataValuePolicy implements DataValuePolicy {
    private final HolderLookup.Provider registries;
    private final boolean scoped;

    private GraphMetadataValuePolicy(HolderLookup.Provider registries, boolean scoped) {
        this.registries = registries;
        this.scoped = scoped;
    }

    /** Null registries support registry-independent codecs and detached ItemStack reads. */
    public static DataValuePolicy of(HolderLookup.Provider registries) {
        return new GraphMetadataValuePolicy(registries, false);
    }

    /** Retains historical scoped keys and capacity, including empty/control-containing keys. */
    public static DataValuePolicy scoped(HolderLookup.Provider registries) {
        return new GraphMetadataValuePolicy(registries, true);
    }

    public static DataValuePolicy runtime() { return scoped(null); }

    @Override public String validateKey(String key) {
        return scoped ? Objects.requireNonNull(key, "key") : DataValuePolicy.super.validateKey(key);
    }
    @Override public int maxEntries() { return scoped ? 65_536 : DataValuePolicy.super.maxEntries(); }
    @Override public DataDictionary.ValidationLimits validationLimits() {
        return scoped ? new DataDictionary.ValidationLimits(128, 1_048_576, Integer.MAX_VALUE)
                : DataValuePolicy.super.validationLimits();
    }

    @Override public Object snapshot(Object value) {
        // Check cycles, depth and graph size before any recursive copying or codec invocation.
        DataDictionary.validateStructure(value, this);
        return copy(value, new IdentityHashMap<>(), new int[]{0}, 0);
    }

    private Object copy(Object value, IdentityHashMap<Object, Boolean> active, int[] count, int depth) {
        DataDictionary.ValidationLimits limits = validationLimits();
        if (++count[0] > limits.maxNodes() || depth > limits.maxDepth())
            throw new IllegalArgumentException("Graph metadata exceeds structural limits");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof UUID) return value;
        if (value instanceof Entity entity) return entity.getUUID();
        if (value instanceof Number number) {
            Number normalized = GraphNumberNormalizer.normalize(number);
            DataDictionary.validateStructure(normalized, this);
            return normalized;
        }
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof BlockPos position) return position.immutable();
        if (value instanceof Vec3 || value instanceof BlockState || value instanceof Item) return value;
        if (active.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic graph metadata value");
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Graph dictionary keys must be strings");
                    validateKey(key);
                    result.put(key, copy(entry.getValue(), active, count, depth + 1));
                }
                return Collections.unmodifiableMap(result);
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) result.add(copy(item, active, count, depth + 1));
                return Collections.unmodifiableList(result);
            }
            if (value instanceof Set<?> set) {
                Set<Object> result = new LinkedHashSet<>();
                for (Object item : set) result.add(copy(item, active, count, depth + 1));
                return Collections.unmodifiableSet(result);
            }
            if (value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                for (int index = 0; index < Array.getLength(value); index++)
                    result.add(copy(Array.get(value, index), active, count, depth + 1));
                return Collections.unmodifiableList(result);
            }
            if (value instanceof RichTextValue richText) {
                DataDictionary.validateStructure(richText.type(), this);
                DataDictionary.validateStructure(richText.plain(), this);
                List<RichTextValue.Segment> segments = new ArrayList<>();
                for (RichTextValue.Segment segment : richText.segments()) {
                    DataDictionary.validateStructure(segment.kind(), this);
                    DataDictionary.validateStructure(segment.text(), this);
                    DataDictionary.validateStructure(segment.source(), this);
                    DataDictionary.validateStructure(segment.display(), this);
                    DataDictionary.validateStructure(segment.style(), this);
                    @SuppressWarnings("unchecked") Map<String, Object> style =
                            (Map<String, Object>) copy(segment.style(), active, count, depth + 1);
                    segments.add(new RichTextValue.Segment(segment.kind(), segment.text(), segment.source(), segment.display(), style));
                }
                return new RichTextValue(richText.type(), richText.version(), richText.plain(), segments);
            }
            // A registered codec is the copy contract for otherwise unknown mutable Java values.
            Tag encoded = GraphValueCodecRegistry.toTagStrict(value, registries);
            Object decoded = GraphMetadataNbt.decode(encoded, registries);
            if (decoded == null || decoded == value)
                throw new IllegalArgumentException("Graph metadata codec must reconstruct an independent value: " + value.getClass().getName());
            DataDictionary.validateStructure(decoded, this);
            return decoded;
        } finally { active.remove(value); }
    }

    @Override public boolean equivalent(Object first, Object second) {
        if (GraphValueSnapshot.equivalent(first, second)) return true;
        if (first == null || second == null) return false;
        if (first instanceof Map<?, ?> left && second instanceof Map<?, ?> right) {
            if (!left.keySet().equals(right.keySet())) return false;
            return left.entrySet().stream().allMatch(e -> equivalent(e.getValue(), right.get(e.getKey())));
        }
        if (first instanceof List<?> left && second instanceof List<?> right) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!equivalent(left.get(i), right.get(i))) return false;
            return true;
        }
        try {
            return GraphValueCodecRegistry.toTagStrict(first, registries)
                    .equals(GraphValueCodecRegistry.toTagStrict(second, registries));
        } catch (RuntimeException unsupported) {
            return false;
        }
    }
}
