package com.mine.geometry_node.core.engine.system.data.library;

import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.UUID;

/** A persistent reference to a loaded entity, not an entity template snapshot. */
public record DataLibraryEntityReference(UUID entityId) {
    public DataLibraryEntityReference {
        Objects.requireNonNull(entityId, "entityId");
    }

    public static DataLibraryEntityReference capture(Entity entity) {
        return new DataLibraryEntityReference(entity.getUUID());
    }
}
