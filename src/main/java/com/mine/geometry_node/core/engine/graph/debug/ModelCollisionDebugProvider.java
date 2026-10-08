package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugType;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionMetrics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** On-demand, bounded visualization of the same surfaces used by server collision. */
final class ModelCollisionDebugProvider {
    private static final int MAX_FACES = 8192;
    private static final int CHUNK_FACES = 512;
    private static final int COLOR = 0xFF56E39F;
    private ServerLevel lastLevel;
    private long lastRevision = Long.MIN_VALUE;
    private Vec3 lastOrigin;
    private double lastRadius;
    private boolean warned;
    private List<GeometryDebugElement> meshes = List.of();

    List<GeometryDebugElement> collect(ServerPlayer player, double radius) {
        ServerLevel level = player.level();
        Vec3 origin = player.position();
        long revision = ModelCollisionScene.revision(level);
        if (level == lastLevel && revision == lastRevision && radius == lastRadius
                && lastOrigin != null && origin.distanceToSqr(lastOrigin) < 16) return meshes;
        lastLevel = level;
        lastRevision = revision;
        lastOrigin = origin;
        lastRadius = radius;
        try (var sample = ModelCollisionMetrics.debugBuild()) {
        int availableParts = 0, displayedParts = 0;
        Comparator<Part> order = Comparator.comparingDouble(Part::distance);
        PriorityQueue<Part> nearest = new PriorityQueue<>(order.reversed());
        boolean truncated = false;
        for (ModelCollisionScene.Entry entry : ModelCollisionScene.snapshots(level)) {
            for (ModelCollisionMesh.Part part : entry.geometry().mesh().parts()) {
                availableParts++;
                double distance = distanceSquared(part, entry, origin);
                if (distance > radius * radius) continue;
                if (nearest.size() >= MAX_FACES / 4) {
                    truncated = true;
                    if (distance >= nearest.peek().distance()) continue;
                    nearest.poll();
                }
                nearest.add(new Part(part, entry.x(), entry.y(), entry.z(), distance));
            }
        }
        List<Part> parts = new ArrayList<>(nearest);
        parts.sort(order);
        List<GeometryDebugElement> result = new ArrayList<>();
        Chunk chunk = new Chunk();
        int total = 0;
        for (Part part : parts) {
            var surface = ModelCollisionDebugGeometry.of(part.part());
            int count = surface.faces().length / 4;
            if (total + count > MAX_FACES) { truncated = true; continue; }
            if (chunk.faces.size() / 4 + count > CHUNK_FACES) {
                result.add(chunk.mesh(result.size(), origin));
                chunk = new Chunk();
            }
            chunk.add(surface, part, origin);
            total += count;
            displayedParts++;
        }
        if (!chunk.faces.isEmpty()) result.add(chunk.mesh(result.size(), origin));
        meshes = List.copyOf(result);
        if (truncated && !warned) player.sendSystemMessage(Component.translatable(
                "geometry_node.debug.model_collision.limited", MAX_FACES));
        warned = truncated;
        sample.partsTested = availableParts;
        sample.candidates = parts.size();
        sample.meshes = meshes.size(); sample.faces = total;
        sample.edges = meshes.stream().mapToLong(mesh -> mesh.edges().length / 2).sum();
        ModelCollisionMetrics.debugState(true, availableParts, parts.size(), displayedParts,
                total, (int) sample.edges, truncated);
        return meshes;
        }
    }

    void clear() {
        meshes = List.of();
        lastLevel = null;
        lastOrigin = null;
        lastRevision = Long.MIN_VALUE;
        warned = false;
    }

    private static final class Chunk {
        final List<Float> vertices = new ArrayList<>();
        final List<Integer> edges = new ArrayList<>(), faces = new ArrayList<>();

        void add(ModelCollisionDebugGeometry.Surface surface, Part part, Vec3 origin) {
            int offset = vertices.size() / 3;
            double[] source = surface.vertices();
            for (int i = 0; i < source.length; i += 3) {
                vertices.add((float) (source[i] + part.x() - origin.x));
                vertices.add((float) (source[i + 1] + part.y() - origin.y));
                vertices.add((float) (source[i + 2] + part.z() - origin.z));
            }
            for (int edge : surface.edges()) edges.add(edge + offset);
            for (int face : surface.faces()) faces.add(face + offset);
        }

        GeometryDebugElement mesh(int index, Vec3 center) {
            float[] positions = new float[vertices.size()];
            for (int i = 0; i < positions.length; i++) positions[i] = vertices.get(i);
            return new GeometryDebugElement("model_collision:" + index, "model_collision", GeometryDebugType.MESH,
                    COLOR, false, center, Vec3.ZERO, Vec3.ZERO, positions,
                    edges.stream().mapToInt(Integer::intValue).toArray(), faces.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private static double distanceSquared(ModelCollisionMesh.Part part, ModelCollisionScene.Entry entry, Vec3 origin) {
        double x = origin.x - entry.x(), y = origin.y - entry.y(), z = origin.z - entry.z();
        double dx = Math.max(0, Math.max(part.minX() - x, x - part.maxX()));
        double dy = Math.max(0, Math.max(part.minY() - y, y - part.maxY()));
        double dz = Math.max(0, Math.max(part.minZ() - z, z - part.maxZ()));
        return dx * dx + dy * dy + dz * dz;
    }

    private record Part(ModelCollisionMesh.Part part, double x, double y, double z, double distance) {}
}
