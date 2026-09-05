package com.mine.geometry_node.core.engine.system.schematic;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Tracks graph-owned client projections so lifecycle cleanup can remove them immediately. */
public final class SchematicProjectionService {
    public static final SchematicProjectionService INSTANCE = new SchematicProjectionService();

    private static final int MAX_ACTIVE_PER_SERVER = 128;
    private static final int MAX_ACTIVE_PER_PLAYER = 32;
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
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if ((event.getServer().getTickCount() % 20) == 0) {
                cleanupExpired(event.getServer(), event.getServer().overworld().getGameTime());
            }
        });
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
        long serverTick = server.getTickCount();
        long sendBytes = saturatingMultiply(estimatedBytes, viewers.size());
        if (!reserveSendBudget(state, serverTick, sendBytes)) return false;
        while (!fits(state, resourceId, viewers, geometry, estimatedBytes)) {
            if (!evictOldestOther(server, state, resourceId)) return false;
        }

        if (previous != null && !previous.viewers().equals(viewers)) {
            Set<UUID> removedViewers = new LinkedHashSet<>(previous.viewers());
            removedViewers.removeAll(viewers);
            if (!removedViewers.isEmpty()) sendRemoval(server, resourceId, removedViewers);
        }
        NetworkHandler.sendToPlayers(targetsById.values(), packet);
        state.resources.put(resourceId, new ProjectionLease(Set.copyOf(viewers),
                expiresAt(currentTick, packet.durationTicks()), geometry, estimatedBytes));
        return true;
    }

    public synchronized boolean remove(MinecraftServer server, GraphResourceId resourceId) {
        ServerState state = servers.get(server);
        ProjectionLease lease = state != null ? state.resources.remove(resourceId) : null;
        if (state != null && state.resources.isEmpty()) servers.remove(server);
        if (lease == null) return false;
        sendRemoval(server, resourceId, lease.viewers());
        return true;
    }

    private synchronized void removeResources(MinecraftServer server, GraphResourceRelease release) {
        ServerState state = servers.get(server);
        if (state == null) return;
        var iterator = state.resources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GraphResourceId, ProjectionLease> entry = iterator.next();
            if (!release.matches(entry.getKey())) continue;
            sendRemoval(server, entry.getKey(), entry.getValue().viewers());
            iterator.remove();
        }
        if (state.resources.isEmpty()) servers.remove(server);
    }

    private synchronized void cleanupExpired(MinecraftServer server, long currentTick) {
        ServerState state = servers.get(server);
        if (state == null) return;
        state.resources.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= currentTick);
        if (state.resources.isEmpty()) servers.remove(server);
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
        if (serverEntries > MAX_ACTIVE_PER_SERVER || serverGeometry > MAX_GEOMETRY_PER_SERVER
                || serverBytes > MAX_ESTIMATED_BYTES_PER_SERVER) return false;
        for (UUID viewer : viewers) {
            PlayerBudget budget = players.get(viewer);
            if (budget != null && (budget.entries > MAX_ACTIVE_PER_PLAYER
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
            sendRemoval(server, entry.getKey(), entry.getValue().viewers());
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

    private static long expiresAt(long currentTick, int durationTicks) {
        long duration = Math.max(1, durationTicks);
        return currentTick > Long.MAX_VALUE - duration ? Long.MAX_VALUE : currentTick + duration;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        return multiplier > 0 && value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    private static void sendRemoval(MinecraftServer server, GraphResourceId resourceId, Set<UUID> viewers) {
        PacketSchematicProjection packet = PacketSchematicProjection.removal(
                GraphResourceIdCodec.encode(resourceId), resourceId.binding().graphId(),
                resourceId.scope().dimension().identifier().toString());
        for (UUID viewerId : viewers) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
            if (player != null) NetworkHandler.sendToPlayer(player, packet);
        }
    }

    private record ProjectionLease(Set<UUID> viewers, long expiresAt, long geometry,
                                   long estimatedBytes) {
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
