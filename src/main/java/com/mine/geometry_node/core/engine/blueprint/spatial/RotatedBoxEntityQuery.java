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

public final class RotatedBoxEntityQuery {
    private RotatedBoxEntityQuery() {
    }

    public static List<Entity> find(ServerLevel level,
                                    Vec3 center,
                                    Vec3 size,
                                    Vec3 rotation,
                                    Predicate<Entity> predicate) {
        if (level == null || center == null || size == null) {
            return List.of();
        }
        Vec3 safeSize = sanitizeSize(size);
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;

        double radius = safeSize.length() * 0.6;
        AABB broadBox = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        List<Entity> candidates = level.getEntities((Entity) null, broadBox, entity ->
                entity != null && !entity.isRemoved() && (predicate == null || predicate.test(entity)));

        Quaternionf boxRotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(safeRotation.y),
                (float) Math.toRadians(safeRotation.x),
                (float) Math.toRadians(safeRotation.z)
        );
        Quaternionf inverseRotation = new Quaternionf(boxRotation).invert();

        float halfX = (float) safeSize.x * 0.5f;
        float halfY = (float) safeSize.y * 0.5f;
        float halfZ = (float) safeSize.z * 0.5f;

        List<Entity> hitEntities = new ArrayList<>();
        for (Entity entity : candidates) {
            if (intersects(entity.getBoundingBox(), center, inverseRotation, halfX, halfY, halfZ)) {
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

    private static boolean intersects(AABB aabb,
                                      Vec3 center,
                                      Quaternionf inverseRotation,
                                      float halfX,
                                      float halfY,
                                      float halfZ) {
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

        return maxX >= -halfX && minX <= halfX
                && maxY >= -halfY && minY <= halfY
                && maxZ >= -halfZ && minZ <= halfZ;
    }
}
