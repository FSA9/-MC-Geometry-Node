package com.mine.geometry_node.core.engine.graph.resource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** Stable host scope of a graph-owned runtime resource. */
public sealed interface GraphResourceScope permits GraphResourceScope.LevelScope, GraphResourceScope.EntityScope {
    ResourceKey<Level> dimension();

    record LevelScope(ResourceKey<Level> dimension) implements GraphResourceScope {
        public LevelScope {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    record EntityScope(ResourceKey<Level> dimension, UUID ownerId) implements GraphResourceScope {
        public EntityScope {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(ownerId, "ownerId");
        }
    }
}
