package com.mine.geometry_node.core.engine.system.chunk_loading;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted entity-owned chunk-loading configuration. The stored center is
 * also the exact forced-ticket range to release after a restart.
 */
public record EntityChunkLoadingConfig(
        UUID entityId,
        ResourceKey<Level> dimension,
        int centerChunkX,
        int centerChunkZ,
        int radius
) {
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 32;

    public EntityChunkLoadingConfig {
        entityId = Objects.requireNonNull(entityId, "entityId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
    }
}
