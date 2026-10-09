package com.mine.geometry_node.core.engine.graph.collision;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.ServerExpressionBindingResolver;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
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

    public void create(ServerLevel level, GraphResourceId id, GeometryValue geometry, Entity anchor,
                       LiveValue<Vec3> position, LiveValue<Vec3> worldRotation, LiveValue<Vec3> pivot,
                       LiveValue<Vec3> translation, LiveValue<Vec3> rotation, LiveValue<Vec3> scale) throws ModelProblem {
        requireType(id);
        if (!id.scope().dimension().equals(level.dimension())) throw new IllegalArgumentException("collision dimension mismatch");
        if (anchor != null && (anchor.level() != level || anchor.isRemoved()))
            throw new IllegalArgumentException("collision anchor is not in this level");
        Map<GraphResourceId, Resource> resources = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (!resources.containsKey(id) && resources.size() >= MAX_RESOURCES_PER_LEVEL)
            throw new IllegalArgumentException("geometry collision resource budget exceeded");
        Resource resource = new Resource(id, geometry, anchor == null ? null : anchor.getUUID(), level.getGameTime(),
                List.of(position.newState(), worldRotation.newState(), pivot.newState(), translation.newState(),
                        rotation.newState(), scale.newState()));
        update(level, resource);
        resources.put(id, resource);
    }

    public boolean remove(ServerLevel level, GraphResourceId id) {
        requireType(id);
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null || resources.remove(id) == null) return false;
        ModelCollisionScene.remove(level, id);
        NetworkHandler.sendToPlayers(level.players(), packet(level, id, PacketGeometryCollision.REMOVE, Vec3.ZERO, null));
        if (resources.isEmpty()) levels.remove(level);
        return true;
    }

    @Override public void tickLevel(ServerLevel level) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : List.copyOf(resources.values())) {
            if (resource.anchor != null && level.getEntity(resource.anchor) == null) {
                remove(level, resource.id);
                continue;
            }
            try {
                update(level, resource);
            } catch (ModelProblem | RuntimeException exception) {
                // Invalid live geometry must not leave an invisible collider at its previous position.
                remove(level, resource.id);
                GeometryNode.LOGGER.warn("Removed invalid geometry collision {}: {}", resource.id, exception.getMessage());
            }
        }
    }

    private void update(ServerLevel level, Resource resource) throws ModelProblem {
        long time = level.getGameTime();
        var context = new ExpressionEvaluationContext(time, Math.max(0, time - resource.created),
                new ServerExpressionBindingResolver(level.getServer()));
        List<Vec3> values = resource.values.stream().map(value -> value.evaluate(context)).toList();
        for (Vec3 value : values) {
            if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z))
                throw new IllegalArgumentException("nonfinite geometry collision transform");
        }
        Vec3 position = values.getFirst();
        if (resource.anchor != null) {
            Entity entity = level.getEntity(resource.anchor);
            if (entity == null || entity.isRemoved()) throw new IllegalArgumentException("missing collision anchor");
            position = entity.position().add(position);
        }
        boolean shapeChanged = resource.resolved == null || !values.subList(1, 6).equals(resource.resolved.subList(1, 6));
        if (!shapeChanged && position.equals(resource.position)) return;
        ModelCollisionScene.Prepared prepared = resource.prepared;
        if (shapeChanged) {
            var mesh = GeometryCollisionMeshBuilder.build(resource.geometry, values.get(1), values.get(2),
                    values.get(3), values.get(4), values.get(5));
            PacketGeometryCollision.validateMesh(mesh);
            prepared = ModelCollisionScene.prepare(mesh);
        }
        ModelCollisionScene.publish(level, resource.id, prepared, position.x, position.y, position.z, resource.anchor);
        resource.position = position;
        resource.resolved = values;
        resource.prepared = prepared;
        NetworkHandler.sendToPlayers(level.players(), new PacketGeometryCollision(level.dimension().identifier(),
                GraphResourceIdCodec.encode(resource.id),
                shapeChanged ? PacketGeometryCollision.UPSERT : PacketGeometryCollision.MOVE, position,
                shapeChanged ? prepared.mesh() : null, shapeChanged ? resource.anchor : null));
    }

    private void synchronize(ServerPlayer player) {
        Map<GraphResourceId, Resource> resources = levels.get(player.level());
        if (resources == null) return;
        for (Resource resource : resources.values()) {
            NetworkHandler.sendToPlayer(player, new PacketGeometryCollision(player.level().dimension().identifier(),
                    GraphResourceIdCodec.encode(resource.id), PacketGeometryCollision.UPSERT,
                    resource.position, resource.prepared.mesh(), resource.anchor));
        }
    }

    private void release(MinecraftServer server, GraphResourceRelease release) {
        for (ServerLevel level : List.copyOf(levels.keySet())) {
            if (level.getServer() != server) continue;
            for (Resource resource : List.copyOf(levels.get(level).values())) {
                boolean anchorRemoved = release instanceof GraphResourceRelease.Entity entity
                        && entity.dimension().equals(level.dimension()) && entity.entityId().equals(resource.anchor);
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
    private static final class Resource {
        final GraphResourceId id;
        final GeometryValue geometry;
        final UUID anchor;
        final long created;
        final List<LiveValue.State<Vec3>> values;
        List<Vec3> resolved;
        Vec3 position;
        ModelCollisionScene.Prepared prepared;
        Resource(GraphResourceId id, GeometryValue geometry, UUID anchor, long created, List<LiveValue.State<Vec3>> values) {
            this.id = id; this.geometry = geometry; this.anchor = anchor; this.created = created; this.values = values;
        }
    }
}
