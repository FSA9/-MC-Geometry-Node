package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Public Area identity. Graph ownership is deliberately not part of this address. */
public record AreaAddress(ResourceKey<Level> dimension, String id) {
    public AreaAddress {
        Objects.requireNonNull(dimension, "dimension");
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Area id cannot be empty");
        if (id.length() > 512) throw new IllegalArgumentException("Area id is too long");
    }

    @Nullable
    public static AreaAddress tryCreate(ResourceKey<Level> dimension, String id) {
        try {
            return new AreaAddress(dimension, id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
