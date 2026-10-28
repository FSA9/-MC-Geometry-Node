package com.mine.geometry_node.core.engine.spatial;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Immutable spatial reference. Object transforms remain the responsibility of its consumer. */
public record SpatialAnchor(@Nullable ResourceKey<Level> dimension,
                            @Nullable UUID entityId,
                            Vec3 position,
                            Origin origin,
                            boolean followPosition,
                            boolean followOrientation,
                            OrientationSource orientationSource) {
    public enum Origin { BASE, EYE }
    public enum OrientationSource { BODY, VIEW }

    public SpatialAnchor {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(orientationSource, "orientationSource");
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Spatial anchor coordinates must be finite");
        }
        if (entityId == null && (origin != Origin.BASE || followPosition || followOrientation
                || orientationSource != OrientationSource.BODY)) {
            throw new IllegalArgumentException("World anchors have no entity-following settings");
        }
        if (entityId != null && followPosition && !position.equals(Vec3.ZERO)) {
            throw new IllegalArgumentException("Following entity positions cannot contain a captured position");
        }
    }

    public static SpatialAnchor world(@Nullable ResourceKey<Level> dimension, Vec3 position) {
        return new SpatialAnchor(dimension, null, position, Origin.BASE, false, false, OrientationSource.BODY);
    }

    public static SpatialAnchor world(Vec3 position) { return world(null, position); }

    public static SpatialAnchor entity(@Nullable ResourceKey<Level> dimension, UUID entityId, Origin origin) {
        return entity(dimension, entityId, origin, true, false, OrientationSource.BODY);
    }

    public static SpatialAnchor entity(@Nullable ResourceKey<Level> dimension, UUID entityId, Origin origin,
                                       boolean followPosition, boolean followOrientation, OrientationSource source) {
        return new SpatialAnchor(dimension, Objects.requireNonNull(entityId, "entityId"), Vec3.ZERO, origin,
                followPosition, followOrientation, source);
    }

    public static SpatialAnchor entity(Entity entity, Origin origin) {
        return entity(entity, origin, true, false, OrientationSource.BODY);
    }

    public static SpatialAnchor entity(Entity entity, Origin origin, boolean followPosition,
                                       boolean followOrientation, OrientationSource source) {
        Objects.requireNonNull(entity, "entity");
        return entity(entity.level().dimension(), entity.getUUID(), origin, followPosition, followOrientation, source);
    }

    public boolean isEntity() { return entityId != null; }

    /** Fully captured references do not depend on the original entity remaining loaded. */
    public boolean requiresEntity() { return isEntity() && (followPosition || followOrientation); }

    public SpatialAnchor withDimension(@Nullable ResourceKey<Level> dimension) {
        return new SpatialAnchor(dimension, entityId, position, origin, followPosition, followOrientation, orientationSource);
    }

    public SpatialAnchor withOrigin(Origin origin) {
        return new SpatialAnchor(dimension, entityId, position, origin, followPosition, followOrientation, orientationSource);
    }

    public SpatialAnchor withFollowPosition(boolean follow) {
        return new SpatialAnchor(dimension, entityId, follow ? Vec3.ZERO : position, origin,
                follow, followOrientation, orientationSource);
    }

    public SpatialAnchor withFollowOrientation(boolean follow) {
        return new SpatialAnchor(dimension, entityId, position, origin, followPosition, follow, orientationSource);
    }

    public SpatialAnchor withOrientationSource(OrientationSource source) {
        return new SpatialAnchor(dimension, entityId, position, origin, followPosition, followOrientation, source);
    }

    /** Used at object creation, after resolving the source entity, never during ordinary rendering. */
    public SpatialAnchor withCapturedPosition(Vec3 captured) {
        if (!isEntity() || followPosition) throw new IllegalStateException("Only fixed entity anchors capture a position");
        return new SpatialAnchor(dimension, entityId, captured, origin, false, followOrientation, orientationSource);
    }

    public ResourceKey<Level> dimensionOr(ResourceKey<Level> contextDimension) {
        return dimension != null ? dimension : Objects.requireNonNull(contextDimension, "contextDimension");
    }
}
