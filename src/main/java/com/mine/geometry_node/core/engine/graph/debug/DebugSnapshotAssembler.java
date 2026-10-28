package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.PathfindingDebugProvider.Subject;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

final class DebugSnapshotAssembler {
    private final Residency residency;
    private final BiFunction<ServerLevel, UUID, Anchor> anchors;

    DebugSnapshotAssembler() {
        this(ResourceResidency::isResident, (level, uuid) -> {
            if (!ResourceResidency.isEntityResident(level, uuid)) return null;
            var entity = level.getEntity(uuid);
            return entity == null ? null : new Anchor(entity.position(), entity.getId(), entity.getEyePosition());
        });
    }

    DebugSnapshotAssembler(Residency residency, BiFunction<ServerLevel, UUID, Anchor> anchors) {
        this.residency = residency;
        this.anchors = anchors;
    }

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
            for (int i = 0; i < source.meshes().size(); i++) {
                addBoundedCandidate(level, origin, radiusSqr, source.meshes().get(i), source.bounds().get(i), candidates);
            }
        }

        if (isEnabled(channelMask, DebugRenderChannel.FORCE_FIELD)) {
            for (GeometryDebugElement mesh : ForceFieldDebugProvider.collect(level, origin, radius)) {
                candidates.add(new Candidate(mesh, mesh.center().distanceToSqr(origin)));
            }
        }

        if (observations != null && isEnabled(channelMask, DebugRenderChannel.INTERACTION)) {
            for (GeometryDebugElement mesh : observations.interactions()) {
                // The provider already queried accessible entity sections. Level.isLoaded also
                // checks block build height, which can reject a valid Interaction's AABB center.
                double distanceSqr = mesh.center().distanceToSqr(origin);
                if (distanceSqr <= radiusSqr) candidates.add(new Candidate(mesh, distanceSqr));
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

    private void addBoundedCandidate(ServerLevel level, Vec3 origin, double radiusSqr,
                                    GeometryDebugElement mesh, AABB bounds, List<Candidate> candidates) {
        if (mesh.placement() != null) {
            var placement = mesh.placement();
            com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugPlacement.Pose pose;
            if (placement.anchor().followPosition() && !placement.anchor().followOrientation()) {
                Anchor entity = anchors.apply(level, mesh.anchorUuid());
                if (entity == null) return;
                Vec3 position = placement.anchor().origin() == com.mine.geometry_node.core.engine.spatial.SpatialAnchor.Origin.EYE
                        ? entity.eyePosition() : entity.position();
                pose = new com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugPlacement.Pose(
                        position.add(placement.worldOffset()),
                        com.mine.geometry_node.core.engine.spatial.SpatialTransform.rotation(placement.worldRotation()));
            } else {
                pose = placement.resolve(level);
                if (pose == null) return;
            }
            bounds = pose.apply(bounds);
        }
        if (!residency.isResident(level, bounds, mesh.anchorUuid())) return;
        if (mesh.anchorUuid() != null) {
            Anchor anchor = anchors.apply(level, mesh.anchorUuid());
            if (anchor == null) return;
            mesh = mesh.withAnchor(mesh.anchorUuid(), anchor.entityId());
        }
        double x = Math.max(0, Math.max(bounds.minX - origin.x, origin.x - bounds.maxX));
        double y = Math.max(0, Math.max(bounds.minY - origin.y, origin.y - bounds.maxY));
        double z = Math.max(0, Math.max(bounds.minZ - origin.z, origin.z - bounds.maxZ));
        double distanceSqr = x * x + y * y + z * z;
        if (distanceSqr <= radiusSqr) candidates.add(new Candidate(mesh, distanceSqr));
    }

    /** Detect ID replacement even if no chunk revision or authored definition changes. */
    long anchorSignature(ServerLevel level, DebugSourceStore sources) {
        long signature = 1469598103934665603L;
        for (DebugSourceStore.VisibleSource source : sources.sources(level)) {
            for (GeometryDebugElement mesh : source.meshes()) {
                if (mesh.anchorUuid() == null) continue;
                Anchor anchor = anchors.apply(level, mesh.anchorUuid());
                signature = mix(signature, mesh.anchorUuid().getMostSignificantBits());
                signature = mix(signature, mesh.anchorUuid().getLeastSignificantBits());
                signature = mix(signature, anchor == null ? -1 : anchor.entityId());
            }
        }
        return signature;
    }

    @FunctionalInterface
    interface Residency {
        boolean isResident(ServerLevel level, AABB bounds, UUID anchor);
    }

    record Anchor(Vec3 position, int entityId, Vec3 eyePosition) {
        Anchor(Vec3 position, int entityId) { this(position, entityId, position); }
    }

    private static PacketGeometryDebugSnapshot.Mesh toPacketMesh(GeometryDebugElement mesh) {
        Vec3 center = mesh.center();
        return new PacketGeometryDebugSnapshot.Mesh(
                mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(),
                center.x, center.y, center.z,
                mesh.size().x, mesh.size().y, mesh.size().z,
                mesh.rotation().x, mesh.rotation().y, mesh.rotation().z,
                mesh.vertices(), mesh.edges(), mesh.faces(), mesh.placement(), mesh.anchorEntityId()
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
        signature = mix(signature, mesh.placement() == null ? 0 : mesh.placement().hashCode());
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
