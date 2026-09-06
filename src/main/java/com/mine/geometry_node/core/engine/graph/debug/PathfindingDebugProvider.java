package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.PathDebugStateStore.FollowTarget;
import com.mine.geometry_node.core.engine.graph.debug.PathDebugStateStore.PatrolRoute;
import com.mine.geometry_node.core.engine.graph.debug.PathDebugStateStore.RequestedTarget;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PathfindingDebugProvider {
    private static final int MAX_PATH_NODES = 128;
    private static final int PATH_COLOR = DebugRenderChannel.PATHFINDING.color();
    private static final int NEXT_NODE_COLOR = 0xFFFFC247;
    private static final int FINAL_TARGET_COLOR = 0xFF4FD17A;
    private static final int REQUESTED_TARGET_COLOR = 0xFF4A8DFF;
    private static final int FOLLOW_TARGET_COLOR = 0xFFE86DFF;
    private static final int PATROL_COMPLETED_COLOR = 0xFF8A8A8A;

    List<Subject> collect(ServerLevel level,
                          List<AABB> regions,
                          List<DebugObserverArea> observers,
                          PathDebugStateStore state,
                          int maxSubjectsPerObserver) {
        if (regions.isEmpty()) return List.of();
        long tick = level.getGameTime();
        state.prune(level, tick);
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Mob> visibleMobs = new HashMap<>();
        for (AABB region : regions) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, region, candidate -> isVisible(candidate, level, state, tick))) {
                if (!seen.add(mob.getUUID())) continue;
                visibleMobs.put(mob.getUUID(), mob);
            }
        }

        Set<UUID> selected = new HashSet<>();
        for (DebugObserverArea observer : observers) {
            double radiusSqr = observer.radius() * observer.radius();
            visibleMobs.values().stream()
                    .filter(mob -> mob.distanceToSqr(observer.origin()) <= radiusSqr)
                    .sorted(Comparator.comparingDouble((Mob mob) -> mob.distanceToSqr(observer.origin()))
                            .thenComparing(Mob::getUUID))
                    .limit(maxSubjectsPerObserver)
                    .map(Mob::getUUID)
                    .forEach(selected::add);
        }

        List<Subject> subjects = new ArrayList<>(selected.size());
        for (UUID entityId : selected) {
            Mob mob = visibleMobs.get(entityId);
            if (mob == null) continue;
            List<GeometryDebugElement> meshes = buildMeshes(level, mob, state, tick);
            if (!meshes.isEmpty()) {
                subjects.add(new Subject(entityId, mob.getBoundingBox().getCenter(), List.copyOf(meshes)));
            }
        }
        return List.copyOf(subjects);
    }

    private static boolean isVisible(Mob mob, ServerLevel level, PathDebugStateStore state, long tick) {
        Path path = mob.getNavigation().getPath();
        return !mob.isRemoved() && (path != null && !path.isDone()
                || state.requested(mob, level, tick) != null
                || state.follow(mob, level, tick) != null
                || state.patrol(mob, level) != null);
    }

    private static List<GeometryDebugElement> buildMeshes(ServerLevel level, Mob mob,
                                                           PathDebugStateStore state, long tick) {
        List<GeometryDebugElement> meshes = new ArrayList<>();
        Path path = mob.getNavigation().getPath();
        RequestedTarget requested = state.requested(mob, level, tick);
        FollowTarget follow = state.follow(mob, level, tick);
        PatrolRoute patrol = state.patrol(mob, level);
        if (follow != null) addFollowLine(level, mob, follow, meshes);
        if (patrol != null) addPatrolRoute(mob, patrol, meshes);
        if (path == null || path.isDone()) {
            if (requested != null) addPathMarker(mob, "requested", requested.position(), REQUESTED_TARGET_COLOR, meshes);
            return meshes;
        }

        BlockPos pathEndPos = path.getNodePos(path.getNodeCount() - 1);
        addPathMesh(mob, path, meshes);
        addPathMarker(mob, "next", path.getNextNodePos(), NEXT_NODE_COLOR, meshes);
        if (requested != null) addPathMarker(mob, "requested", requested.position(), REQUESTED_TARGET_COLOR, meshes);
        if (requested == null || !pathEndPos.equals(BlockPos.containing(requested.position()))) {
            addPathMarker(mob, "target", pathEndPos, FINAL_TARGET_COLOR, meshes);
        }
        return meshes;
    }

    private static void addPatrolRoute(Mob mob, PatrolRoute route, List<GeometryDebugElement> meshes) {
        List<Vec3> points = route.waypoints();
        int pointCount = Math.min(points.size(), MAX_PATH_NODES);
        for (int i = 0; i < pointCount; i++) {
            int color = !route.loop() && i < route.completedCount() ? PATROL_COMPLETED_COLOR : REQUESTED_TARGET_COLOR;
            addPathMarker(mob, "patrol-point-" + i, points.get(i), color, meshes);
        }
        int segmentCount = route.loop() ? pointCount : Math.max(0, pointCount - 1);
        for (int i = 0; i < segmentCount; i++) {
            int end = (i + 1) % pointCount;
            int color = !route.loop() && i < route.completedCount() - 1
                    ? PATROL_COMPLETED_COLOR : REQUESTED_TARGET_COLOR;
            addPatrolSegment(mob, points.get(i), points.get(end), i, color, meshes);
        }
    }

    private static void addPatrolSegment(Mob mob, Vec3 start, Vec3 end, int index,
                                         int color, List<GeometryDebugElement> meshes) {
        Vec3 center = start.add(end).scale(0.5D);
        float[] vertices = new float[]{
                (float) (start.x - center.x), (float) (start.y - center.y), (float) (start.z - center.z),
                (float) (end.x - center.x), (float) (end.y - center.y), (float) (end.z - center.z)
        };
        String id = DebugRenderChannel.PATHFINDING.id() + ":" + mob.getStringUUID() + ":patrol-segment-" + index;
        meshes.add(new GeometryDebugElement(id, "pathfinding", GeometryDebugType.MESH, color, false,
                center, Vec3.ZERO, Vec3.ZERO, vertices, new int[]{0, 1}, new int[0]));
    }

    private static void addFollowLine(ServerLevel level, Mob follower, FollowTarget relation,
                                      List<GeometryDebugElement> meshes) {
        Entity target = level.getEntity(relation.targetId());
        if (target == null || target.isRemoved()) return;
        Vec3 start = follower.getBoundingBox().getCenter();
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 center = start.add(end).scale(0.5D);
        float[] vertices = new float[]{
                (float) (start.x - center.x), (float) (start.y - center.y), (float) (start.z - center.z),
                (float) (end.x - center.x), (float) (end.y - center.y), (float) (end.z - center.z)
        };
        String id = DebugRenderChannel.PATHFINDING.id() + ":" + follower.getStringUUID() + ":follow";
        meshes.add(new GeometryDebugElement(id, "pathfinding", GeometryDebugType.MESH,
                FOLLOW_TARGET_COLOR, false, center, Vec3.ZERO, Vec3.ZERO,
                vertices, new int[]{0, 1}, new int[0]));
    }

    private static void addPathMesh(Mob mob, Path path, List<GeometryDebugElement> meshes) {
        int firstNode = path.getNextNodeIndex();
        int nodeCount = Math.min(path.getNodeCount() - firstNode, MAX_PATH_NODES);
        if (nodeCount <= 0) return;
        Vec3 center = Vec3.atCenterOf(mob.blockPosition());
        float[] vertices = new float[(nodeCount + 1) * 3];
        writeRelativeVertex(vertices, 0, center, center);
        for (int i = 0; i < nodeCount; i++) {
            writeRelativeVertex(vertices, i + 1, Vec3.atCenterOf(path.getNodePos(firstNode + i)), center);
        }
        int[] edges = new int[nodeCount * 2];
        for (int i = 0; i < nodeCount; i++) {
            edges[i * 2] = i;
            edges[i * 2 + 1] = i + 1;
        }
        String id = DebugRenderChannel.PATHFINDING.id() + ":" + mob.getStringUUID() + ":path";
        meshes.add(new GeometryDebugElement(id, "pathfinding", GeometryDebugType.MESH, PATH_COLOR, true,
                center, Vec3.ZERO, Vec3.ZERO, vertices, edges, new int[0]));
    }

    private static void addPathMarker(Mob mob, String name, BlockPos position,
                                      int color, List<GeometryDebugElement> meshes) {
        addPathMarker(mob, name, Vec3.atCenterOf(position), color, meshes);
    }

    private static void addPathMarker(Mob mob, String name, Vec3 position,
                                      int color, List<GeometryDebugElement> meshes) {
        DebugRenderShape shape = new DebugRenderShape(
                DebugRenderChannel.PATHFINDING.id() + ":" + mob.getStringUUID() + ":" + name,
                "pathfinding", "box", position, new Vec3(1.0D, 1.0D, 1.0D), Vec3.ZERO, color
        );
        meshes.add(GeometryDebugMeshFactory.buildShapeMesh(shape));
    }

    private static void writeRelativeVertex(float[] vertices, int index, Vec3 position, Vec3 center) {
        int offset = index * 3;
        vertices[offset] = (float) (position.x - center.x);
        vertices[offset + 1] = (float) (position.y - center.y);
        vertices[offset + 2] = (float) (position.z - center.z);
    }

    record Subject(UUID entityId, Vec3 center, List<GeometryDebugElement> meshes) {
    }
}
