package com.mine.geometry_node.core.engine.system.schematic.projection;

import com.mine.geometry_node.core.engine.metadata.state.MetadataOwnerThread;

import com.mine.geometry_node.core.engine.metadata.state.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Retains bounded graph-owned definitions independently of spatially resident client runtimes. */
public final class SchematicProjectionService {
    public static final SchematicProjectionService INSTANCE = new SchematicProjectionService();

    private static final int MAX_RETAINED_PER_SERVER = 128;
    private static final int MAX_RETAINED_PER_PLAYER = 32;
    private static final long MAX_GEOMETRY_PER_SERVER = 1_048_576L;
    private static final long MAX_GEOMETRY_PER_PLAYER = 349_525L;
    private static final long MAX_ESTIMATED_BYTES_PER_SERVER = 64L * 1024L * 1024L;
    private static final long MAX_ESTIMATED_BYTES_PER_PLAYER = 32L * 1024L * 1024L;
    private static final long MAX_ESTIMATED_BYTES_PER_TICK = 32L * 1024L * 1024L;
    private static final int MAX_SENDS_PER_TICK = 64;

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();
    private boolean initialized;

    private SchematicProjectionService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        GraphResourceLifecycleManager.INSTANCE.registerStore("schematic_projection", this::removeResources);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                tick(event.getServer(), event.getServer().overworld().getGameTime()));
    }

    public synchronized boolean upsert(MinecraftServer server, GraphResourceId resourceId,
                                       PacketSchematicProjection packet, Collection<ServerPlayer> targets,
                                       long currentTick) {
        cleanupExpired(server, currentTick);
        if (targets == null || targets.isEmpty()) {
            remove(server, resourceId);
            return false;
        }
        Map<UUID, ServerPlayer> targetsById = new LinkedHashMap<>();
        for (ServerPlayer target : targets) {
            if (target != null && target.level().getServer() == server) {
                targetsById.putIfAbsent(target.getUUID(), target);
            }
        }
        Set<UUID> viewers = new LinkedHashSet<>(targetsById.keySet());
        if (viewers.isEmpty()) {
            remove(server, resourceId);
            return false;
        }
        long geometry = geometryCost(packet);
        long estimatedBytes = estimatedBytes(packet);
        if (geometry > MAX_GEOMETRY_PER_PLAYER || estimatedBytes > MAX_ESTIMATED_BYTES_PER_PLAYER) {
            return false;
        }

        ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
        ProjectionLease previous = state.resources.get(resourceId);
        while (!fits(state, resourceId, viewers, geometry, estimatedBytes)) {
            if (!evictOldestOther(server, state, resourceId)) return false;
        }

        SchematicProjectionDefinition definition = new SchematicProjectionDefinition(packet, currentTick);
        Map<UUID, ViewerSession<ServerGamePacketListenerImpl>> sessions = new LinkedHashMap<>();
        targetsById.forEach((id, player) -> sessions.put(id, new ViewerSession<>(player.connection)));
        if (previous != null) sendRemoval(server, resourceId, previous);
        ProjectionLease replacement = new ProjectionLease(previous == null ? new RuntimeMetadata() : previous.metadata(),
                Set.copyOf(viewers), Map.copyOf(sessions), new LinkedHashSet<>(), definition, geometry, estimatedBytes,
                new QuerySnapshot(resourceId, packet.dimension(),
                        new net.minecraft.world.phys.Vec3(packet.originX(), packet.originY(), packet.originZ()),
                        definition.bounds()));
        state.resources.put(resourceId, replacement);
        reconcile(server, state, resourceId, replacement, currentTick);
        return true;
    }

    public synchronized boolean remove(MinecraftServer server, GraphResourceId resourceId) {
        ServerState state = servers.get(server);
        ProjectionLease lease = state != null ? state.resources.remove(resourceId) : null;
        if (state != null && state.resources.isEmpty()) servers.remove(server);
        if (lease == null) return false;
        sendRemoval(server, resourceId, lease);
        return true;
    }

    private synchronized void removeResources(MinecraftServer server, GraphResourceRelease release) {
        ServerState state = servers.get(server);
        if (state == null) return;
        var iterator = state.resources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GraphResourceId, ProjectionLease> entry = iterator.next();
            if (!release.matches(entry.getKey())) continue;
            sendRemoval(server, entry.getKey(), entry.getValue());
            iterator.remove();
        }
        if (state.resources.isEmpty()) servers.remove(server);
    }

    private synchronized void cleanupExpired(MinecraftServer server, long currentTick) {
        ServerState state = servers.get(server);
        if (state == null) return;
        state.resources.entrySet().removeIf(entry -> {
            if (!entry.getValue().definition().expired(currentTick)) return false;
            sendRemoval(server, entry.getKey(), entry.getValue());
            return true;
        });
        if (state.resources.isEmpty()) servers.remove(server);
    }

    private synchronized void tick(MinecraftServer server, long currentTick) {
        cleanupExpired(server, currentTick);
        ServerState state = servers.get(server);
        if (state == null) return;
        for (var entry : state.resources.entrySet()) {
            reconcile(server, state, entry.getKey(), entry.getValue(), currentTick);
        }
    }

    private static void reconcile(MinecraftServer server, ServerState state, GraphResourceId id,
                                  ProjectionLease lease, long currentTick) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                Identifier.parse(lease.snapshot().dimension())));
        // Graph ownership is not an entity attachment; projections have no explicit anchor.
        if (level == null || !ResourceResidency.isResident(level, lease.snapshot().bounds(), null)) {
            sendRemoval(server, id, lease);
            return;
        }
        lease.activeViewers().removeIf(viewer -> !isOriginalSession(server, lease, viewer));
        PacketSchematicProjection packet = null;
        for (UUID viewer : lease.viewers()) {
            if (lease.activeViewers().contains(viewer) || !isOriginalSession(server, lease, viewer)) continue;
            // Retry budget-deferred wakeups next tick, one original recipient at a time.
            if (!reserveSendBudget(state, server.getTickCount(), lease.estimatedBytes())) break;
            if (packet == null) packet = lease.definition().packetAt(currentTick);
            if (packet == null) return;
            NetworkHandler.sendToPlayer(server.getPlayerList().getPlayer(viewer), packet);
            lease.activeViewers().add(viewer);
        }
    }

    private static boolean isOriginalSession(MinecraftServer server, ProjectionLease lease, UUID viewer) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer);
        return player != null && lease.sessions().get(viewer).matches(player.connection);
    }

    private static boolean fits(ServerState state, GraphResourceId replacedId, Set<UUID> viewers,
                                long geometry, long estimatedBytes) {
        int serverEntries = 1;
        long serverGeometry = geometry;
        long serverBytes = estimatedBytes;
        Map<UUID, PlayerBudget> players = new HashMap<>();
        for (Map.Entry<GraphResourceId, ProjectionLease> entry : state.resources.entrySet()) {
            if (entry.getKey().equals(replacedId)) continue;
            ProjectionLease lease = entry.getValue();
            serverEntries++;
            serverGeometry = saturatingAdd(serverGeometry, lease.geometry());
            serverBytes = saturatingAdd(serverBytes, lease.estimatedBytes());
            addPlayerBudget(players, lease.viewers(), lease.geometry(), lease.estimatedBytes());
        }
        addPlayerBudget(players, viewers, geometry, estimatedBytes);
        if (serverEntries > MAX_RETAINED_PER_SERVER || serverGeometry > MAX_GEOMETRY_PER_SERVER
                || serverBytes > MAX_ESTIMATED_BYTES_PER_SERVER) return false;
        for (UUID viewer : viewers) {
            PlayerBudget budget = players.get(viewer);
            if (budget != null && (budget.entries > MAX_RETAINED_PER_PLAYER
                    || budget.geometry > MAX_GEOMETRY_PER_PLAYER
                    || budget.estimatedBytes > MAX_ESTIMATED_BYTES_PER_PLAYER)) return false;
        }
        return true;
    }

    private static void addPlayerBudget(Map<UUID, PlayerBudget> budgets, Set<UUID> viewers,
                                        long geometry, long estimatedBytes) {
        for (UUID viewer : viewers) {
            PlayerBudget budget = budgets.computeIfAbsent(viewer, ignored -> new PlayerBudget());
            budget.entries++;
            budget.geometry = saturatingAdd(budget.geometry, geometry);
            budget.estimatedBytes = saturatingAdd(budget.estimatedBytes, estimatedBytes);
        }
    }

    private static boolean evictOldestOther(MinecraftServer server, ServerState state,
                                            GraphResourceId protectedId) {
        Iterator<Map.Entry<GraphResourceId, ProjectionLease>> iterator = state.resources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GraphResourceId, ProjectionLease> entry = iterator.next();
            if (entry.getKey().equals(protectedId)) continue;
            iterator.remove();
            sendRemoval(server, entry.getKey(), entry.getValue());
            return true;
        }
        return false;
    }

    private static boolean reserveSendBudget(ServerState state, long currentTick, long bytes) {
        if (state.sendBudgetTick != currentTick) {
            state.sendBudgetTick = currentTick;
            state.sentBytesThisTick = 0L;
            state.sendsThisTick = 0;
        }
        if (state.sendsThisTick >= MAX_SENDS_PER_TICK
                || bytes > MAX_ESTIMATED_BYTES_PER_TICK - state.sentBytesThisTick) return false;
        state.sendsThisTick++;
        state.sentBytesThisTick += bytes;
        return true;
    }

    private static long geometryCost(PacketSchematicProjection packet) {
        return saturatingAdd(packet.blocks().size(),
                saturatingAdd(packet.blockEntities().size(), packet.entities().size()));
    }

    private static long estimatedBytes(PacketSchematicProjection packet) {
        long bytes = 128L;
        bytes = saturatingAdd(bytes, (long) packet.resourceId().length() * 2L);
        bytes = saturatingAdd(bytes, (long) packet.graphId().length() * 2L);
        bytes = saturatingAdd(bytes, (long) packet.dimension().length() * 2L);
        for (String state : packet.states()) {
            bytes = saturatingAdd(bytes, 4L + (long) (state == null ? 0 : state.length()) * 2L);
        }
        bytes = saturatingAdd(bytes, (long) packet.blocks().size() * 20L);
        for (PacketSchematicProjection.BlockEntity blockEntity : packet.blockEntities()) {
            bytes = saturatingAdd(bytes, 16L + blockEntity.tag().sizeInBytes());
        }
        for (PacketSchematicProjection.Entity entity : packet.entities()) {
            bytes = saturatingAdd(bytes, 28L + entity.tag().sizeInBytes());
        }
        return bytes;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static void sendRemoval(MinecraftServer server, GraphResourceId resourceId, ProjectionLease lease) {
        if (lease.activeViewers().isEmpty()) return;
        PacketSchematicProjection packet = PacketSchematicProjection.removal(
                GraphResourceIdCodec.encode(resourceId), resourceId.binding().graphId(),
                lease.snapshot().dimension());
        for (UUID viewerId : lease.activeViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
            if (isOriginalSession(server, lease, viewerId)) NetworkHandler.sendToPlayer(player, packet);
        }
        lease.activeViewers().clear();
    }

    public synchronized MetadataCapabilities.Access metadata(MinecraftServer server, GraphResourceId id) {
        MetadataOwnerThread.requireCurrent(server);
        ServerState state = servers.get(server);
        ProjectionLease lease = state == null ? null : state.resources.get(id);
        if (lease == null || lease.definition().expired(server.overworld().getGameTime()))
            throw new IllegalStateException("Projection no longer exists");
        RuntimeMetadata metadata = lease.metadata();
        return metadata.access(GraphMetadataValuePolicy.of(server.registryAccess()), () -> {
            MetadataOwnerThread.requireCurrent(server);
            ServerState currentState = servers.get(server);
            ProjectionLease current = currentState == null ? null : currentState.resources.get(id);
            if (current == null || current.metadata() != metadata || current.definition().expired(server.overworld().getGameTime()))
                throw new IllegalStateException("Projection metadata handle is stale");
        });
    }

    public synchronized long metadataRevision(MinecraftServer server, GraphResourceId id) {
        metadata(server, id);
        return servers.get(server).resources.get(id).metadata().revision();
    }

    public record QuerySnapshot(GraphResourceId id, String dimension, net.minecraft.world.phys.Vec3 position,
                                net.minecraft.world.phys.AABB bounds) {}
    /** Includes dormant definitions until expiry or explicit lifecycle deletion. */
    public synchronized void visitWorldQuery(MinecraftServer server, java.util.function.Predicate<QuerySnapshot> visitor) {
        var state = servers.get(server);
        if (state == null) return;
        long now = server.overworld().getGameTime();
        for (var lease : state.resources.values())
            if (!lease.definition().expired(now) && !visitor.test(lease.snapshot())) return;
    }
    private record ProjectionLease(RuntimeMetadata metadata, Set<UUID> viewers,
                                   Map<UUID, ViewerSession<ServerGamePacketListenerImpl>> sessions, Set<UUID> activeViewers,
                                   SchematicProjectionDefinition definition, long geometry,
                                   long estimatedBytes, QuerySnapshot snapshot) {
    }

    /** Connection identity survives respawn but never grants a reconnect the old recipient's lease. */
    static final class ViewerSession<T> {
        private final WeakReference<T> original;

        ViewerSession(T connection) { original = new WeakReference<>(connection); }

        boolean matches(T connection) {
            return connection != null && original.get() == connection;
        }
    }

    private static final class ServerState {
        private final Map<GraphResourceId, ProjectionLease> resources = new LinkedHashMap<>();
        private long sendBudgetTick = Long.MIN_VALUE;
        private long sentBytesThisTick;
        private int sendsThisTick;
    }

    private static final class PlayerBudget {
        private int entries;
        private long geometry;
        private long estimatedBytes;
    }
}
