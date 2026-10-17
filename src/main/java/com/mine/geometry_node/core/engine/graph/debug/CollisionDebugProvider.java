package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugType;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
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
final class CollisionDebugProvider {
    private static final int MAX_FACES = 8192;
    private static final int CHUNK_FACES = 512;
    private final boolean nodeCollision;
    private final String channel;

    CollisionDebugProvider(boolean nodeCollision) {
        this.nodeCollision = nodeCollision;
        this.channel = nodeCollision ? "collision" : "model_collision";
    }

    static boolean isNodeCollision(Object instanceId) {
        return instanceId instanceof GraphResourceId id
                && id.type().equals(GraphResourceTypeRegistry.GEOMETRY_COLLISION);
    }
    private ServerLevel lastLevel;
    private long lastRevision = Long.MIN_VALUE;
    private Vec3 lastOrigin;
    private List<Vec3> lastOrigins = List.of();
    private double lastRadius;
    private boolean warned;
    private List<GeometryDebugElement> meshes = List.of();

    List<GeometryDebugElement> collect(ServerPlayer player, double radius) {
        return collect(player, radius, player.position());
    }

    List<GeometryDebugElement> collect(ServerPlayer player, double radius, Vec3 origin) {
        return collect(player, radius, List.of(origin));
    }

    List<GeometryDebugElement> collect(ServerPlayer player, double radius, List<Vec3> origins) {
        if (origins.isEmpty()) return List.of();
        Vec3 origin = origins.getFirst();
        ServerLevel level = player.level();
        long revision = ModelCollisionScene.revision(level);
        if (level == lastLevel && revision == lastRevision && radius == lastRadius
                && lastOrigins.equals(origins)) return meshes;
        lastLevel = level;
        lastRevision = revision;
        lastOrigin = origin;
        lastOrigins = List.copyOf(origins);
        lastRadius = radius;
        try (var sample = nodeCollision ? null : ModelCollisionMetrics.debugBuild()) {
        int availableParts = 0, displayedParts = 0;
        Comparator<Part> order = Comparator.comparingDouble(Part::distance);
        PriorityQueue<Part> nearest = new PriorityQueue<>(order.reversed());
        boolean truncated = false;
        for (ModelCollisionScene.Entry entry : ModelCollisionScene.snapshots(level)) {
            if (entry.instanceId() instanceof com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime.Key) continue;
            if (isNodeCollision(entry.instanceId()) != nodeCollision) continue;
            for (ModelCollisionMesh.Part part : entry.geometry().mesh().parts()) {
                availableParts++;
                Vec3 closest = origin;
                double distance = Double.POSITIVE_INFINITY;
                for (Vec3 eye : origins) {
                    double candidate = distanceSquared(part, entry, eye);
                    if (candidate < distance) { distance = candidate; closest = eye; }
                }
                if (distance > radius * radius) continue;
                if (nearest.size() >= MAX_FACES / 4) {
                    truncated = true;
                    if (distance >= nearest.peek().distance()) continue;
                    nearest.poll();
                }
                nearest.add(new Part(part, entry.x(), entry.y(), entry.z(), distance, closest));
            }
        }
        List<Part> parts = new ArrayList<>(nearest);
        parts.sort(order);
        List<GeometryDebugElement> result = new ArrayList<>();
        var chunks = new java.util.LinkedHashMap<Vec3, Chunk>();
        int total = 0;
        for (Part part : parts) {
            var surface = ModelCollisionDebugGeometry.of(part.part());
            Vec3 chunkOrigin = part.origin();
            Chunk chunk = chunks.computeIfAbsent(chunkOrigin, key -> new Chunk());
            int count = surface.faces().length / 4;
            if (total + count > MAX_FACES) { truncated = true; continue; }
            if (chunk.faces.size() / 4 + count > CHUNK_FACES) {
                result.add(chunk.mesh(result.size(), chunkOrigin));
                chunk = new Chunk();
                chunks.put(chunkOrigin, chunk);
            }
            chunk.add(surface, part, chunkOrigin);
            total += count;
            displayedParts++;
        }
        chunks.forEach((chunkOrigin, chunk) -> {
            if (!chunk.faces.isEmpty()) result.add(chunk.mesh(result.size(), chunkOrigin));
        });
        meshes = List.copyOf(result);
        if (truncated && !warned) player.sendSystemMessage(Component.translatable(
                "geometry_node.debug." + channel + ".limited", MAX_FACES));
        warned = truncated;
        if (sample != null) {
        sample.partsTested = availableParts;
        sample.candidates = parts.size();
        sample.meshes = meshes.size(); sample.faces = total;
        sample.edges = meshes.stream().mapToLong(mesh -> mesh.edges().length / 2).sum();
        ModelCollisionMetrics.debugState(true, availableParts, parts.size(), displayedParts,
                total, (int) sample.edges, truncated);
        }
        return meshes;
        }
    }

    void clear() {
        meshes = List.of();
        lastLevel = null;
        lastOrigin = null;
        lastOrigins = List.of();
        lastRevision = Long.MIN_VALUE;
        warned = false;
    }

    private final class Chunk {
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
            return new GeometryDebugElement(channel + ":" + index, channel, GeometryDebugType.MESH,
                    DebugRenderStyle.COLLISION.color(), DebugRenderStyle.COLLISION.showPoints(),
                    center, Vec3.ZERO, Vec3.ZERO, positions,
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

    private record Part(ModelCollisionMesh.Part part, double x, double y, double z, double distance, Vec3 origin) {}
}
