package com.mine.geometry_node.core.engine.spatial;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.Objects;
import java.util.UUID;

/** Shared dimension-aware capture and resolution; missing live references remain unresolved. */
public final class SpatialAnchorResolver {
    private SpatialAnchorResolver() { }

    @FunctionalInterface
    public interface EntityLookup {
        @Nullable Entity find(ResourceKey<Level> dimension, UUID entityId);
    }

    /** Captures fixed entity positions at the consuming object's creation boundary. */
    public static @Nullable SpatialAnchor capture(SpatialAnchor anchor, Level contextLevel) {
        return available(anchor, contextLevel) ? capture(anchor, contextLevel.dimension(), lookup(contextLevel)) : null;
    }

    public static @Nullable SpatialAnchor capture(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                                   EntityLookup lookup) {
        Objects.requireNonNull(anchor, "anchor");
        if (!anchor.isEntity()) return anchor.withDimension(anchor.dimensionOr(contextDimension));
        ResourceKey<Level> dimension = anchor.dimensionOr(contextDimension);
        Entity entity = entity(anchor, dimension, lookup);
        if (entity == null) return null;
        SpatialAnchor bound = anchor.withDimension(dimension);
        return bound.followPosition() ? bound : bound.withCapturedPosition(position(bound, entity, false, 0));
    }

    public static @Nullable Vec3 resolve(SpatialAnchor anchor, Level contextLevel) {
        return available(anchor, contextLevel) ? resolve(anchor, contextLevel.dimension(), lookup(contextLevel), false, 0) : null;
    }

    public static @Nullable Vec3 resolve(SpatialAnchor anchor, Level contextLevel, float partialTick) {
        return available(anchor, contextLevel) ? resolve(anchor, contextLevel.dimension(), lookup(contextLevel), true, partialTick) : null;
    }

    public static @Nullable Vec3 resolve(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                         EntityLookup lookup) {
        return resolve(anchor, contextDimension, lookup, false, 0);
    }

    public static @Nullable Vec3 resolvePosition(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                                 EntityLookup lookup, Vec3 offset) {
        Vec3 base = resolve(anchor, contextDimension, lookup);
        return base == null ? null : base.add(Objects.requireNonNull(offset, "offset"));
    }

    public static @Nullable Quaterniond resolveRotation(SpatialAnchor anchor, Level contextLevel) {
        return available(anchor, contextLevel) ? resolveRotation(anchor, contextLevel.dimension(), lookup(contextLevel), false, 0) : null;
    }

    public static @Nullable Quaterniond resolveRotation(SpatialAnchor anchor, Level contextLevel, float partialTick) {
        return available(anchor, contextLevel) ? resolveRotation(anchor, contextLevel.dimension(), lookup(contextLevel), true, partialTick) : null;
    }

    public static @Nullable Quaterniond resolveRotation(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                                        EntityLookup lookup) {
        return resolveRotation(anchor, contextDimension, lookup, false, 0);
    }

    private static @Nullable Quaterniond resolveRotation(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                                          EntityLookup lookup, boolean interpolate, float partialTick) {
        Objects.requireNonNull(anchor, "anchor");
        if (!anchor.isEntity() || !anchor.followOrientation()) return new Quaterniond();
        Entity entity = entity(anchor, anchor.dimensionOr(contextDimension), lookup);
        if (entity == null) return null;
        boolean body = anchor.orientationSource() == SpatialAnchor.OrientationSource.BODY;
        double yaw = body && entity instanceof LivingEntity living
                ? interpolate ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : living.yBodyRot
                : interpolate ? entity.getViewYRot(partialTick) : entity.getYRot();
        double pitch = body ? 0 : interpolate ? entity.getViewXRot(partialTick) : entity.getXRot();
        return new Quaterniond().rotationYXZ(Math.toRadians(-yaw), Math.toRadians(pitch), 0);
    }

    private static @Nullable Vec3 resolve(SpatialAnchor anchor, ResourceKey<Level> contextDimension,
                                          EntityLookup lookup, boolean interpolate, float partialTick) {
        Objects.requireNonNull(anchor, "anchor");
        if (!anchor.isEntity() || !anchor.followPosition()) return anchor.position();
        Entity entity = entity(anchor, anchor.dimensionOr(contextDimension), lookup);
        return entity == null ? null : position(anchor, entity, interpolate, partialTick);
    }

    private static Vec3 position(SpatialAnchor anchor, Entity entity, boolean interpolate, float partialTick) {
        if (!interpolate) return anchor.origin() == SpatialAnchor.Origin.EYE ? entity.getEyePosition() : entity.position();
        return anchor.origin() == SpatialAnchor.Origin.EYE
                ? entity.getEyePosition(partialTick) : entity.getPosition(partialTick);
    }

    private static @Nullable Entity entity(SpatialAnchor anchor, ResourceKey<Level> dimension, EntityLookup lookup) {
        Entity entity = Objects.requireNonNull(lookup, "lookup").find(dimension, anchor.entityId());
        return entity == null || entity.isRemoved() || !anchor.entityId().equals(entity.getUUID())
                || !dimension.equals(entity.level().dimension()) ? null : entity;
    }

    private static boolean available(SpatialAnchor anchor, Level contextLevel) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(contextLevel, "contextLevel");
        ResourceKey<Level> dimension = anchor.dimensionOr(contextLevel.dimension());
        return dimension.equals(contextLevel.dimension()) || contextLevel instanceof ServerLevel server
                && server.getServer().getLevel(dimension) != null;
    }

    private static EntityLookup lookup(Level contextLevel) {
        Objects.requireNonNull(contextLevel, "contextLevel");
        return (dimension, id) -> {
            Level target = contextLevel;
            if (!dimension.equals(contextLevel.dimension())) {
                target = contextLevel instanceof ServerLevel server ? server.getServer().getLevel(dimension) : null;
            }
            return target == null ? null : target.getEntity(id);
        };
    }
}
