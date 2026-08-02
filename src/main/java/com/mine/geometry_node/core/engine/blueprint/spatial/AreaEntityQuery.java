package com.mine.geometry_node.core.engine.blueprint.spatial;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AreaEntityQuery {
    private static final double PROJECTILE_SWEEP_PADDING = 16.0D;
    private static final double EPSILON = 1.0E-7D;

    private AreaEntityQuery() {
    }

    public record Hit(Entity entity,
                      Vec3 hitPos,
                      Vec3 velocity) {
    }

    public static List<Entity> find(ServerLevel level,
                                    AreaShape shape,
                                    Vec3 center,
                                    Vec3 size,
                                    Vec3 rotation,
                                    Predicate<Entity> predicate) {
        return find(level, shape, center, size, rotation, AreaTargetType.ALL, predicate);
    }

    public static List<Entity> find(ServerLevel level,
                                    AreaShape shape,
                                    Vec3 center,
                                    Vec3 size,
                                    Vec3 rotation,
                                    AreaTargetType targetType,
                                    Predicate<Entity> predicate) {
        List<Hit> hits = findHits(level, shape, center, size, rotation, targetType, predicate);
        List<Entity> entities = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            entities.add(hit.entity());
        }
        return entities;
    }

    public static List<Hit> findHits(ServerLevel level,
                                     AreaShape shape,
                                     Vec3 center,
                                     Vec3 size,
                                     Vec3 rotation,
                                     AreaTargetType targetType,
                                     Predicate<Entity> predicate) {
        if (level == null || center == null || size == null) {
            return List.of();
        }

        Vec3 safeSize = sanitizeSize(size);
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;
        AreaShape safeShape = shape != null ? shape : AreaShape.BOX;
        AreaTargetType safeTargetType = targetType != null ? targetType : AreaTargetType.ALL;
        double broadRadius = broadRadius(safeShape, safeSize);
        AABB broadBox = AABB.ofSize(center, broadRadius * 2, broadRadius * 2, broadRadius * 2);
        if (safeTargetType == AreaTargetType.PROJECTILE) {
            broadBox = broadBox.inflate(PROJECTILE_SWEEP_PADDING);
        }
        List<Entity> candidates = findCandidates(level, broadBox, safeTargetType, predicate);

        Quaternionf areaRotation = safeShape == AreaShape.SPHERE ? null : rotation(safeRotation);
        Quaternionf inverseRotation = areaRotation == null ? null : new Quaternionf(areaRotation).invert();
        float halfX = (float) safeSize.x * 0.5f;
        float halfY = (float) safeSize.y * 0.5f;
        float halfZ = (float) safeSize.z * 0.5f;

        List<Hit> hits = new ArrayList<>();
        for (Entity entity : candidates) {
            Hit hit = contact(entity, center, areaRotation, inverseRotation, safeShape, halfX, halfY, halfZ);
            if (hit != null) {
                hits.add(hit);
            }
        }

        return hits;
    }

    private static List<Entity> findCandidates(ServerLevel level,
                                               AABB broadBox,
                                               AreaTargetType targetType,
                                               Predicate<Entity> predicate) {
        Predicate<Entity> combinedPredicate = entity ->
                entity != null
                        && !entity.isRemoved()
                        && targetType.matches(entity)
                        && (predicate == null || predicate.test(entity));

        if (targetType == AreaTargetType.ALL) {
            return level.getEntities((Entity) null, broadBox, combinedPredicate);
        }

        return findCandidatesOfClass(level, broadBox, targetType.entityClass(), combinedPredicate);
    }

    private static <T extends Entity> List<Entity> findCandidatesOfClass(ServerLevel level,
                                                                         AABB broadBox,
                                                                         Class<T> entityClass,
                                                                         Predicate<Entity> predicate) {
        List<T> typedCandidates = level.getEntitiesOfClass(entityClass, broadBox, predicate::test);
        return new ArrayList<>(typedCandidates);
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

    private static Quaternionf rotation(Vec3 rotation) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );
    }

    private static Hit contact(Entity entity,
                               Vec3 center,
                               Quaternionf areaRotation,
                               Quaternionf inverseRotation,
                               AreaShape shape,
                               float halfX,
                               float halfY,
                               float halfZ) {
        Vec3 end = entity.position();
        Vec3 start = previousPosition(entity, end);
        Vec3 velocity = entity.getDeltaMovement();
        boolean intersectsNow = intersects(entity.getBoundingBox(), center, inverseRotation, shape, halfX, halfY, halfZ);

        if (entity instanceof Projectile) {
            if (intersectsNow && containsPoint(start, center, inverseRotation, shape, halfX, halfY, halfZ)) {
                return new Hit(entity, end, velocity);
            }
            SegmentHit sweptHit = segmentHit(start, end, center, areaRotation, inverseRotation, shape, halfX, halfY, halfZ);
            if (sweptHit != null) {
                return new Hit(entity, sweptHit.position(), velocity);
            }
        }

        if (!intersectsNow) {
            return null;
        }

        return new Hit(entity, end, velocity);
    }

    private static Vec3 previousPosition(Entity entity, Vec3 fallback) {
        Vec3 velocity = entity.getDeltaMovement();
        // A newly spawned projectile can still carry constructor-time xOld coordinates.
        if (entity instanceof Projectile && entity.tickCount <= 1 && isFinite(velocity)) {
            return fallback.subtract(velocity);
        }
        Vec3 oldPos = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        if (isFinite(oldPos)) {
            return oldPos;
        }
        return velocity != null ? fallback.subtract(velocity) : fallback;
    }

    private static boolean containsPoint(Vec3 point,
                                         Vec3 center,
                                         Quaternionf inverseRotation,
                                         AreaShape shape,
                                         float halfX,
                                         float halfY,
                                         float halfZ) {
        if (shape == AreaShape.SPHERE) {
            double radius = Math.max(halfX, Math.max(halfY, halfZ));
            return point.distanceToSqr(center) <= square(radius);
        }

        Vec3 local = toLocal(point, center, inverseRotation);
        return switch (shape) {
            case BOX -> Math.abs(local.x) <= halfX
                    && Math.abs(local.y) <= halfY
                    && Math.abs(local.z) <= halfZ;
            case CYLINDER -> insideCylinder(local, halfX, halfY, halfZ);
            case SPHERE -> false;
        };
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

    private static SegmentHit segmentHit(Vec3 start,
                                         Vec3 end,
                                         Vec3 center,
                                         Quaternionf areaRotation,
                                         Quaternionf inverseRotation,
                                         AreaShape shape,
                                         float halfX,
                                         float halfY,
                                         float halfZ) {
        return switch (shape) {
            case SPHERE -> segmentSphere(start, end, center, Math.max(halfX, Math.max(halfY, halfZ)));
            case BOX -> segmentBox(start, end, center, areaRotation, inverseRotation, halfX, halfY, halfZ);
            case CYLINDER -> segmentCylinder(start, end, center, areaRotation, inverseRotation, halfX, halfY, halfZ);
        };
    }

    private static SegmentHit segmentSphere(Vec3 start, Vec3 end, Vec3 center, float radius) {
        Vec3 direction = end.subtract(start);
        double a = direction.lengthSqr();
        if (a < EPSILON) {
            return square(start.x - center.x) + square(start.y - center.y) + square(start.z - center.z) <= square(radius)
                    ? new SegmentHit(start, safeNormal(start.subtract(center), new Vec3(0, 1, 0)))
                    : null;
        }

        Vec3 offset = start.subtract(center);
        double c = offset.lengthSqr() - square(radius);
        if (c <= 0.0D) {
            return new SegmentHit(start, safeNormal(offset, direction.scale(-1.0D)));
        }

        double b = 2.0D * offset.dot(direction);
        double discriminant = b * b - 4.0D * a * c;
        if (discriminant < 0.0D) {
            return null;
        }

        double t = (-b - Math.sqrt(discriminant)) / (2.0D * a);
        if (t < 0.0D || t > 1.0D) {
            return null;
        }

        Vec3 hitPos = start.add(direction.scale(t));
        return new SegmentHit(hitPos, safeNormal(hitPos.subtract(center), direction.scale(-1.0D)));
    }

    private static SegmentHit segmentBox(Vec3 start,
                                         Vec3 end,
                                         Vec3 center,
                                         Quaternionf areaRotation,
                                         Quaternionf inverseRotation,
                                         float halfX,
                                         float halfY,
                                         float halfZ) {
        Vec3 localStart = toLocal(start, center, inverseRotation);
        Vec3 localEnd = toLocal(end, center, inverseRotation);
        Vec3 localDirection = localEnd.subtract(localStart);
        SlabHit slabHit = slabHit(localStart, localDirection, halfX, halfY, halfZ);
        if (slabHit == null) {
            return null;
        }

        Vec3 worldHit = start.add(end.subtract(start).scale(slabHit.t()));
        Vec3 worldNormal = toWorldDirection(slabHit.normal(), areaRotation);
        return new SegmentHit(worldHit, safeNormal(worldNormal, end.subtract(start).scale(-1.0D)));
    }

    private static SegmentHit segmentCylinder(Vec3 start,
                                              Vec3 end,
                                              Vec3 center,
                                              Quaternionf areaRotation,
                                              Quaternionf inverseRotation,
                                              float halfX,
                                              float halfY,
                                              float halfZ) {
        Vec3 localStart = toLocal(start, center, inverseRotation);
        Vec3 localEnd = toLocal(end, center, inverseRotation);
        Vec3 localDirection = localEnd.subtract(localStart);

        CandidateHit best = null;
        if (insideCylinder(localStart, halfX, halfY, halfZ)) {
            best = new CandidateHit(0.0D, cylinderNormal(localStart, halfX, halfY, halfZ));
        }

        double a = square(localDirection.x / halfX) + square(localDirection.z / halfZ);
        double b = 2.0D * ((localStart.x * localDirection.x) / square(halfX) + (localStart.z * localDirection.z) / square(halfZ));
        double c = square(localStart.x / halfX) + square(localStart.z / halfZ) - 1.0D;
        if (a > EPSILON) {
            double discriminant = b * b - 4.0D * a * c;
            if (discriminant >= 0.0D) {
                double sqrt = Math.sqrt(discriminant);
                best = bestCylinderSideCandidate(best, localStart, localDirection, (-b - sqrt) / (2.0D * a), halfX, halfY, halfZ);
                best = bestCylinderSideCandidate(best, localStart, localDirection, (-b + sqrt) / (2.0D * a), halfX, halfY, halfZ);
            }
        }

        if (Math.abs(localDirection.y) > EPSILON) {
            best = bestCylinderCapCandidate(best, localStart, localDirection, (-halfY - localStart.y) / localDirection.y, new Vec3(0, -1, 0), halfX, halfZ);
            best = bestCylinderCapCandidate(best, localStart, localDirection, (halfY - localStart.y) / localDirection.y, new Vec3(0, 1, 0), halfX, halfZ);
        }

        if (best == null) {
            return null;
        }

        Vec3 worldHit = start.add(end.subtract(start).scale(best.t()));
        Vec3 worldNormal = toWorldDirection(best.normal(), areaRotation);
        return new SegmentHit(worldHit, safeNormal(worldNormal, end.subtract(start).scale(-1.0D)));
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

    private static SlabHit slabHit(Vec3 start, Vec3 direction, float halfX, float halfY, float halfZ) {
        SlabRange range = new SlabRange(0.0D, 1.0D, Vec3.ZERO);
        range = clipSlab(range, start.x, direction.x, -halfX, halfX, new Vec3(-1, 0, 0), new Vec3(1, 0, 0));
        if (range == null) return null;
        range = clipSlab(range, start.y, direction.y, -halfY, halfY, new Vec3(0, -1, 0), new Vec3(0, 1, 0));
        if (range == null) return null;
        range = clipSlab(range, start.z, direction.z, -halfZ, halfZ, new Vec3(0, 0, -1), new Vec3(0, 0, 1));
        if (range == null) return null;

        Vec3 normal = range.normal.lengthSqr() > EPSILON
                ? range.normal
                : safeNormal(start, direction.scale(-1.0D));
        return new SlabHit(range.minT, normal);
    }

    private static SlabRange clipSlab(SlabRange range,
                                      double start,
                                      double direction,
                                      double min,
                                      double max,
                                      Vec3 minNormal,
                                      Vec3 maxNormal) {
        if (Math.abs(direction) < EPSILON) {
            return start < min || start > max ? null : range;
        }

        double t1 = (min - start) / direction;
        double t2 = (max - start) / direction;
        Vec3 nearNormal = minNormal;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
            nearNormal = maxNormal;
        }

        double minT = range.minT;
        Vec3 normal = range.normal;
        if (t1 > minT) {
            minT = t1;
            normal = nearNormal;
        }

        double maxT = Math.min(range.maxT, t2);
        if (minT > maxT || maxT < 0.0D || minT > 1.0D) {
            return null;
        }

        return new SlabRange(Math.max(0.0D, minT), Math.min(1.0D, maxT), normal);
    }

    private static CandidateHit bestCylinderSideCandidate(CandidateHit best,
                                                          Vec3 start,
                                                          Vec3 direction,
                                                          double t,
                                                          float halfX,
                                                          float halfY,
                                                          float halfZ) {
        if (t < 0.0D || t > 1.0D) {
            return best;
        }
        Vec3 point = start.add(direction.scale(t));
        if (point.y < -halfY || point.y > halfY) {
            return best;
        }
        return bestCandidate(best, new CandidateHit(t, cylinderSideNormal(point, halfX, halfZ)));
    }

    private static CandidateHit bestCylinderCapCandidate(CandidateHit best,
                                                         Vec3 start,
                                                         Vec3 direction,
                                                         double t,
                                                         Vec3 normal,
                                                         float halfX,
                                                         float halfZ) {
        if (t < 0.0D || t > 1.0D) {
            return best;
        }
        Vec3 point = start.add(direction.scale(t));
        if (square(point.x / halfX) + square(point.z / halfZ) > 1.0D) {
            return best;
        }
        return bestCandidate(best, new CandidateHit(t, normal));
    }

    private static CandidateHit bestCandidate(CandidateHit current, CandidateHit candidate) {
        return current == null || candidate.t < current.t ? candidate : current;
    }

    private static boolean insideCylinder(Vec3 point, float halfX, float halfY, float halfZ) {
        return point.y >= -halfY
                && point.y <= halfY
                && square(point.x / halfX) + square(point.z / halfZ) <= 1.0D;
    }

    private static Vec3 cylinderNormal(Vec3 point, float halfX, float halfY, float halfZ) {
        double sideValue = square(point.x / halfX) + square(point.z / halfZ);
        if (Math.abs(point.y - halfY) < 0.001D && sideValue < 0.95D) {
            return new Vec3(0, 1, 0);
        }
        if (Math.abs(point.y + halfY) < 0.001D && sideValue < 0.95D) {
            return new Vec3(0, -1, 0);
        }
        return cylinderSideNormal(point, halfX, halfZ);
    }

    private static Vec3 cylinderSideNormal(Vec3 point, float halfX, float halfZ) {
        return safeNormal(new Vec3(point.x / square(halfX), 0.0D, point.z / square(halfZ)), new Vec3(0, 1, 0));
    }

    private static Vec3 toLocal(Vec3 point, Vec3 center, Quaternionf inverseRotation) {
        Vector3f local = new Vector3f(
                (float) (point.x - center.x),
                (float) (point.y - center.y),
                (float) (point.z - center.z)
        );
        local.rotate(inverseRotation);
        return new Vec3(local.x(), local.y(), local.z());
    }

    private static Vec3 toWorldDirection(Vec3 localDirection, Quaternionf areaRotation) {
        Vector3f world = new Vector3f((float) localDirection.x, (float) localDirection.y, (float) localDirection.z);
        world.rotate(areaRotation);
        return new Vec3(world.x(), world.y(), world.z());
    }

    private static Vec3 safeNormal(Vec3 vector, Vec3 fallback) {
        if (vector != null && isFinite(vector) && vector.lengthSqr() > EPSILON) {
            return vector.normalize();
        }
        if (fallback != null && isFinite(fallback) && fallback.lengthSqr() > EPSILON) {
            return fallback.normalize();
        }
        return new Vec3(0, 1, 0);
    }

    private static boolean isFinite(Vec3 vec) {
        return vec != null
                && Double.isFinite(vec.x)
                && Double.isFinite(vec.y)
                && Double.isFinite(vec.z);
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

    private record SegmentHit(Vec3 position, Vec3 normal) {
    }

    private record SlabHit(double t, Vec3 normal) {
    }

    private record SlabRange(double minT, double maxT, Vec3 normal) {
    }

    private record CandidateHit(double t, Vec3 normal) {
    }
}
