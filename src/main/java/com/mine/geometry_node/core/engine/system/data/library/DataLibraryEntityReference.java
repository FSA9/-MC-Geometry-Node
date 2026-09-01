package com.mine.geometry_node.core.engine.system.data.library;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** A persistent reference to a loaded entity, not an entity template snapshot. */
public record DataLibraryEntityReference(Identifier dimension, UUID entityId) {
    public DataLibraryEntityReference {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(entityId, "entityId");
    }

    public static DataLibraryEntityReference capture(Entity entity) {
        return new DataLibraryEntityReference(entity.level().dimension().identifier(), entity.getUUID());
    }

    @Nullable
    public Entity resolve(MinecraftServer server) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = server.getLevel(key);
        return level != null ? level.getEntity(entityId) : null;
    }
}
