package com.mine.geometry_node.core.engine.behavior.debug;

import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketBehaviorDebugSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-thread subscription registry for sampled behavior debug snapshots. */
public final class BehaviorTreeDebugService {
    public static final BehaviorTreeDebugService INSTANCE =
            new BehaviorTreeDebugService();

    public static final int SAMPLE_INTERVAL_TICKS = 5;
    public static final int MAX_SUBSCRIPTIONS_PER_PLAYER = 4;
    public static final int MAX_REQUESTS_PER_WINDOW = 20;
    public static final int REQUEST_WINDOW_TICKS = 20;
    public static final double MAX_VISIBLE_DISTANCE = 128.0D;
    private static final double MAX_VISIBLE_DISTANCE_SQR =
            MAX_VISIBLE_DISTANCE * MAX_VISIBLE_DISTANCE;

    private final Map<MinecraftServer, ServerSubscriptions> servers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private boolean initialized;

    private BehaviorTreeDebugService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) cancelAll(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> servers.remove(event.getServer()));
    }

    public void handle(ServerPlayer player, UUID instanceId, boolean subscribe) {
        if (player == null || instanceId == null) return;
        MinecraftServer server = player.level().getServer();
        ServerSubscriptions state = servers.computeIfAbsent(server, ignored -> new ServerSubscriptions());
        long tick = server.overworld().getGameTime();
        if (!state.allowRequest(player.getUUID(), tick)) return;
        if (!subscribe) {
            cancel(player, instanceId, true);
            return;
        }
        if (!hasPermission(player)) {
            removeSubscription(player, instanceId);
            sendStatus(player, instanceId, PacketBehaviorDebugSnapshot.Status.PERMISSION_DENIED,
                    "permission_denied");
            return;
        }

        LinkedHashSet<UUID> subscriptions = state.byPlayer.computeIfAbsent(
                player.getUUID(), ignored -> new LinkedHashSet<>());
        if (subscriptions.contains(instanceId)) return;
        if (subscriptions.size() >= MAX_SUBSCRIPTIONS_PER_PLAYER) {
            sendStatus(player, instanceId, PacketBehaviorDebugSnapshot.Status.LIMIT_REACHED,
                    "subscription_limit_reached");
            return;
        }

        BehaviorTreeDebugAccess access = BehaviorTreeRuntime.INSTANCE.debugAccess(server, instanceId);
        PacketBehaviorDebugSnapshot.Status visibility = access == null || !access.active()
                ? PacketBehaviorDebugSnapshot.Status.NOT_FOUND : validateVisible(player, access);
        if (visibility != null) {
            subscriptions.remove(instanceId);
            sendStatus(player, instanceId, visibility, visibility.name().toLowerCase(Locale.ROOT));
            prune(server, state, player.getUUID());
            return;
        }

        subscriptions.add(instanceId);
        updateDebugTracing(server, state, instanceId);
        BehaviorTreeDebugSnapshot snapshot = BehaviorTreeRuntime.INSTANCE.debugSnapshot(server, instanceId);
        if (snapshot == null) {
            removeSubscription(player, instanceId);
            sendStatus(player, instanceId, PacketBehaviorDebugSnapshot.Status.NOT_FOUND, "not_found");
            return;
        }
        long nextInitialTick = state.nextInitialSnapshotByPlayer.getOrDefault(
                player.getUUID(), Long.MIN_VALUE);
        if (tick >= nextInitialTick) {
            NetworkHandler.sendToPlayer(player, PacketBehaviorDebugSnapshot.snapshot(snapshot));
            state.nextInitialSnapshotByPlayer.put(player.getUUID(), safeAdd(tick, SAMPLE_INTERVAL_TICKS));
        }
    }

    public void cancelAll(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerSubscriptions state = servers.get(server);
        if (state != null) {
            Set<UUID> affectedInstances = state.subscriptionsFor(player.getUUID());
            state.removePlayer(player.getUUID());
            for (UUID instanceId : affectedInstances) {
                updateDebugTracing(server, state, instanceId);
            }
            if (state.isEmpty()) servers.remove(server, state);
        }
    }

    private void tick(MinecraftServer server) {
        ServerSubscriptions state = servers.get(server);
        if (state == null) return;
        long tick = server.overworld().getGameTime();
        state.pruneRequestBuckets(tick);
        if (state.byPlayer.isEmpty()) {
            if (state.isEmpty()) servers.remove(server, state);
            return;
        }
        if (Math.floorMod(tick, SAMPLE_INTERVAL_TICKS) != 0L) return;

        Map<UUID, PacketBehaviorDebugSnapshot> packetCache = new java.util.HashMap<>();
        Iterator<Map.Entry<UUID, LinkedHashSet<UUID>>> players = state.byPlayer.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, LinkedHashSet<UUID>> playerEntry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null || player.isRemoved()) {
                Set<UUID> affectedInstances = Set.copyOf(playerEntry.getValue());
                state.nextInitialSnapshotByPlayer.remove(playerEntry.getKey());
                players.remove();
                for (UUID instanceId : affectedInstances) {
                    updateDebugTracing(server, state, instanceId);
                }
                continue;
            }
            if (!hasPermission(player)) {
                Set<UUID> affectedInstances = Set.copyOf(playerEntry.getValue());
                for (UUID instanceId : playerEntry.getValue()) {
                    sendStatus(player, instanceId,
                            PacketBehaviorDebugSnapshot.Status.PERMISSION_DENIED,
                            "permission_denied");
                }
                state.nextInitialSnapshotByPlayer.remove(playerEntry.getKey());
                players.remove();
                for (UUID instanceId : affectedInstances) {
                    updateDebugTracing(server, state, instanceId);
                }
                continue;
            }

            Iterator<UUID> subscriptions = playerEntry.getValue().iterator();
            while (subscriptions.hasNext()) {
                UUID instanceId = subscriptions.next();
                BehaviorTreeDebugAccess access = BehaviorTreeRuntime.INSTANCE.debugAccess(server, instanceId);
                PacketBehaviorDebugSnapshot.Status visibility = access == null
                        ? PacketBehaviorDebugSnapshot.Status.NOT_FOUND
                        : validateVisible(player, access);
                if (visibility != null) {
                    sendStatus(player, instanceId, visibility,
                            visibility.name().toLowerCase(Locale.ROOT));
                    subscriptions.remove();
                    updateDebugTracing(server, state, instanceId);
                    continue;
                }
                PacketBehaviorDebugSnapshot packet = packetCache.computeIfAbsent(instanceId, ignored -> {
                    BehaviorTreeDebugSnapshot snapshot = BehaviorTreeRuntime.INSTANCE.debugSnapshot(server, instanceId);
                    return snapshot != null ? PacketBehaviorDebugSnapshot.snapshot(snapshot) : null;
                });
                if (packet == null) {
                    sendStatus(player, instanceId, PacketBehaviorDebugSnapshot.Status.NOT_FOUND,
                            "not_found");
                    subscriptions.remove();
                    updateDebugTracing(server, state, instanceId);
                    continue;
                }
                NetworkHandler.sendToPlayer(player, packet);
                if (!access.active()) {
                    subscriptions.remove();
                    updateDebugTracing(server, state, instanceId);
                }
            }
            if (playerEntry.getValue().isEmpty()) {
                state.nextInitialSnapshotByPlayer.remove(playerEntry.getKey());
                players.remove();
            }
        }
        if (state.isEmpty()) servers.remove(server, state);
    }

    private void cancel(ServerPlayer player, UUID instanceId, boolean acknowledge) {
        removeSubscription(player, instanceId);
        if (acknowledge) {
            sendStatus(player, instanceId, PacketBehaviorDebugSnapshot.Status.CANCELLED, "");
        }
    }

    private void removeSubscription(ServerPlayer player, UUID instanceId) {
        MinecraftServer server = player.level().getServer();
        ServerSubscriptions state = servers.get(server);
        if (state != null) {
            LinkedHashSet<UUID> subscriptions = state.byPlayer.get(player.getUUID());
            if (subscriptions != null) {
                subscriptions.remove(instanceId);
            }
            updateDebugTracing(server, state, instanceId);
            prune(server, state, player.getUUID());
        }
    }

    private static PacketBehaviorDebugSnapshot.Status validateVisible(
            ServerPlayer player, BehaviorTreeDebugAccess access) {
        String playerDimension = player.level().dimension().identifier().toString();
        if (!access.positionKnown() || !playerDimension.equals(access.dimension())
                || player.distanceToSqr(access.ownerX(), access.ownerY(), access.ownerZ())
                > MAX_VISIBLE_DISTANCE_SQR) {
            return PacketBehaviorDebugSnapshot.Status.OUT_OF_RANGE;
        }
        return null;
    }

    private void prune(MinecraftServer server, ServerSubscriptions state, UUID playerId) {
        LinkedHashSet<UUID> subscriptions = state.byPlayer.get(playerId);
        if (subscriptions == null || subscriptions.isEmpty()) state.removePlayer(playerId);
        if (state.isEmpty()) servers.remove(server, state);
    }

    private static boolean hasPermission(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void sendStatus(ServerPlayer player, UUID instanceId,
                                   PacketBehaviorDebugSnapshot.Status status, String detail) {
        NetworkHandler.sendToPlayer(player,
                PacketBehaviorDebugSnapshot.status(instanceId, status, detail));
    }

    private static void updateDebugTracing(MinecraftServer server, ServerSubscriptions state,
                                           UUID instanceId) {
        var instance = BehaviorTreeRuntime.INSTANCE.get(server, instanceId);
        if (instance != null) instance.setDebugTracingEnabled(state.hasSubscription(instanceId));
    }

    private static long safeAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static final class ServerSubscriptions {
        private final Map<UUID, LinkedHashSet<UUID>> byPlayer = new java.util.LinkedHashMap<>();
        private final Map<UUID, Long> nextInitialSnapshotByPlayer = new java.util.HashMap<>();
        private final Map<UUID, RequestBucket> requestBuckets = new java.util.HashMap<>();

        private boolean allowRequest(UUID playerId, long tick) {
            RequestBucket bucket = requestBuckets.computeIfAbsent(
                    playerId, ignored -> new RequestBucket(tick));
            if (tick < bucket.windowStartTick
                    || tick - bucket.windowStartTick >= REQUEST_WINDOW_TICKS) {
                bucket.windowStartTick = tick;
                bucket.requests = 0;
            }
            bucket.lastRequestTick = tick;
            if (bucket.requests >= MAX_REQUESTS_PER_WINDOW) return false;
            bucket.requests++;
            return true;
        }

        private void pruneRequestBuckets(long tick) {
            requestBuckets.entrySet().removeIf(entry -> tick < entry.getValue().lastRequestTick
                    || tick - entry.getValue().lastRequestTick >= REQUEST_WINDOW_TICKS);
        }

        private boolean isEmpty() {
            return byPlayer.isEmpty() && requestBuckets.isEmpty();
        }

        private boolean hasSubscription(UUID instanceId) {
            for (Set<UUID> subscriptions : byPlayer.values()) {
                if (subscriptions.contains(instanceId)) return true;
            }
            return false;
        }

        private Set<UUID> subscriptionsFor(UUID playerId) {
            Set<UUID> subscriptions = byPlayer.get(playerId);
            return subscriptions != null ? Set.copyOf(subscriptions) : Set.of();
        }

        private void removePlayer(UUID playerId) {
            byPlayer.remove(playerId);
            nextInitialSnapshotByPlayer.remove(playerId);
            requestBuckets.remove(playerId);
        }
    }

    private static final class RequestBucket {
        private long windowStartTick;
        private long lastRequestTick;
        private int requests;

        private RequestBucket(long tick) {
            windowStartTick = tick;
            lastRequestTick = tick;
        }
    }
}
