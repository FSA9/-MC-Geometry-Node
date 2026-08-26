package com.mine.geometry_node.core.engine.blueprint.variables;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Graph values are shared by all graph families. Use {@link GraphValueCodecRegistry}.
 */
@Deprecated
public final class VariableRegistry {
    private VariableRegistry() {
    }

    public static <T> void register(VariableSerializer<T> serializer) {
        GraphValueCodecRegistry.register(serializer);
    }

    @Nullable
    public static Tag toTag(Object value, HolderLookup.Provider provider) {
        return GraphValueCodecRegistry.toTag(value, provider);
    }

    @Nullable
    public static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        return GraphValueCodecRegistry.fromTag(tag, provider);
    }

    public static boolean isSupported(Object value) {
        return GraphValueCodecRegistry.isSupported(value);
    }
}
