package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** Persistence adapter for scoped-state values. */
final class ScopedStateValueCodec {
    private ScopedStateValueCodec() {
    }

    static Tag encode(Object value, HolderLookup.Provider registries, String location) {
        try {
            Tag encoded = GraphValueCodecRegistry.toTagStrict(value, registries);
            if (encoded != null) return encoded;
        } catch (RuntimeException exception) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard value cannot be encoded: " + location
                            + " [type=" + runtimeType(value) + "]", exception);
        }
        throw new ScopedStateAccessException(
                "Persistent blackboard value cannot be encoded: " + location
                        + " [type=" + runtimeType(value) + "]");
    }

    private static String runtimeType(Object value) {
        return value != null ? value.getClass().getName() : "null";
    }
}
