package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.PathfindingDebugProvider.Subject;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DebugSnapshotAssembler {
    Snapshot assemble(ServerLevel level,
                      Vec3 origin,
                      double radius,
                      int channelMask,
                      int maxMeshes,
                      int maxPathfindingEntities,
                      DebugSourceStore sources,
                      DebugLevelObservationCache observations) {
        return assemble(level, origin, radius, channelMask, maxMeshes, maxPathfindingEntities,
                sources, observations, List.of());
    }

    Snapshot assemble(ServerLevel level,
                      Vec3 origin,
                      double radius,
                      int channelMask,
                      int maxMeshes,
                      int maxPathfindingEntities,
                      DebugSourceStore sources,
                      DebugLevelObservationCache observations,
                      List<GeometryDebugElement> modelCollision) {
        double radiusSqr = radius * radius;
        List<Candidate> candidates = new ArrayList<>();
        // Collision parts have already been bounded using their full extent, not just their center.
        for (GeometryDebugElement mesh : modelCollision) {
            candidates.add(new Candidate(mesh, mesh.center().distanceToSqr(origin)));
        }
        for (DebugSourceStore.VisibleSource source : sources.sources(level)) {
            if (!isEnabled(channelMask, source.id().channel())) continue;
            for (GeometryDebugElement mesh : source.meshes()) {
                addBoundedCandidate(level, origin, radiusSqr, mesh, candidates);
            }
        }

        if (observations != null && isEnabled(channelMask, DebugRenderChannel.INTERACTION)) {
            for (GeometryDebugElement mesh : observations.interactions()) {
                addBoundedCandidate(level, origin, radiusSqr, mesh, candidates);
            }
        }
        if (observations != null && isEnabled(channelMask, DebugRenderChannel.PATHFINDING)) {
            List<Subject> subjects = observations.pathfinding().stream()
                    .filter(subject -> subject.center().distanceToSqr(origin) <= radiusSqr)
                    .sorted(Comparator.comparingDouble((Subject subject) -> subject.center().distanceToSqr(origin))
                            .thenComparing(Subject::entityId))
                    .limit(maxPathfindingEntities)
                    .toList();
            for (Subject subject : subjects) {
                for (GeometryDebugElement mesh : subject.meshes()) {
                    candidates.add(new Candidate(mesh, mesh.center().distanceToSqr(origin)));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::distanceSqr)
                .thenComparing(candidate -> candidate.mesh().id())
                .thenComparing(candidate -> candidate.mesh().graphId()));
        int count = Math.min(maxMeshes, candidates.size());
        // Distance selects the budgeted set; stable wire order avoids resending moving anchors' meshes.
        candidates = candidates.subList(0, count);
        candidates.sort(Comparator.comparing((Candidate candidate) -> candidate.mesh().id())
                .thenComparing(candidate -> candidate.mesh().graphId()));
        List<PacketGeometryDebugSnapshot.Mesh> meshes = new ArrayList<>(count);
        long signature = 1469598103934665603L;
        for (int i = 0; i < count; i++) {
            PacketGeometryDebugSnapshot.Mesh mesh = toPacketMesh(candidates.get(i).mesh());
            meshes.add(mesh);
            signature = mix(signature, mesh);
        }
        signature = signature * 31L + count;
        signature = signature * 31L + Double.doubleToLongBits(radius);
        return new Snapshot(List.copyOf(meshes), signature);
    }

    static int channelBit(DebugRenderChannel channel) {
        return 1 << channel.ordinal();
    }

    private static boolean isEnabled(int mask, DebugRenderChannel channel) {
        return (mask & channelBit(channel)) != 0;
    }

    private static void addBoundedCandidate(ServerLevel level, Vec3 origin, double radiusSqr,
                                            GeometryDebugElement mesh, List<Candidate> candidates) {
        Vec3 center = mesh.center();
        if (mesh.anchorUuid() != null) {
            var anchor = level.getEntity(mesh.anchorUuid());
            if (anchor == null || !anchor.isAlive()) return;
            center = center.add(anchor.position());
        }
        if (!level.isLoaded(BlockPos.containing(center))) return;
        double distanceSqr = center.distanceToSqr(origin);
        if (distanceSqr <= radiusSqr) candidates.add(new Candidate(mesh, distanceSqr));
    }

    private static PacketGeometryDebugSnapshot.Mesh toPacketMesh(GeometryDebugElement mesh) {
        Vec3 center = mesh.center();
        return new PacketGeometryDebugSnapshot.Mesh(
                mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(),
                center.x, center.y, center.z,
                mesh.size().x, mesh.size().y, mesh.size().z,
                mesh.rotation().x, mesh.rotation().y, mesh.rotation().z,
                mesh.vertices(), mesh.edges(), mesh.faces(), mesh.anchorUuid(), mesh.anchorEntityId()
        );
    }

    private static long mix(long signature, PacketGeometryDebugSnapshot.Mesh mesh) {
        signature = mix(signature, mesh.id().hashCode());
        signature = mix(signature, mesh.graphId().hashCode());
        signature = mix(signature, mesh.geometryType().networkId());
        signature = mix(signature, mesh.color());
        signature = mix(signature, mesh.showPoints() ? 1L : 0L);
        signature = mix(signature, mesh.anchorUuid() == null ? 0 : mesh.anchorUuid().hashCode());
        signature = mix(signature, mesh.anchorEntityId());
        signature = mix(signature, Double.doubleToLongBits(mesh.centerX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerZ()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeZ()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationZ()));
        for (float value : mesh.vertices()) signature = mix(signature, Float.floatToIntBits(value));
        for (int value : mesh.edges()) signature = mix(signature, value);
        for (int value : mesh.faces()) signature = mix(signature, value);
        return signature;
    }

    private static long mix(long signature, long value) {
        return (signature ^ value) * 1099511628211L;
    }

    private record Candidate(GeometryDebugElement mesh, double distanceSqr) {
    }

    record Snapshot(List<PacketGeometryDebugSnapshot.Mesh> meshes, long signature) {
    }
}
