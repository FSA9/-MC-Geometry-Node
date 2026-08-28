package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** Snapshot and persistence adapter for scoped-state values. */
public final class ScopedStateValueCodec {
    private ScopedStateValueCodec() {
    }

    public static Tag encode(Object value, HolderLookup.Provider registries, String location) {
        try {
            Tag encoded = GraphValueCodecRegistry.toTagStrict(value, registries);
            if (encoded != null) return encoded;
        } catch (RuntimeException exception) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value cannot be encoded: " + location);
        }
        throw new ScopedStateAccessException(
                "Persistent blackboard value cannot be encoded: " + location);
    }
}
