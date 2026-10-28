package com.mine.geometry_node.core.engine.graph.debug.geometry;

import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.Objects;

/** Deferred outer placement; local geometry transforms are baked only once. */
public record GeometryDebugPlacement(SpatialAnchor anchor, Vec3 worldOffset, Vec3 worldRotation) {
    public GeometryDebugPlacement {
        Objects.requireNonNull(anchor, "anchor");
        new SpatialTransform(worldOffset, worldRotation, Vec3.ZERO, Vec3.ZERO);
    }

    public @Nullable Pose resolve(Level level) {
        if (level != null && !anchor.dimensionOr(level.dimension()).equals(level.dimension())) return null;
        if (!anchor.requiresEntity()) return new Pose(anchor.position().add(worldOffset), SpatialTransform.rotation(worldRotation));
        Vec3 position = SpatialAnchorResolver.resolve(anchor, level);
        Quaterniond rotation = SpatialAnchorResolver.resolveRotation(anchor, level);
        return position == null || rotation == null ? null
                : new Pose(position.add(worldOffset), SpatialTransform.outerRotation(worldRotation, rotation));
    }

    public @Nullable Pose resolve(Level level, float partialTick) {
        if (level != null && !anchor.dimensionOr(level.dimension()).equals(level.dimension())) return null;
        if (!anchor.requiresEntity()) return new Pose(anchor.position().add(worldOffset), SpatialTransform.rotation(worldRotation));
        Vec3 position = SpatialAnchorResolver.resolve(anchor, level, partialTick);
        Quaterniond rotation = SpatialAnchorResolver.resolveRotation(anchor, level, partialTick);
        return position == null || rotation == null ? null
                : new Pose(position.add(worldOffset), SpatialTransform.outerRotation(worldRotation, rotation));
    }

    public record Pose(Vec3 position, Quaterniond rotation) {
        public Vec3 apply(Vec3 point) { return position.add(SpatialTransform.rotate(rotation, point)); }
        public AABB apply(AABB bounds) {
            Vec3 first = apply(new Vec3(bounds.minX, bounds.minY, bounds.minZ));
            double minX = first.x, minY = first.y, minZ = first.z;
            double maxX = minX, maxY = minY, maxZ = minZ;
            for (int i = 1; i < 8; i++) {
                Vec3 point = apply(new Vec3((i & 1) == 0 ? bounds.minX : bounds.maxX,
                        (i & 2) == 0 ? bounds.minY : bounds.maxY, (i & 4) == 0 ? bounds.minZ : bounds.maxZ));
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
