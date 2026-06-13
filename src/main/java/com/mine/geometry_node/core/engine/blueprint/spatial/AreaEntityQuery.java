package com.mine.geometry_node.core.engine.blueprint.spatial;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AreaEntityQuery {
    private AreaEntityQuery() {
    }

    public static List<Entity> find(ServerLevel level,
                                    AreaShape shape,
                                    Vec3 center,
                                    Vec3 size,
                                    Vec3 rotation,
                                    Predicate<Entity> predicate) {
        if (level == null || center == null || size == null) {
            return List.of();
        }

        Vec3 safeSize = sanitizeSize(size);
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;
        AreaShape safeShape = shape != null ? shape : AreaShape.BOX;
        double broadRadius = broadRadius(safeShape, safeSize);
        AABB broadBox = AABB.ofSize(center, broadRadius * 2, broadRadius * 2, broadRadius * 2);
        List<Entity> candidates = level.getEntities((Entity) null, broadBox, entity ->
                entity != null && !entity.isRemoved() && (predicate == null || predicate.test(entity)));

        Quaternionf inverseRotation = inverseRotation(safeRotation);
        float halfX = (float) safeSize.x * 0.5f;
        float halfY = (float) safeSize.y * 0.5f;
        float halfZ = (float) safeSize.z * 0.5f;

        List<Entity> hitEntities = new ArrayList<>();
        for (Entity entity : candidates) {
            if (intersects(entity.getBoundingBox(), center, inverseRotation, safeShape, halfX, halfY, halfZ)) {
                hitEntities.add(entity);
            }
        }

        return hitEntities;
    }

    public static Vec3 sanitizeSize(Vec3 size) {
        if (size == null) {
            return new Vec3(1, 1, 1);
        }
        return new Vec3(
                Math.max(0.001, Math.abs(size.x)),
                Math.max(0.001, Math.abs(size.y)),
                Math.max(0.001, Math.abs(size.z))
        );
    }

    private static double broadRadius(AreaShape shape, Vec3 size) {
        return switch (shape) {
            case SPHERE -> Math.max(size.x, Math.max(size.y, size.z)) * 0.5;
            case CYLINDER -> Math.sqrt(square(Math.max(size.x, size.z) * 0.5) + square(size.y * 0.5));
            case BOX -> size.length() * 0.6;
        };
    }

    private static Quaternionf inverseRotation(Vec3 rotation) {
        Quaternionf areaRotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );
        return new Quaternionf(areaRotation).invert();
    }

    private static boolean intersects(AABB aabb,
                                      Vec3 center,
                                      Quaternionf inverseRotation,
                                      AreaShape shape,
                                      float halfX,
                                      float halfY,
                                      float halfZ) {
        return switch (shape) {
            case SPHERE -> intersectsSphere(aabb, center, Math.max(halfX, Math.max(halfY, halfZ)));
            case CYLINDER -> intersectsCylinder(aabb, center, inverseRotation, halfX, halfY, halfZ);
            case BOX -> intersectsBox(aabb, center, inverseRotation, halfX, halfY, halfZ);
        };
    }

    private static boolean intersectsSphere(AABB aabb, Vec3 center, float radius) {
        double closestX = clamp(center.x, aabb.minX, aabb.maxX);
        double closestY = clamp(center.y, aabb.minY, aabb.maxY);
        double closestZ = clamp(center.z, aabb.minZ, aabb.maxZ);
        return square(closestX - center.x) + square(closestY - center.y) + square(closestZ - center.z) <= square(radius);
    }

    private static boolean intersectsCylinder(AABB aabb,
                                              Vec3 center,
                                              Quaternionf inverseRotation,
                                              float halfX,
                                              float halfY,
                                              float halfZ) {
        LocalBounds bounds = localBounds(aabb, center, inverseRotation);
        if (bounds.maxY < -halfY || bounds.minY > halfY) {
            return false;
        }

        double closestX = closestToZero(bounds.minX, bounds.maxX);
        double closestZ = closestToZero(bounds.minZ, bounds.maxZ);
        return square(closestX / halfX) + square(closestZ / halfZ) <= 1.0;
    }

    private static boolean intersectsBox(AABB aabb,
                                         Vec3 center,
                                         Quaternionf inverseRotation,
                                         float halfX,
                                         float halfY,
                                         float halfZ) {
        LocalBounds bounds = localBounds(aabb, center, inverseRotation);
        return bounds.maxX >= -halfX && bounds.minX <= halfX
                && bounds.maxY >= -halfY && bounds.minY <= halfY
                && bounds.maxZ >= -halfZ && bounds.minZ <= halfZ;
    }

    private static LocalBounds localBounds(AABB aabb, Vec3 center, Quaternionf inverseRotation) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        for (int x = 0; x < 2; x++) {
            double cornerX = x == 0 ? aabb.minX : aabb.maxX;
            for (int y = 0; y < 2; y++) {
                double cornerY = y == 0 ? aabb.minY : aabb.maxY;
                for (int z = 0; z < 2; z++) {
                    double cornerZ = z == 0 ? aabb.minZ : aabb.maxZ;
                    Vector3f local = new Vector3f(
                            (float) (cornerX - center.x),
                            (float) (cornerY - center.y),
                            (float) (cornerZ - center.z)
                    );
                    local.rotate(inverseRotation);

                    minX = Math.min(minX, local.x());
                    minY = Math.min(minY, local.y());
                    minZ = Math.min(minZ, local.z());
                    maxX = Math.max(maxX, local.x());
                    maxY = Math.max(maxY, local.y());
                    maxZ = Math.max(maxZ, local.z());
                }
            }
        }

        return new LocalBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double closestToZero(double min, double max) {
        if (min > 0.0) return min;
        if (max < 0.0) return max;
        return 0.0;
    }

    private static double square(double value) {
        return value * value;
    }

    private record LocalBounds(float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ) {
    }
}
