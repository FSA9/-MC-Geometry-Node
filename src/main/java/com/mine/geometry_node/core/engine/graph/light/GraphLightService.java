package com.mine.geometry_node.core.engine.graph.light;

import com.mine.geometry_node.core.engine.metadata.state.MetadataOwnerThread;

import com.mine.geometry_node.core.engine.metadata.state.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.lifecycle.ServerEngine;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketGraphLight;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.engine.system.image.ImagePathReference;
import com.mine.geometry_node.core.engine.system.image.ServerImageAssetService;
import com.mine.geometry_node.core.engine.graph.runtime.GraphEngineServices;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;
import java.util.function.UnaryOperator;

/** Server-owned lights; entity movement is interpolated locally rather than broadcast each tick. */
public final class GraphLightService implements ServerEngine {
    public static final GraphLightService INSTANCE = new GraphLightService();
    public static final int MAX_LIGHTS_PER_LEVEL = 256;
    private final Map<ServerLevel, Map<GraphResourceId, Resource>> levels = new IdentityHashMap<>();
    private boolean initialized;
    private volatile long acceptedCreates, unchangedCreates, sentUpserts, failedActions;
    private volatile String lastFailure = "";
    private volatile Map<String, Object> lastLight = Map.of();
    private final Set<GraphResourceId> loggedLights = new HashSet<>();
    private GraphLightService() {}
    @Override public String id() { return "geometry_node:light"; }
    @Override public int tickOrder() { return 110; }
    @Override public void init() {
        if (initialized) return;
        initialized = true;
        GraphResourceLifecycleManager.INSTANCE.registerStore("light", this::release);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
    }

    public boolean create(ServerLevel level, GraphResourceId id, LightDefinition definition) {
        return create(level, id, definition, null);
    }

    private boolean create(ServerLevel level, GraphResourceId id, LightDefinition definition, Long retainedCreated) {
        validate(level, id, definition);
        String path = definition.config().projection().path();
        if (path.toLowerCase(Locale.ROOT).startsWith("server:")) throw new IllegalArgumentException("Use remote:/ for server images");
        Map<GraphResourceId, Resource> resources = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (!resources.containsKey(id) && resources.size() >= MAX_LIGHTS_PER_LEVEL)
            throw new IllegalArgumentException("Light resource budget exceeded (256 per dimension)");
        Resource old = resources.get(id);
        if (old != null && !isLive(level, old)) {
            remove(level, id);
            resources = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
            old = null;
        }
        boolean unchanged = old != null && old.definition.equals(definition);
        ImagePathReference remote = !unchanged && "spot".equals(definition.type()) && path.toLowerCase(Locale.ROOT).startsWith("remote:")
                ? ImagePathReference.parse(path) : null;
        if (loggedLights.size() < 8 && loggedLights.add(id))
            com.mine.geometry_node.GeometryNode.LOGGER.info("Node light server create: resource={}, position={}, rotation={}, intensity={}, range={}, shadow={}",
                    GraphResourceIdCodec.encode(id), definition.position(), definition.rotation(), definition.intensity(),
                    definition.range(), definition.config().shadow().enabled());
        acceptedCreates++;
        lastLight = Map.of("resource", GraphResourceIdCodec.encode(id), "position", List.of(definition.position().x,
                definition.position().y, definition.position().z), "intensity", definition.intensity(),
                "range", definition.range(), "volume", definition.config().volume().mode());
        if (unchanged) {
            unchangedCreates++;
            old.created = retainedCreated == null ? level.getGameTime() : retainedCreated;
            refreshResidency(level, old);
            return true;
        }
        // Keep a pending image request (or its resolved asset) while other properties change.
        if (old != null && remote != null && "spot".equals(old.definition.type())
                && old.definition.config().projection().path().equals(path)) {
            old.updatePendingDefinition(definition, retainedCreated == null ? level.getGameTime() : retainedCreated);
            refreshResidency(level, old);
            return true;
        }
        Resource value = new Resource(id, definition, retainedCreated == null ? level.getGameTime() : retainedCreated);
        if (old != null && isLive(level, old)) value.metadata = old.metadata;
        if (old != null) {
            value.inheritPosition(old);
            retire(level, old);
        }
        resources.put(id, value);
        if (remote != null) {
            refreshResidency(level, value);
            ServerImageAssetService.INSTANCE.loadAsync(level.getServer(), remote.path()).whenComplete((asset, error) ->
                    level.getServer().execute(() -> {
                        Map<GraphResourceId, Resource> current = levels.get(level);
                        if (current == null || current.get(id) != value) return;
                        if (error != null) {
                            recordFailure("projection", error.getMessage());
                            remove(level, id);
                            com.mine.geometry_node.GeometryNode.LOGGER.warn("Unable to load light projection {}: {}", path, error.getMessage());
                            return;
                        }
                        if (value.acceptAsset(current.get(id), asset, level.getGameTime())) refreshResidency(level, value);
                        else remove(level, id);
                    }));
        } else {
            value.wire = definition;
            refreshResidency(level, value);
        }
        return true;
    }

