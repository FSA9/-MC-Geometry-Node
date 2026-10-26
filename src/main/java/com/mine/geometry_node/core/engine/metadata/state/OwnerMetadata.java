package com.mine.geometry_node.core.engine.metadata.state;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataNbt;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import java.util.*;

/** Immutable owner value. Encoded data survives saves/copies without a live registry provider. */
public final class OwnerMetadata {
    public static final int MAX_ENCODED_BYTES = 64 * 1024;
    public static final int MAX_ENCODED_DEPTH = 128;
    public static final OwnerMetadata EMPTY = new OwnerMetadata(List.of(), null);
    private final List<String> tags;
    private final Tag data;
    private final int encodedSize;

    private OwnerMetadata(Collection<String> tags, Tag data) {
        this.tags = TagSet.normalize(tags, TagPolicy.EXACT);
        encodedSize = (int) encodedSize(envelope(this.tags, data), new IdentityHashMap<>(), new long[]{0, 0}, 0);
        this.data = data == null ? null : data.copy();
    }

    public static OwnerMetadata of(Collection<String> tags, Map<String, ?> data, HolderLookup.Provider registries) {
        Map<String, Object> values = DataDictionary.snapshot(data, GraphMetadataValuePolicy.of(registries));
        return new OwnerMetadata(tags, values.isEmpty() ? null : GraphValueCodecRegistry.toTagStrict(values, registries));
    }

    public List<String> tags() { return tags; }
    public boolean isEmpty() { return tags.isEmpty() && data == null; }
    public int encodedSize() { return isEmpty() ? 0 : encodedSize; }
    public OwnerMetadata withTags(Collection<String> tags) { return new OwnerMetadata(tags, data); }
    public OwnerMetadata withData(Map<String, ?> values, HolderLookup.Provider registries) { return of(tags, values, registries); }

    public Map<String, Object> data(HolderLookup.Provider registries) {
        if (data == null) return Map.of();
        Object decoded = GraphMetadataNbt.decode(data.copy(), registries);
        if (!(decoded instanceof Map<?, ?> map)) throw new IllegalArgumentException("Invalid owner metadata dictionary");
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (!(key instanceof String text)) throw new IllegalArgumentException("Invalid owner metadata key");
            values.put(text, value);
        });
        return DataDictionary.snapshot(values, GraphMetadataValuePolicy.of(registries));
    }

    public CompoundTag save() {
        return envelope(tags, data == null ? null : data.copy());
    }

    private static CompoundTag envelope(List<String> tags, Tag data) {
        CompoundTag result = new CompoundTag();
        ListTag labels = new ListTag();
        tags.forEach(tag -> labels.add(StringTag.valueOf(tag)));
        result.put("Tags", labels);
        if (data != null) result.put("Data", data);
        return result;
    }

    /** Missing metadata is the v4/legacy representation of an empty owner. */
    public static OwnerMetadata load(CompoundTag encoded) {
        // Inspect untrusted saved/wire NBT before recursive copies or graph codec decoding.
        encodedSize(encoded, new IdentityHashMap<>(), new long[]{0, 0}, 0);
        List<String> tags = new ArrayList<>();
        if (encoded.contains("Tags")) {
            ListTag labels = encoded.getList("Tags").orElseThrow(() -> new IllegalArgumentException("Invalid metadata tags"));
            if (labels.size() > TagSet.MAX_TAGS) throw new IllegalArgumentException("Too many metadata tags");
            for (Tag tag : labels) {
                if (!(tag instanceof StringTag text)) throw new IllegalArgumentException("Invalid metadata tag");
                tags.add(text.value());
            }
        }
        Tag data = encoded.get("Data");
        if (data != null && !(data instanceof CompoundTag)) throw new IllegalArgumentException("Invalid metadata data");
        return new OwnerMetadata(tags, data);
    }

    private static long encodedSize(Tag tag, IdentityHashMap<Tag, Boolean> active, long[] budget, int depth) {
        if (depth > MAX_ENCODED_DEPTH || ++budget[1] > 65_536)
            throw new IllegalArgumentException("Encoded owner metadata exceeds structural limits");
        long own;
        if (tag instanceof CompoundTag) own = 50;
        else if (tag instanceof ListTag list) own = 36L + 4L * list.size();
        else if (tag instanceof ByteArrayTag array) own = 24L + array.size();
        else if (tag instanceof IntArrayTag array) own = 24L + 4L * array.size();
        else if (tag instanceof LongArrayTag array) own = 24L + 8L * array.size();
        else if (tag instanceof StringTag text) own = 38L + stringBudget(text.value());
        else own = tag.sizeInBytes();
        budget[0] += own;
        if (budget[0] > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Encoded owner metadata exceeds 64 KiB budget");
        if (active.put(tag, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic owner metadata NBT");
        long total = own;
        try {
            if (tag instanceof CompoundTag compound) {
                for (String key : compound.keySet()) {
                    // Conservative allocation accounting, including map entry, name and tag type.
                    long keySize = 72L + stringBudget(key);
                    budget[0] += keySize;
                    if (budget[0] > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Encoded owner metadata exceeds 64 KiB budget");
                    total += keySize + encodedSize(Objects.requireNonNull(compound.get(key)), active, budget, depth + 1);
                }
            } else if (tag instanceof ListTag list) {
                for (Tag item : list) total += encodedSize(item, active, budget, depth + 1);
            }
        } finally { active.remove(tag); }
        return total;
    }

    /** NBT uses DataOutput.writeUTF: NUL and each UTF-16 surrogate have modified UTF-8 lengths. */
    private static long stringBudget(String value) {
        long utfBytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            utfBytes += character >= 1 && character <= 0x7f ? 1 : character <= 0x7ff ? 2 : 3;
            if (utfBytes > 65_535) throw new IllegalArgumentException("Owner metadata string exceeds NBT modified UTF-8 limit");
        }
        // Cover both the Java string allocation and NeoForge's modified UTF-8 reader quota.
        return Math.max(2L * value.length(), utfBytes);
    }

    @Override public boolean equals(Object other) {
        return other instanceof OwnerMetadata value && tags.equals(value.tags) && Objects.equals(data, value.data);
    }
    @Override public int hashCode() { return Objects.hash(tags, data); }
}
