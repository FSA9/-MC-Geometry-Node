package com.mine.geometry_node.core.engine.graph.value;

import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;

/** JSON codec used by Data Library persistence for a registered graph port type. */
public interface GraphValueJsonCodec {
    JsonElement encode(Object value, HolderLookup.Provider registries);

    Object decode(JsonElement value, HolderLookup.Provider registries);
}