    public boolean update(ServerLevel level, GraphResourceId id, LightDefinition definition) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null || !resources.containsKey(id)) return false;
        return create(level, id, definition);
    }

    /** Executes on the server thread; unrelated setters preserve the original expiry time. */
    public boolean mutate(ServerLevel level, GraphResourceId id, UnaryOperator<LightDefinition> change,
                          boolean restartLifetime) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        Resource current = resources == null ? null : resources.get(id);
        if (current == null) return false;
        if (!isLive(level, current)) { remove(level, id); return false; }
        LightDefinition next = Objects.requireNonNull(change.apply(current.definition));
        if (next.equals(current.definition) && !restartLifetime) return true;
        return create(level, id, next, restartLifetime ? null : current.created);
    }

    public boolean remove(ServerLevel level, GraphResourceId id) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        Resource resource = resources == null ? null : resources.remove(id);
        if (resource == null) return false;
        retire(level, resource);
        if (resources.isEmpty()) levels.remove(level);
        return true;
    }

    private static void retire(ServerLevel level, Resource resource) {
        resource.closed = true;
        if (resource.registration != null) resource.registration.close();
        if (resource.active) broadcastRemoval(level, resource.id);
        resource.active = false;
    }

    @Override public void tickLevel(ServerLevel level) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : List.copyOf(resources.values())) {
            if (!isLive(level, resource)) remove(level, resource.id);
            else refreshResidency(level, resource);
        }
    }

    private static void refreshResidency(ServerLevel level, Resource resource) {
        resource.position = resolvePosition(level, resource);
        AABB bounds = resource.residencyBounds();
        UUID anchor = liveEntity(resource.definition);
        var entity = anchor == null ? null : level.getEntity(anchor);
        int entityId = entity == null ? -1 : entity.getId();
        if (resource.entityId != entityId) { resource.entityId = entityId; resource.dirty = true; }
        if (resource.registration == null) {
            resource.registration = ResourceResidency.watch(level, bounds, anchor,
                    resident -> reconcile(level, resource, resident));
        } else {
            resource.registration.update(bounds, anchor);
        }
        reconcile(level, resource, resource.registration.isResident());
    }

    private static void reconcile(ServerLevel level, Resource resource, boolean resident) {
        boolean wasActive = resource.active;
        int operation = resource.publicationOperation(resident, level.getGameTime());
        if (operation == PacketGraphLight.REMOVE) broadcastRemoval(level, resource.id);
        else if (operation == PacketGraphLight.UPSERT) broadcast(level, resource, !wasActive);
    }

    /** Conservative influence coverage, including the emitting surface and local transform offset. */
    public static AABB worldBounds(LightDefinition definition, Vec3 position) {
        return new AABB(position, position).inflate(definition.range() + definition.emitterRadius());
    }

    public static UUID liveEntity(LightDefinition definition) {
        var anchor=definition.config().transform().anchor();
        return anchor.requiresEntity()?anchor.entityId():null;
    }

    /** Conservative initial position; entity orientation is resolved at runtime. */
    public static Vec3 localPosition(LightDefinition definition) {
        var anchor=definition.config().transform().anchor();
        Vec3 base=anchor.followPosition()?Vec3.ZERO:anchor.position();
        return spatialTransform(definition).position(base,new org.joml.Quaterniond());
    }

    public static com.mine.geometry_node.core.engine.spatial.SpatialTransform spatialTransform(LightDefinition definition) {
        return new com.mine.geometry_node.core.engine.spatial.SpatialTransform(definition.position(),definition.rotation(),
            definition.config().transform().position(),definition.config().transform().rotation());
    }

    private static Vec3 resolvePosition(ServerLevel level, Resource resource) {
        var anchor=resource.definition.config().transform().anchor();
        Vec3 base=com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolve(anchor,level);
        var rotation=com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolveRotation(anchor,level);
        Vec3 position=base==null||rotation==null?null:spatialTransform(resource.definition).position(base,rotation);
        return resource.recordPosition(anchor.requiresEntity()?anchor.entityId():null,position);
    }

    private static void broadcastRemoval(ServerLevel level, GraphResourceId id) {
        NetworkHandler.sendToPlayers(level.players(), new PacketGraphLight(level.dimension().identifier(),
                GraphResourceIdCodec.encode(id), PacketGraphLight.REMOVE, -1, null));
    }

    public MetadataCapabilities.Access metadata(ServerLevel level, GraphResourceId id) {
        MetadataOwnerThread.requireCurrent(level.getServer());
        var resources = levels.get(level);
        Resource resource = resources == null ? null : resources.get(id);
        if (resource == null || !isLive(level, resource)) throw new IllegalStateException("Light no longer exists");
        RuntimeMetadata metadata = resource.metadata;
        return metadata.access(GraphMetadataValuePolicy.of(level.getServer().registryAccess()), () -> {
            MetadataOwnerThread.requireCurrent(level.getServer());
            var currentResources = levels.get(level);
            Resource current = currentResources == null ? null : currentResources.get(id);
            if (current == null || current.metadata != metadata || !isLive(level, current))
                throw new IllegalStateException("Light metadata handle is stale");
        });
    }

    public long metadataRevision(ServerLevel level, GraphResourceId id) {
        metadata(level, id);
        return levels.get(level).get(id).metadata.revision();
    }

    private static boolean isLive(ServerLevel level, Resource resource) {
        return resource.exists(level.getGameTime());
    }

    public record QuerySnapshot(GraphResourceId id, LightDefinition definition, Vec3 position) {}
    /** Includes disabled/dormant lights with a known world pose; never-resolved attachments have no spatial row. */
    public void visitWorldQuery(ServerLevel level, java.util.function.Predicate<QuerySnapshot> visitor) {
        var resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : resources.values()) {
            if (!isLive(level, resource)) continue;
            resource.position = resolvePosition(level, resource);
            QuerySnapshot snapshot = resource.snapshot(level.getGameTime());
            if (snapshot != null && !visitor.test(snapshot)) return;
        }
    }

    private void synchronize(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        NetworkHandler.sendToPlayer(player, new PacketGraphLight(level.dimension().identifier(), "", PacketGraphLight.CLEAR, -1, null));
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources != null) for (Resource resource : resources.values()) send(player, level, resource);
    }

    private static void validate(ServerLevel level, GraphResourceId id, LightDefinition definition) {
        Objects.requireNonNull(definition);
        if (!GraphResourceTypeRegistry.LIGHT.equals(id.type()) || !level.dimension().equals(id.scope().dimension()))
            throw new IllegalArgumentException("Invalid light resource scope");
        if (!definition.config().transform().anchor().dimensionOr(level.dimension()).equals(level.dimension()))
            throw new IllegalArgumentException("Light anchor dimension does not match its resource scope");
        // UUID attachments may be authored or mutated while their entity is unloaded.
        // Residency, not existence, follows the entity in this resource's fixed dimension.
    }

    private static PacketGraphLight packet(ServerLevel level, Resource resource) {
        UUID anchor = liveEntity(resource.definition);
        var entity = anchor == null ? null : level.getEntity(anchor);
        return new PacketGraphLight(level.dimension().identifier(), GraphResourceIdCodec.encode(resource.id),
                PacketGraphLight.UPSERT, entity == null ? -1 : entity.getId(), resource.wire);
    }
    private static void broadcast(ServerLevel level, Resource resource, boolean includeAsset) {
        var players = level.players();
        if (resource.wire == null || players.isEmpty()) return;
        PacketGraphLight lightPacket = packet(level, resource);
        PacketVisualAssetData assetPacket = includeAsset && resource.asset != null
                ? new PacketVisualAssetData(resource.asset.assetId(), resource.asset.data()) : null;
        for (ServerPlayer player : players) {
            if (assetPacket != null) NetworkHandler.sendToPlayer(player, assetPacket);
            NetworkHandler.sendToPlayer(player, lightPacket);
            INSTANCE.sentUpserts++;
        }
    }
    private static void send(ServerPlayer player, ServerLevel level, Resource resource) {
        // A queued activation will broadcast to all players. Do not publish an untracked client copy here.
        if (!resource.active || !resource.exists(level.getGameTime()) || !resource.definition.config().lifecycle().enabled()
                || resource.wire == null || !ResourceResidency.isResident(level,
                resource.residencyBounds(), liveEntity(resource.definition))) return;
        if (resource.asset != null) NetworkHandler.sendToPlayer(player,
                new PacketVisualAssetData(resource.asset.assetId(), resource.asset.data()));
        NetworkHandler.sendToPlayer(player, packet(level, resource));
        INSTANCE.sentUpserts++;
    }
    private static LightDefinition withAsset(LightDefinition definition, GraphEngineServices.VisualAsset asset) {
        var projection = definition.config().projection();
        return new LightDefinition(definition.type(), definition.color(), definition.intensity(), definition.range(),
                definition.coneAngle(), definition.edgeSoftness(), definition.position(), definition.rotation(),
                definition.config().withProjection(new LightConfig.Projection("server:" + asset.assetId(),
                        projection.colored(), projection.rotation(), projection.scale())));
    }
    private void release(MinecraftServer server, GraphResourceRelease release) {
        for (ServerLevel level : List.copyOf(levels.keySet())) {
            if (level.getServer() != server) continue;
            for (Resource resource : List.copyOf(levels.get(level).values())) {
                boolean anchorRemoved = release instanceof GraphResourceRelease.Entity entity
                        && entity.dimension().equals(level.dimension())
                        && entity.entityId().equals(liveEntity(resource.definition));
                if (release.matches(resource.id) || anchorRemoved) remove(level, resource.id);
            }
        }
    }
    @Override public void levelUnloaded(ServerLevel level) {
        release(level.getServer(), new GraphResourceRelease.LevelScope(level.dimension()));
    }
    @Override public void shutdown(MinecraftServer server) { release(server, GraphResourceRelease.Server.INSTANCE); }
    public void recordFailure(String action, String reason) {
        failedActions++;
        lastFailure = action + ": " + reason;
    }
    /** Immutable values only: the render thread must not inspect the server-owned resource maps. */
    public Map<String, Object> diagnostics() {
        return Map.of("scope", "this_process_lifetime", "acceptedCreates", acceptedCreates,
                "unchangedCreates", unchangedCreates, "sentUpserts", sentUpserts,
                "failedActions", failedActions, "lastFailure", lastFailure, "lastLight", lastLight);
    }
    static final class Resource {
        final GraphResourceId id;
        RuntimeMetadata metadata = new RuntimeMetadata();
        LightDefinition definition;
        long created;
        LightDefinition wire;
        GraphEngineServices.VisualAsset asset;
        ResourceResidency.Registration registration;
        Vec3 position;
        UUID positionAnchor;
        boolean hasResolvedWorldPosition;
        int entityId = -1;
        boolean active, closed;
        boolean dirty = true;

        /** A null pose means the current anchor has never resolved in this dimension. */
        Vec3 recordPosition(UUID anchor, Vec3 resolvedWorldPosition) {
            if (!Objects.equals(positionAnchor, anchor)) {
                position = null;
                hasResolvedWorldPosition = false;
            }
            positionAnchor = anchor;
            if (resolvedWorldPosition != null) {
                position = resolvedWorldPosition;
                hasResolvedWorldPosition = true;
            }
            return position;
        }

        void inheritPosition(Resource previous) {
            UUID anchor = liveEntity(definition);
            if (anchor != null && anchor.equals(previous.positionAnchor) && previous.hasResolvedWorldPosition) {
                position = previous.position;
                hasResolvedWorldPosition = true;
            }
        }

        AABB residencyBounds() {
            // Anchored residency ignores spatial coverage; the placeholder is never a query position.
            return worldBounds(definition, hasResolvedWorldPosition ? position : Vec3.ZERO);
        }

        QuerySnapshot snapshot(long now) {
            return !exists(now) || !hasResolvedWorldPosition
                    || !Objects.equals(positionAnchor, liveEntity(definition))
                    ? null : new QuerySnapshot(id, definition, position);
        }

        void updatePendingDefinition(LightDefinition next, long created) {
            definition = next;
            this.created = created;
            UUID anchor = liveEntity(next);
            recordPosition(anchor, anchor == null ? localPosition(next) : null);
            wire = asset == null ? null : withAsset(next, asset);
            dirty = true;
        }

        boolean acceptAsset(Resource current, GraphEngineServices.VisualAsset resolved, long now) {
            if (current != this || !exists(now)) return false;
            asset = resolved;
            wire = withAsset(definition, resolved);
            dirty = true;
            return true;
        }

        boolean exists(long now) {
            int duration = definition.config().lifecycle().durationTicks();
            return !closed && (duration <= 0 || now - created < duration);
        }

        int publicationOperation(boolean resident, long now) {
            boolean desired = exists(now) && definition.config().lifecycle().enabled() && wire != null && resident;
            if (!desired) {
                if (!active) return -1;
                active = false;
                return PacketGraphLight.REMOVE;
            }
            if (active && !dirty) return -1;
            active = true;
            dirty = false;
            return PacketGraphLight.UPSERT;
        }

        Resource(GraphResourceId id, LightDefinition definition, long created) {
            this.id = id; this.definition = definition; this.created = created;
            this.positionAnchor = liveEntity(definition);
            this.hasResolvedWorldPosition = positionAnchor == null;
            this.position = hasResolvedWorldPosition ? localPosition(definition) : null;
        }
    }
}
