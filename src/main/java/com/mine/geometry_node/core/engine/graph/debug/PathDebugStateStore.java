package com.mine.geometry_node.core.engine.graph.debug;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PathDebugStateStore {
    private final Map<UUID, RequestedTarget> requestedTargets = new HashMap<>();
    private final Map<UUID, FollowTarget> followTargets = new HashMap<>();
    private final Map<UUID, PatrolRoute> patrolRoutes = new HashMap<>();

    void recordRequested(Mob mob, Vec3 position, long expiresAt) {
        requestedTargets.put(mob.getUUID(), new RequestedTarget(mob.level().dimension(), position, expiresAt));
    }

    boolean clearRequested(UUID mobId) {
        return requestedTargets.remove(mobId) != null;
    }

    void recordFollow(Mob follower, Entity target, long expiresAt) {
        followTargets.put(follower.getUUID(), new FollowTarget(
                follower.level().dimension(), target.getUUID(), expiresAt));
    }

    boolean clearFollow(UUID mobId) {
        return followTargets.remove(mobId) != null;
    }

    boolean recordPatrol(Mob mob, List<Vec3> waypoints, int completedCount, boolean loop) {
        PatrolRoute next = new PatrolRoute(
                mob.level().dimension(), List.copyOf(waypoints), completedCount, loop);
        return !next.equals(patrolRoutes.put(mob.getUUID(), next));
    }

    boolean clearPatrol(UUID mobId) {
        return patrolRoutes.remove(mobId) != null;
    }

    RequestedTarget requested(Mob mob, ServerLevel level, long tick) {
        RequestedTarget target = requestedTargets.get(mob.getUUID());
        return target != null && target.dimension().equals(level.dimension()) && target.expiresAt() >= tick
                ? target : null;
    }

    FollowTarget follow(Mob mob, ServerLevel level, long tick) {
        FollowTarget target = followTargets.get(mob.getUUID());
        return target != null && target.dimension().equals(level.dimension()) && target.expiresAt() >= tick
                ? target : null;
    }

    PatrolRoute patrol(Mob mob, ServerLevel level) {
        PatrolRoute route = patrolRoutes.get(mob.getUUID());
        return route != null && route.dimension().equals(level.dimension()) ? route : null;
    }

    void prune(ServerLevel level, long tick) {
        ResourceKey<Level> dimension = level.dimension();
        requestedTargets.entrySet().removeIf(entry -> {
            RequestedTarget target = entry.getValue();
            return target.dimension().equals(dimension) && target.expiresAt() < tick;
        });
        followTargets.entrySet().removeIf(entry -> {
            FollowTarget target = entry.getValue();
            return target.dimension().equals(dimension) && target.expiresAt() < tick;
        });
        patrolRoutes.entrySet().removeIf(entry -> {
            PatrolRoute route = entry.getValue();
            Entity owner = level.getEntity(entry.getKey());
            return route.dimension().equals(dimension) && (owner == null || owner.isRemoved());
        });
    }

    boolean removeDimension(ResourceKey<Level> dimension) {
        boolean changed = requestedTargets.values().removeIf(target -> target.dimension().equals(dimension));
        changed |= followTargets.values().removeIf(target -> target.dimension().equals(dimension));
        changed |= patrolRoutes.values().removeIf(route -> route.dimension().equals(dimension));
        return changed;
    }

    void clear() {
        requestedTargets.clear();
        followTargets.clear();
        patrolRoutes.clear();
    }

    boolean isEmpty() {
        return requestedTargets.isEmpty() && followTargets.isEmpty() && patrolRoutes.isEmpty();
    }

    record RequestedTarget(ResourceKey<Level> dimension, Vec3 position, long expiresAt) {
    }

    record FollowTarget(ResourceKey<Level> dimension, UUID targetId, long expiresAt) {
    }

    record PatrolRoute(ResourceKey<Level> dimension, List<Vec3> waypoints, int completedCount, boolean loop) {
    }
}
