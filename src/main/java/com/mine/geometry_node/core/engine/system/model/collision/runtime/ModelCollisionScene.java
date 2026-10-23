package com.mine.geometry_node.core.engine.system.model.collision.runtime;

import com.mine.geometry_node.core.engine.metadata.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.RuntimeMetadataTargets;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import net.minecraft.server.level.ServerLevel;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.model.collision.generation.ModelCollisionBuilder;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** Level identity isolates client prediction, server authority, dimensions and reconnects. */
public final class ModelCollisionScene {
    private static final Map<Level, State> LEVELS = new WeakHashMap<>();
    private ModelCollisionScene() {}

    public static Prepared prepare(ModelCollisionMesh mesh) throws ModelProblem {
        return prepare(mesh, new ModelCancellation());
    }

    public static Prepared prepare(ModelCollisionMesh mesh, ModelCancellation cancellation) throws ModelProblem {
        return new Prepared(mesh, cancellation);
    }

    public static synchronized void publish(Level level, Object instanceId, Prepared geometry, double x, double y, double z) {
        publish(level, instanceId, geometry, x, y, z, null);
    }

    public static synchronized void publish(Level level, Object instanceId, Prepared geometry, double x, double y, double z,
                                            UUID excludedEntity) {
        State state = LEVELS.computeIfAbsent(level, ignored -> new State());
        Definition previous = state.definitions.get(instanceId);
        RuntimeMetadata metadata = previous == null ? new RuntimeMetadata() : previous.metadata();
        AABB localBounds = geometry == null ? null : geometry.bounds();
        AABB worldBounds = localBounds == null ? new AABB(x, y, z, x, y, z) : localBounds.move(x, y, z);
        state.definitions.put(instanceId, new Definition(instanceId, worldBounds, x, y, z, excludedEntity, metadata));
        state.entries.put(instanceId, new Entry(instanceId, geometry, x, y, z, excludedEntity, metadata));
        state.snapshotDirty = true;
        state.revision++;
    }

    /** Retained query information owns metadata even when the heavyweight runtime is suspended. */
    public static synchronized void retainDefinition(Level level, Object instanceId, AABB worldBounds,
            double x, double y, double z, UUID excludedEntity) {
        com.mine.geometry_node.core.engine.resource.residency.ChunkCoverage.of(worldBounds);
        State state = LEVELS.computeIfAbsent(level, ignored -> new State());
        Definition previous = state.definitions.get(instanceId);
        state.definitions.put(instanceId, new Definition(instanceId, worldBounds, x, y, z, excludedEntity,
                previous == null ? new RuntimeMetadata() : previous.metadata()));
        state.revision++;
    }

    /** Temporary deactivation retains no prepared geometry but preserves definition and metadata. */
    public static synchronized void suspend(Level level, Object instanceId) {
        State state = LEVELS.get(level);
        if (state != null && state.entries.remove(instanceId) != null) {
            state.snapshot = List.of(); state.snapshotDirty = true; state.revision++;
        }
    }

    public static synchronized void remove(Level level, Object instanceId) {
        State state = LEVELS.get(level);
        if (state == null) return;
        boolean changed = state.definitions.remove(instanceId) != null;
        if (state.entries.remove(instanceId) != null) { state.snapshot = List.of(); state.snapshotDirty = true; changed = true; }
        if (changed) state.revision++;
    }

    public static synchronized void clear(Level level) {
        State state = LEVELS.get(level);
        if (state != null && (!state.entries.isEmpty() || !state.definitions.isEmpty())) {
            state.entries.clear(); state.definitions.clear();
            state.snapshot = List.of(); state.snapshotDirty = false; state.revision++;
        }
    }

    public static synchronized void visitDefinitions(Level level, java.util.function.Predicate<Definition> visitor) {
        State state = LEVELS.get(level);
        if (state != null) for (Definition entry : state.definitions.values()) if (!visitor.test(entry)) return;
    }

