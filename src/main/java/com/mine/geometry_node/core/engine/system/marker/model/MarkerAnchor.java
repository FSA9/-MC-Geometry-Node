package com.mine.geometry_node.core.engine.system.marker.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public sealed interface MarkerAnchor permits MarkerAnchor.Coordinate, MarkerAnchor.Entity {
    ResourceKey<Level> dimension();

    record Coordinate(ResourceKey<Level> dimension, Vec3 position) implements MarkerAnchor {
        public Coordinate {
            dimension = Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position");
        }
    }

    record Entity(ResourceKey<Level> dimension, UUID entityId) implements MarkerAnchor {
        public Entity {
            dimension = Objects.requireNonNull(dimension, "dimension");
            entityId = Objects.requireNonNull(entityId, "entityId");
        }
    }
}
