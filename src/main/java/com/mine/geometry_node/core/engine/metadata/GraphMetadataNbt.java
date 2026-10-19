package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/** NBT boundary helpers; dictionary ownership and registry choice remain with the caller. */
public final class GraphMetadataNbt {
    private GraphMetadataNbt() {}
    public static Tag encode(Object value, HolderLookup.Provider registries) {
        return GraphValueCodecRegistry.toTagStrict(GraphMetadataValuePolicy.scoped(registries).snapshot(value), registries);
    }
    /** Rejects unresolved nested codec values instead of silently substituting null. */
    public static Object decode(Tag encoded, HolderLookup.Provider registries) {
        validateEncoded(encoded, new IdentityHashMap<>(), new int[]{0}, 0);
        Object decoded = GraphValueCodecRegistry.fromTag(encoded.copy(), registries);
        validateDecoded(encoded, decoded, 0);
        return decoded;
    }
    private static void validateEncoded(Tag encoded, IdentityHashMap<Tag, Boolean> active, int[] count, int depth) {
        if (encoded == null || depth > 128 || ++count[0] > 1_048_576)
            throw new IllegalArgumentException("Malformed or excessive graph metadata NBT");
        if (active.put(encoded, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic graph metadata NBT");
        try {
            if (encoded instanceof CompoundTag compound) {
                for (String key : compound.keySet()) validateEncoded(compound.get(key), active, count, depth + 1);
            } else if (encoded instanceof ListTag list) {
                for (Tag item : list) validateEncoded(item, active, count, depth + 1);
            }
        } finally { active.remove(encoded); }
    }
    private static void validateDecoded(Tag encoded, Object decoded, int depth) {
        if (depth > 128) throw new IllegalArgumentException("Graph metadata NBT exceeds depth limit");
        String type = encoded instanceof CompoundTag compound ? compound.getStringOr("_gn_type", "") : "";
        if (decoded == null) {
            if ("_gn_null".equals(type)) return;
            throw new IllegalArgumentException("Unresolved graph metadata codec value");
        }
        if (encoded instanceof CompoundTag compound && "_gn_dict".equals(type)) {
            if (!(compound.get("data") instanceof CompoundTag data) || !(decoded instanceof Map<?, ?> map))
                throw new IllegalArgumentException("Malformed graph metadata dictionary");
            for (String key : data.keySet()) validateDecoded(data.get(key), map.get(key), depth + 1);
        } else if (encoded instanceof CompoundTag compound && "_gn_list".equals(type)) {
            if (!(compound.get("data") instanceof ListTag data) || !(decoded instanceof List<?> list) || data.size() != list.size())
                throw new IllegalArgumentException("Malformed graph metadata list");
            for (int index = 0; index < data.size(); index++)
                validateDecoded(data.getCompoundOrEmpty(index).get("v"), list.get(index), depth + 1);
        } else if (encoded instanceof CompoundTag compound && "rich_text".equals(type)) {
            if (!(compound.get("data") instanceof CompoundTag data) || !(decoded instanceof RichTextValue text)
                    || !(data.get("segments") instanceof ListTag segments) || segments.size() != text.segments().size())
                throw new IllegalArgumentException("Malformed graph metadata rich text");
            for (int index = 0; index < segments.size(); index++)
                validateDecoded(segments.getCompoundOrEmpty(index).get("style"), text.segments().get(index).style(), depth + 1);
        }
    }
}
