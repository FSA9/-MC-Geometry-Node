package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Public force-field identity within one dimension. */
public record ForceFieldAddress(ResourceKey<Level> dimension, String id) {
    public ForceFieldAddress {
        Objects.requireNonNull(dimension, "dimension");
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Force field id cannot be empty");
        if (id.length() > 512) throw new IllegalArgumentException("Force field id is too long");
    }

    @Nullable
    public static ForceFieldAddress tryCreate(ResourceKey<Level> dimension, String id) {
        try {
            return new ForceFieldAddress(dimension, id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
