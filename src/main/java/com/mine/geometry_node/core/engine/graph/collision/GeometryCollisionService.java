package com.mine.geometry_node.core.engine.graph.collision;

import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import org.joml.Quaterniond;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver;

import com.mine.geometry_node.core.engine.metadata.state.MetadataOwnerThread;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.ServerExpressionBindingResolver;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.lifecycle.ServerEngine;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.metadata.state.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryCollision;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/** Binding-owned primitive colliders. All mutations happen on the server thread. */
public final class GeometryCollisionService implements ServerEngine {
    public static final GeometryCollisionService INSTANCE = new GeometryCollisionService();
    private static final int MAX_RESOURCES_PER_LEVEL = 1024;
    private final Map<ServerLevel, Map<GraphResourceId, Resource>> levels = new IdentityHashMap<>();
    private boolean initialized;

    private GeometryCollisionService() {}
    @Override public String id() { return "geometry_node:geometry_collision"; }
    @Override public int tickOrder() { return 100; }

    @Override public void init() {
        if (initialized) return;
        initialized = true;
        GraphResourceLifecycleManager.INSTANCE.registerStore("geometry_collision", this::release);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
    }

    /** Position already contains the world base or the live entity-relative offset. */
    public void create(ServerLevel level, GraphResourceId id, GeometryValue geometry, SpatialAnchor anchor,
                       LiveValue<Vec3> position, LiveValue<Vec3> worldRotation, LiveValue<Vec3> pivot,
                       LiveValue<Vec3> translation, LiveValue<Vec3> rotation, LiveValue<Vec3> scale) throws ModelProblem {
        requireType(id);
        if (!id.scope().dimension().equals(level.dimension())) throw new IllegalArgumentException("collision dimension mismatch");
        if (!java.util.Objects.requireNonNull(anchor, "anchor").dimensionOr(level.dimension()).equals(level.dimension()))
            throw new IllegalArgumentException("collision anchor is not in this level");
        Map<GraphResourceId, Resource> resources = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (!resources.containsKey(id) && resources.size() >= MAX_RESOURCES_PER_LEVEL)
            throw new IllegalArgumentException("geometry collision resource budget exceeded");
        anchor = SpatialAnchorResolver.capture(anchor, level);
        if (anchor == null) throw new IllegalArgumentException("collision anchor is unavailable");
        Resource resource = new Resource(id, geometry, anchor.withDimension(level.dimension()), level.getGameTime(),
                List.of(position.newState(), worldRotation.newState(), pivot.newState(), translation.newState(),
                        rotation.newState(), scale.newState()));
        resolve(level, resource);
        if (resource.prepared == null) throw new IllegalArgumentException("collision anchor is not available");
        Resource previous = resources.get(id);
        if (previous != null) {
            resource.metadata = previous.metadata;
            resource.enabled = previous.enabled;
            retire(level, previous);
        }
        resources.put(id, resource);
        refreshResidency(level, resource);
    }

    public boolean remove(ServerLevel level, GraphResourceId id) {
        requireType(id);
        Map<GraphResourceId, Resource> resources = levels.get(level);
        Resource resource = resources == null ? null : resources.remove(id);
        if (resource == null) return false;
        retire(level, resource);
        if (resources.isEmpty()) levels.remove(level);
        return true;
    }

