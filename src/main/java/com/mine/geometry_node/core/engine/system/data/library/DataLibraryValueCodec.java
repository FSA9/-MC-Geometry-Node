package com.mine.geometry_node.core.engine.system.data.library;

import com.google.gson.JsonElement;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.core.HolderLookup;

/** JSON representation of values stored inside a Data Library entry. */
public final class DataLibraryValueCodec {
    private DataLibraryValueCodec() {
    }

    public static JsonElement encode(PortType type, Object value, HolderLookup.Provider registries) {
        return GraphValueCodecRegistry.toJson(type, value, registries);
    }

    public static Object decode(PortType type, JsonElement value, HolderLookup.Provider registries) {
        return GraphValueCodecRegistry.fromJson(type, value, registries);
    }
}