    private static synchronized Definition definition(Level level, Object instanceId) {
        State state = LEVELS.get(level);
        return state == null ? null : state.definitions.get(instanceId);
    }

    public static synchronized long revision(Level level) {
        State state = LEVELS.get(level);
        return state == null ? 0 : state.revision;
    }

    public static synchronized List<Entry> snapshots(Level level) {
        State state = LEVELS.get(level);
        if (state != null && state.snapshotDirty) {
            state.snapshot = List.copyOf(state.entries.values());
            state.snapshotDirty = false;
        }
        return state == null ? List.of() : state.snapshot;
    }

    /** Bounded metadata visitor; exposes no mutable collision geometry to query clients. */
    public static synchronized void visitWorldQuery(Level level,
            java.util.function.Predicate<Entry> visitor) {
        State state = LEVELS.get(level);
        if (state != null) for (Entry entry : state.entries.values()) if (!visitor.test(entry)) return;
    }

    public static synchronized Entry lookup(Level level, Object instanceId) {
        State state = LEVELS.get(level);
        return state == null ? null : state.entries.get(instanceId);
    }

    public static List<Candidate> query(Level level, AABB bounds) {
        return query(level, bounds, null);
    }

    public static List<Candidate> query(Level level, AABB bounds, UUID movingEntity) {
        List<Entry> entries = snapshots(level);
        if (entries.isEmpty()) return List.of();
        try (var sample = ModelCollisionMetrics.query(level.isClientSide())) {
        List<Candidate> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (movingEntity != null && movingEntity.equals(entry.excludedEntity)) continue;
            AABB local = bounds.move(-entry.x, -entry.y, -entry.z);
            entry.geometry.query(local, entry.x, entry.y, entry.z, result, sample.sampled() ? sample : null);
        }
        if (sample.sampled()) sample.candidates = result.size();
        return result;
        }
    }

    /** A resolved handle is tied to one live entry lifetime, across position/geometry refreshes. */
    public static MetadataCapabilities.Access metadata(ServerLevel level, Object instanceId) {
        RuntimeMetadataTargets.requireThread(level.getServer());
        return metadata(level, instanceId, GraphMetadataValuePolicy.of(level.getServer().registryAccess()), () -> {
            RuntimeMetadataTargets.requireThread(level.getServer());
            // Absence of the runtime anchor is dormancy, not destruction of metadata.
        });
    }

    static synchronized MetadataCapabilities.Access metadata(Level level, Object instanceId,
            com.mine.geometry_node.core.metadata.DataValuePolicy policy, Runnable requireThread) {
        requireThread.run();
        Definition entry = definition(level, instanceId);
        if (entry == null) throw new IllegalStateException("Collision no longer exists");
        RuntimeMetadata metadata = entry.metadata();
        return metadata.access(policy, () -> {
            requireThread.run();
            Definition current = definition(level, instanceId);
            if (current == null || current.metadata() != metadata)
                throw new IllegalStateException("Collision metadata handle is stale");
        });
    }

    public record Definition(Object instanceId, AABB bounds, double x, double y, double z,
                             UUID excludedEntity, RuntimeMetadata metadata) {}

    public record Entry(Object instanceId, Prepared geometry, double x, double y, double z,
                        UUID excludedEntity, RuntimeMetadata metadata) {
        public Entry(Object instanceId, Prepared geometry, double x, double y, double z, UUID excludedEntity) {
            this(instanceId, geometry, x, y, z, excludedEntity, new RuntimeMetadata());
        }
    }

    public record Candidate(ModelConvexCollider collider, double x, double y, double z) {
        public ModelConvexCollider.Hit sweep(AABB bounds, Vec3 movement) {
            return collider.sweep(bounds.move(-x, -y, -z), movement);
        }

        public boolean intersects(AABB bounds) {
            return collider.intersects(bounds.move(-x, -y, -z));
        }
    }

    public static final class Prepared {
        private final ModelCollisionMesh mesh;
        private final ModelConvexCollider[] colliders;
        private final Node root;
        private final long estimatedGeometryBytes;
        private final long estimatedIndexBytes;

        private Prepared(ModelCollisionMesh mesh, ModelCancellation cancellation) throws ModelProblem {
            this.mesh = mesh;
            if (mesh.parts().size() > ModelCollisionBuilder.MAX_PARTS) throw new IllegalArgumentException("collision part budget exceeded");
            long deadline = System.nanoTime() + 30_000_000_000L;
            colliders = new ModelConvexCollider[mesh.parts().size()];
            long geometryBytes = 0, indexBytes = colliders.length * 8L;
            for (int i = 0; i < colliders.length; i++) {
                cancellation.check();
                if (System.nanoTime() > deadline) throw new IllegalArgumentException("collision preparation time budget exceeded");
                ModelCollisionMesh.Part part = mesh.parts().get(i);
                colliders[i] = new ModelConvexCollider(part);
                geometryBytes += part.estimatedGeometryBytes();
                indexBytes += colliders[i].estimatedIndexBytes();
            }
            root = colliders.length == 0 ? null : build(0, colliders.length, cancellation, deadline);
            estimatedGeometryBytes = geometryBytes;
            estimatedIndexBytes = indexBytes + estimateTree(root);
        }

        /** Cached root bounds: O(1), no collider/triangle walk during metadata queries. */
        public AABB bounds() { return root == null ? null : root.bounds; }
        public ModelCollisionMesh mesh() { return mesh; }
        public long estimatedGeometryBytes() { return estimatedGeometryBytes; }
        public long estimatedIndexBytes() { return estimatedIndexBytes; }

        private static long estimateTree(Node node) {
            return node == null ? 0 : 112 + estimateTree(node.left) + estimateTree(node.right);
        }

        private Node build(int start, int end, ModelCancellation cancellation, long deadline) throws ModelProblem {
            cancellation.check();
            if (System.nanoTime() > deadline) throw new IllegalArgumentException("collision indexing time budget exceeded");
            AABB bounds = colliders[start].bounds();
            for (int i = start + 1; i < end; i++) bounds = bounds.minmax(colliders[i].bounds());
            if (end - start <= 8) return new Node(bounds, start, end, null, null);
            double dx = bounds.maxX - bounds.minX, dy = bounds.maxY - bounds.minY, dz = bounds.maxZ - bounds.minZ;
            int axis = dx >= dy && dx >= dz ? 0 : dy >= dz ? 1 : 2;
            Arrays.sort(colliders, start, end, Comparator.comparingDouble(c -> center(c.bounds(), axis)));
            int middle = (start + end) >>> 1;
            return new Node(bounds, start, end, build(start, middle, cancellation, deadline), build(middle, end, cancellation, deadline));
        }

        private static double center(AABB b, int axis) {
            return axis == 0 ? b.minX + b.maxX : axis == 1 ? b.minY + b.maxY : b.minZ + b.maxZ;
        }

        private void query(AABB box, double x, double y, double z, List<Candidate> result, ModelCollisionMetrics.Sample sample) {
            if (root != null) query(root, box, x, y, z, result, sample);
        }

        private void query(Node node, AABB box, double x, double y, double z, List<Candidate> result, ModelCollisionMetrics.Sample sample) {
            if (sample != null) sample.nodes++;
            if (!node.bounds.intersects(box)) return;
            if (node.left != null) {
                query(node.left, box, x, y, z, result, sample);
                query(node.right, box, x, y, z, result, sample);
            } else for (int i = node.start; i < node.end; i++) {
                if (sample != null) sample.partsTested++;
                if (colliders[i].bounds().intersects(box)) result.add(new Candidate(colliders[i], x, y, z));
            }
        }
    }

    private record Node(AABB bounds, int start, int end, Node left, Node right) {}
    private static final class State {
        private final Map<Object, Entry> entries = new LinkedHashMap<>();
        private final Map<Object, Definition> definitions = new LinkedHashMap<>();
        private List<Entry> snapshot = List.of();
        private boolean snapshotDirty;
        private long revision;
    }
}
