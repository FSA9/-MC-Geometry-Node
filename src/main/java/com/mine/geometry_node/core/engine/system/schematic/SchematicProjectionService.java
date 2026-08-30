package com.mine.geometry_node.core.engine.system.schematic;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Tracks graph-owned client projections so lifecycle cleanup can remove them immediately. */
public final class SchematicProjectionService {
    public static final SchematicProjectionService INSTANCE = new SchematicProjectionService();

    private final Map<MinecraftServer, Map<GraphResourceId, ProjectionLease>> servers = new WeakHashMap<>();
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
        Set<UUID> viewers = new LinkedHashSet<>();
        for (ServerPlayer target : targets) {
            if (target != null) viewers.add(target.getUUID());
        }
        if (viewers.isEmpty()) {
            remove(server, resourceId);
            return false;
        }
        Map<GraphResourceId, ProjectionLease> resources =
                servers.computeIfAbsent(server, ignored -> new HashMap<>());
        ProjectionLease previous = resources.get(resourceId);
        if (previous != null && !previous.viewers().equals(viewers)) {
            Set<UUID> removedViewers = new LinkedHashSet<>(previous.viewers());
            removedViewers.removeAll(viewers);
            if (!removedViewers.isEmpty()) sendRemoval(server, resourceId, removedViewers);
        }
        NetworkHandler.sendToPlayers(targets, packet);
        long expiresAt = currentTick + Math.max(1, packet.durationTicks());
        resources.put(resourceId, new ProjectionLease(Set.copyOf(viewers), expiresAt));
        return true;
    }

    public synchronized boolean remove(MinecraftServer server, GraphResourceId resourceId) {
        Map<GraphResourceId, ProjectionLease> resources = servers.get(server);
        ProjectionLease lease = resources != null ? resources.remove(resourceId) : null;
        if (resources != null && resources.isEmpty()) servers.remove(server);
        if (lease == null) return false;
        sendRemoval(server, resourceId, lease.viewers());
        return true;
    }

    private synchronized void removeResources(MinecraftServer server, Predicate<GraphResourceId> predicate) {
        Map<GraphResourceId, ProjectionLease> resources = servers.get(server);
        if (resources == null) return;
        var iterator = resources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GraphResourceId, ProjectionLease> entry = iterator.next();
            if (!predicate.test(entry.getKey())) continue;
            sendRemoval(server, entry.getKey(), entry.getValue().viewers());
            iterator.remove();
        }
        if (resources.isEmpty()) servers.remove(server);
    }

    private synchronized void cleanupExpired(MinecraftServer server, long currentTick) {
        Map<GraphResourceId, ProjectionLease> resources = servers.get(server);
        if (resources == null) return;
        resources.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= currentTick);
        if (resources.isEmpty()) servers.remove(server);
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

    private record ProjectionLease(Set<UUID> viewers, long expiresAt) {
    }
}