    @Override public void tickLevel(ServerLevel level) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : List.copyOf(resources.values())) {
            try {
                update(level, resource);
            } catch (ModelProblem | RuntimeException exception) {
                // Invalid live geometry must not leave an invisible collider at its previous position.
                remove(level, resource.id);
                GeometryNode.LOGGER.warn("Removed invalid geometry collision {}: {}", resource.id, exception.getMessage());
            }
        }
    }

    /** Manual disable retains the definition, live inputs and metadata. */
    public boolean setEnabled(ServerLevel level, GraphResourceId id, boolean enabled) {
        var resources = levels.get(level);
        Resource resource = resources == null ? null : resources.get(id);
        if (resource == null) return false;
        resource.enabled = enabled;
        if (!enabled) {
            reconcile(level, resource, false);
            return true;
        }
        try {
            update(level, resource);
            return true;
        } catch (ModelProblem | RuntimeException failure) {
            remove(level, id);
            GeometryNode.LOGGER.warn("Removed invalid geometry collision {}: {}", id, failure.getMessage());
            return false;
        }
    }

    private static void retire(ServerLevel level, Resource resource) {
        resource.closed = true;
        if (resource.registration != null) resource.registration.close();
        reconcile(level, resource, false);
    }

    void update(ServerLevel level, Resource resource) throws ModelProblem {
        // Disable suspends evaluation too: transient invalid live inputs must not destroy the definition.
        if (resource.closed || !resource.enabled) {
            reconcile(level, resource, false);
            return;
        }
        // Missing anchors suspend the resource; evaluating them must not delete the retained definition.
        if (resource.anchorEntityId() != null && !ResourceResidency.isEntityResident(level, resource.anchorEntityId())) {
            reconcile(level, resource, false);
            return;
        }
        resource.updating = true;
        try {
            resolve(level, resource);
            refreshResidency(level, resource);
        } finally {
            resource.updating = false;
        }
    }

    static void resolve(ServerLevel level, Resource resource) throws ModelProblem {
        if (resource.closed || !resource.enabled) return;
        long time = level.getGameTime();
        var context = new ExpressionEvaluationContext(time, Math.max(0, time - resource.created),
                new ServerExpressionBindingResolver(level.getServer()));
        Vec3 anchorPosition = SpatialAnchorResolver.resolve(resource.spatialAnchor, level);
        Quaterniond anchorRotation = SpatialAnchorResolver.resolveRotation(resource.spatialAnchor, level);
        if (anchorPosition == null || anchorRotation == null) return;
        resolve(resource, context, anchorPosition, anchorRotation);
    }

    static void resolve(Resource resource, ExpressionEvaluationContext context, Vec3 anchorPosition) throws ModelProblem {
        resolve(resource, context, anchorPosition == null ? Vec3.ZERO : anchorPosition, new Quaterniond());
    }

    static void resolve(Resource resource, ExpressionEvaluationContext context, Vec3 anchorPosition,
                        Quaterniond anchorRotation) throws ModelProblem {
        if (resource.closed || !resource.enabled) return;
        List<Vec3> values = resource.values.stream().map(value -> value.evaluate(context)).toList();
        for (Vec3 value : values) {
            if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z))
                throw new IllegalArgumentException("nonfinite geometry collision transform");
        }
        Vec3 position = anchorPosition.add(values.getFirst());
        Quaterniond outer = SpatialTransform.outerRotation(values.get(1), anchorRotation);
        boolean shapeChanged = resource.resolved == null || !values.subList(1, 6).equals(resource.resolved.subList(1, 6))
                || !outer.equals(resource.resolvedOuterRotation);
        if (shapeChanged) {
            var mesh = GeometryCollisionMeshBuilder.build(resource.geometry, outer, values.get(2),
                    values.get(3), values.get(4), values.get(5));
            PacketGeometryCollision.validateMesh(mesh);
            resource.prepared = ModelCollisionScene.prepare(mesh);
            resource.resolvedOuterRotation = new Quaterniond(outer);
        }
        resource.shapeDirty |= shapeChanged;
        resource.positionDirty |= !position.equals(resource.position);
        resource.position = position;
        resource.resolved = values;
    }

    private void refreshResidency(ServerLevel level, Resource resource) {
        if (resource.registration == null) {
            resource.registration = ResourceResidency.watch(level, resource.bounds(), resource.anchorEntityId(), resident -> {
                if (resource.closed) return;
                if (!resident || resource.updating) { reconcile(level, resource, resident); return; }
                try {
                    // Entity reloads must publish the new pose, not the last dormant pose.
                    update(level, resource);
                } catch (ModelProblem | RuntimeException failure) {
                    remove(level, resource.id);
                    GeometryNode.LOGGER.warn("Removed invalid geometry collision {}: {}", resource.id, failure.getMessage());
                }
            });
        } else {
            resource.registration.update(resource.bounds(), resource.anchorEntityId());
        }
        reconcile(level, resource, resource.registration.isResident());
    }

    private static void reconcile(ServerLevel level, Resource resource, boolean resident) {
        int operation = resource.publicationOperation(resident);
        if (operation < 0) return;
        if (operation == PacketGeometryCollision.REMOVE) {
            ModelCollisionScene.remove(level, resource.id);
            NetworkHandler.sendToPlayers(level.players(), packet(level, resource.id, operation, Vec3.ZERO, null));
            return;
        }
        Vec3 position = resource.position;
        ModelCollisionScene.publish(level, resource.id, resource.prepared, position.x, position.y, position.z, resource.anchorEntityId());
        NetworkHandler.sendToPlayers(level.players(), new PacketGeometryCollision(level.dimension().identifier(),
                GraphResourceIdCodec.encode(resource.id), operation, position,
                operation == PacketGeometryCollision.UPSERT ? resource.prepared.mesh() : null,
                operation == PacketGeometryCollision.UPSERT ? resource.anchorEntityId() : null));
    }

    public MetadataCapabilities.Access metadata(ServerLevel level, GraphResourceId id) {
        MetadataOwnerThread.requireCurrent(level.getServer());
        var resources = levels.get(level);
        Resource resource = resources == null ? null : resources.get(id);
        if (resource == null || resource.closed) throw new IllegalStateException("Collision no longer exists");
        return resource.metadataAccess(GraphMetadataValuePolicy.of(level.getServer().registryAccess()), () -> {
            var currentResources = levels.get(level);
            return currentResources == null ? null : currentResources.get(id);
        }, () -> MetadataOwnerThread.requireCurrent(level.getServer()));
    }

    public record QuerySnapshot(GraphResourceId id, Vec3 position, AABB bounds, RuntimeMetadata metadata,
                                boolean enabled, boolean active) {
        public List<String> tags() { return metadata.tagSnapshot(); }
    }

    /** Owner-thread visitor includes dormant and disabled resources, with their last resolved anchor pose. */
    public void visitWorldQuery(ServerLevel level, java.util.function.Predicate<QuerySnapshot> visitor) {
        var resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : resources.values()) {
            if (!resource.closed && !visitor.test(new QuerySnapshot(resource.id, resource.position,
                    resource.bounds(), resource.metadata, resource.enabled, resource.active))) return;
        }
    }

    private void synchronize(ServerPlayer player) {
        Map<GraphResourceId, Resource> resources = levels.get(player.level());
        if (resources == null) return;
        for (Resource resource : resources.values()) {
            // A queued activation will broadcast; every sent copy must be covered by active teardown.
            if (!resource.active || resource.closed || !resource.enabled || resource.prepared == null
                    || !ResourceResidency.isResident(player.level(), resource.bounds(), resource.anchorEntityId())) continue;
            NetworkHandler.sendToPlayer(player, new PacketGeometryCollision(player.level().dimension().identifier(),
                    GraphResourceIdCodec.encode(resource.id), PacketGeometryCollision.UPSERT,
                    resource.position, resource.prepared.mesh(), resource.anchorEntityId()));
        }
    }

    private void release(MinecraftServer server, GraphResourceRelease release) {
        for (ServerLevel level : List.copyOf(levels.keySet())) {
            if (level.getServer() != server) continue;
            for (Resource resource : List.copyOf(levels.get(level).values())) {
                boolean anchorRemoved = release instanceof GraphResourceRelease.Entity entity
                        && entity.dimension().equals(level.dimension()) && entity.entityId().equals(resource.anchorEntityId());
                if (release.matches(resource.id) || anchorRemoved) remove(level, resource.id);
            }
        }
    }

    @Override public void levelUnloaded(ServerLevel level) {
        release(level.getServer(), new GraphResourceRelease.LevelScope(level.dimension()));
    }
    @Override public void shutdown(MinecraftServer server) { release(server, GraphResourceRelease.Server.INSTANCE); }

    private static PacketGeometryCollision packet(ServerLevel level, GraphResourceId id, int operation, Vec3 position,
            com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh mesh) {
        return new PacketGeometryCollision(level.dimension().identifier(), GraphResourceIdCodec.encode(id), operation, position, mesh);
    }
    private static void requireType(GraphResourceId id) {
        if (!GraphResourceTypeRegistry.GEOMETRY_COLLISION.equals(id.type()))
            throw new IllegalArgumentException("resource is not a geometry collision");
    }
    static final class Resource {
        final GraphResourceId id;
        final GeometryValue geometry;
        final SpatialAnchor spatialAnchor;
        final long created;
        final List<LiveValue.State<Vec3>> values;
        List<Vec3> resolved;
        Quaterniond resolvedOuterRotation;
        Vec3 position;
        ModelCollisionScene.Prepared prepared;
        RuntimeMetadata metadata = new RuntimeMetadata();
        ResourceResidency.Registration registration;
        boolean enabled = true;
        boolean active, closed, shapeDirty, positionDirty, updating;

        MetadataCapabilities.Access metadataAccess(com.mine.geometry_node.core.metadata.DataValuePolicy policy,
                java.util.function.Supplier<Resource> current, Runnable requireThread) {
            RuntimeMetadata captured = metadata;
            return captured.access(policy, () -> {
                requireThread.run();
                Resource value = current.get();
                if (value == null || value.closed || value.metadata != captured)
                    throw new IllegalStateException("Collision metadata handle is stale");
            });
        }

        AABB bounds() {
            return prepared.bounds().move(position);
        }

        int publicationOperation(boolean resident) {
            if (closed || !enabled || !resident || prepared == null) {
                if (!active) return -1;
                active = false;
                return PacketGeometryCollision.REMOVE;
            }
            int operation = !active || shapeDirty ? PacketGeometryCollision.UPSERT
                    : positionDirty ? PacketGeometryCollision.MOVE : -1;
            active = true;
            shapeDirty = false;
            positionDirty = false;
            return operation;
        }

        @Nullable UUID anchorEntityId() { return spatialAnchor.requiresEntity() ? spatialAnchor.entityId() : null; }

        Resource(GraphResourceId id, GeometryValue geometry, SpatialAnchor anchor, long created,
                 List<LiveValue.State<Vec3>> values) {
            this.id = id;
            this.geometry = geometry;
            this.spatialAnchor = java.util.Objects.requireNonNull(anchor, "anchor");
            this.created = created;
            this.values = List.copyOf(values);
        }

    }
}
