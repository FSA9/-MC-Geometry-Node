package com.mine.geometry_node.core.engine.graph.light;

import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketGraphLight;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.engine.system.visual.image.ImagePathReference;
import com.mine.geometry_node.core.engine.system.visual.image.ServerImageAssetService;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
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
        validate(level, id, definition);
        String path = definition.config().projection().path();
        if (path.toLowerCase(Locale.ROOT).startsWith("server:")) throw new IllegalArgumentException("Use remote:/ for server images");
        Map<GraphResourceId, Resource> resources = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (!resources.containsKey(id) && resources.size() >= MAX_LIGHTS_PER_LEVEL)
            throw new IllegalArgumentException("Light resource budget exceeded (256 per dimension)");
        Resource old = resources.get(id);
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
            old.created = level.getGameTime();
            return true;
        }
        // Keep a pending image request (or its resolved asset) while other properties change.
        if (old != null && remote != null && "spot".equals(old.definition.type())
                && old.definition.config().projection().path().equals(path)) {
            old.definition = definition;
            old.created = level.getGameTime();
            if (old.asset != null) {
                old.wire = withAsset(definition, old.asset);
                broadcast(level, old, false);
            }
            return true;
        }
        Resource value = new Resource(id, definition, level.getGameTime());
        resources.put(id, value);
        if (remote != null) {
            if (old != null) NetworkHandler.sendToPlayers(level.players(), new PacketGraphLight(level.dimension().identifier(),
                    GraphResourceIdCodec.encode(id), PacketGraphLight.REMOVE, -1, null));
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
                        value.asset = asset;
                        value.wire = withAsset(value.definition, asset);
                        broadcast(level, value);
                    }));
        } else {
            value.wire = definition;
            broadcast(level, value);
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
        LightDefinition next = Objects.requireNonNull(change.apply(current.definition));
        if (next.equals(current.definition) && !restartLifetime) return true;
        long created = current.created;
        boolean success = create(level, id, next);
        if (success && !restartLifetime) resources.get(id).created = created;
        return success;
    }

    public boolean remove(ServerLevel level, GraphResourceId id) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null || resources.remove(id) == null) return false;
        broadcastRemoval(level, id);
        if (resources.isEmpty()) levels.remove(level);
        return true;
    }

    @Override public void tickLevel(ServerLevel level) {
        Map<GraphResourceId, Resource> resources = levels.get(level);
        if (resources == null) return;
        long now = level.getGameTime();
        for (Iterator<Resource> iterator = resources.values().iterator(); iterator.hasNext();) {
            Resource resource = iterator.next();
            var config = resource.definition.config();
            UUID anchor = config.transform().entityId();
            var entity = anchor == null ? null : level.getEntity(anchor);
            int duration = config.lifecycle().durationTicks();
            if ((anchor != null && (entity == null || entity.isRemoved()))
                    || (duration > 0 && now - resource.created >= duration)) {
                iterator.remove();
                broadcastRemoval(level, resource.id);
            }
        }
        if (resources.isEmpty()) levels.remove(level);
    }

    private static void broadcastRemoval(ServerLevel level, GraphResourceId id) {
        NetworkHandler.sendToPlayers(level.players(), new PacketGraphLight(level.dimension().identifier(),
                GraphResourceIdCodec.encode(id), PacketGraphLight.REMOVE, -1, null));
    }

    public record QuerySnapshot(GraphResourceId id, LightDefinition definition, Vec3 position) {}
    /** Server-thread immutable metadata; no asset bytes or client state. */
    public void visitWorldQuery(ServerLevel level, java.util.function.Predicate<QuerySnapshot> visitor) {
        var resources = levels.get(level);
        if (resources == null) return;
        for (Resource resource : resources.values()) {
            var definition = resource.definition;
            var anchorId = definition.config().transform().entityId();
            var anchor = anchorId == null ? null : level.getEntity(anchorId);
            if (anchorId != null && (anchor == null || anchor.isRemoved())) continue;
            int duration = definition.config().lifecycle().durationTicks();
            if (duration > 0 && level.getGameTime() - resource.created >= duration) continue;
            var transform = definition.config().transform();
            var baseRotation = new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(-definition.rotation().y),
                    (float)Math.toRadians(definition.rotation().x), (float)Math.toRadians(definition.rotation().z));
            var local = baseRotation.transform(new org.joml.Vector3f((float)transform.position().x,
                    (float)transform.position().y, (float)transform.position().z));
            Vec3 position = definition.position().add(local.x, local.y, local.z);
            if (anchor != null) {
                float yaw = !transform.followView() && anchor instanceof net.minecraft.world.entity.LivingEntity living
                        ? living.yBodyRot : anchor.getYRot();
                var anchorRotation = new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(-yaw),
                        transform.followView() ? (float)Math.toRadians(anchor.getXRot()) : 0, 0);
                var offset = anchorRotation.transform(new org.joml.Vector3f((float)position.x, (float)position.y, (float)position.z));
                position = (transform.eyeAnchor() ? anchor.getEyePosition() : anchor.position()).add(offset.x, offset.y, offset.z);
            }
            if (!visitor.test(new QuerySnapshot(resource.id, definition, position))) return;
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
        UUID anchor = definition.config().transform().entityId();
        var entity = anchor == null ? null : level.getEntity(anchor);
        if (anchor != null && (entity == null || entity.isRemoved()))
            throw new IllegalArgumentException("Light anchor is not in this level");
    }

    private static PacketGraphLight packet(ServerLevel level, Resource resource) {
        UUID anchor = resource.definition.config().transform().entityId();
        var entity = anchor == null ? null : level.getEntity(anchor);
        return new PacketGraphLight(level.dimension().identifier(), GraphResourceIdCodec.encode(resource.id),
                PacketGraphLight.UPSERT, entity == null ? -1 : entity.getId(), resource.wire);
    }
    private static void broadcast(ServerLevel level, Resource resource) {
        broadcast(level, resource, true);
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
        if (resource.wire == null) return;
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
                        && entity.entityId().equals(resource.definition.config().transform().entityId());
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
    private static final class Resource {
        final GraphResourceId id;
        LightDefinition definition;
        long created;
        LightDefinition wire;
        GraphEngineServices.VisualAsset asset;
        Resource(GraphResourceId id, LightDefinition definition, long created) {
            this.id = id; this.definition = definition; this.created = created;
        }
    }
}
