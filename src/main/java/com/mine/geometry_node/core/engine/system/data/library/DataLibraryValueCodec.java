package com.mine.geometry_node.core.engine.system.data.library;

import com.google.gson.JsonElement;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.core.HolderLookup;

/** JSON representation of values stored inside a Data Library entry. */
public final class DataLibraryValueCodec {
    private DataLibraryValueCodec() {
    }

    /** Metadata uses the graph NBT codec: unlike port FLOAT JSON it retains Double precision
     * and registered values that intentionally have no editable port/JSON representation. */
    public static JsonElement encodeData(java.util.Map<String, Object> data, HolderLookup.Provider registries) {
        var copy = com.mine.geometry_node.core.metadata.DataDictionary.snapshot(data,
                com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(registries));
        return new com.google.gson.JsonPrimitive(canonical(
                com.mine.geometry_node.core.engine.metadata.GraphMetadataNbt.encode(copy, registries)).toString());
    }

    public static java.util.Map<String, Object> decodeData(JsonElement value, HolderLookup.Provider registries) {
        if (value == null || value.isJsonNull()) return java.util.Map.of();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Metadata data must be a graph NBT string");
        try {
            Object decoded = com.mine.geometry_node.core.engine.metadata.GraphMetadataNbt.decode(
                    net.minecraft.nbt.TagParser.parseCompoundFully(value.getAsString()), registries);
            if (!(decoded instanceof java.util.Map<?, ?> map))
                throw new IllegalArgumentException("Metadata data must be a dictionary");
            @SuppressWarnings("unchecked") var typed = (java.util.Map<String, Object>) map;
            return com.mine.geometry_node.core.metadata.DataDictionary.snapshot(typed,
                    com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(registries));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException malformed) {
            throw new IllegalArgumentException("Malformed metadata data", malformed);
        }
    }

    /** NBT compounds (including custom codec values) have dictionary rather than insertion-order identity. */
    private static net.minecraft.nbt.Tag canonical(net.minecraft.nbt.Tag value) {
        if (value instanceof net.minecraft.nbt.CompoundTag compound) {
            var result = new net.minecraft.nbt.CompoundTag();
            compound.keySet().stream().sorted().forEach(key -> result.put(key, canonical(compound.get(key))));
            return result;
        }
        if (value instanceof net.minecraft.nbt.ListTag list) {
            var result = new net.minecraft.nbt.ListTag();
            list.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    public static JsonElement encode(PortType type, Object value, HolderLookup.Provider registries) {
        return GraphValueCodecRegistry.toJson(type, value, registries);
    }

    public static Object decode(PortType type, JsonElement value, HolderLookup.Provider registries) {
        return GraphValueCodecRegistry.fromJson(type, value, registries);
    }
}
