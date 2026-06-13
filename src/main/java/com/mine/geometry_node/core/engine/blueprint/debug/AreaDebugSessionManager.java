package com.mine.geometry_node.core.engine.blueprint.debug;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketAreaDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AreaDebugSessionManager {
    public static final double DEFAULT_RADIUS = 256.0D;
    public static final int DEFAULT_MAX_BOXES = 100;

    private static final double MIN_RADIUS = 1.0D;
    private static final double MAX_RADIUS = 2048.0D;
    private static final double MOVE_REFRESH_DISTANCE = 32.0D;
    private static final double MOVE_REFRESH_DISTANCE_SQR = MOVE_REFRESH_DISTANCE * MOVE_REFRESH_DISTANCE;
    private static final int IDLE_CHECK_INTERVAL_TICKS = 20;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<ServerLevel, LevelCache> LEVEL_CACHES = new IdentityHashMap<>();
    private static boolean registered;
    private static long dirtyVersion;

    private AreaDebugSessionManager() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SESSIONS.remove(player.getUUID());
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                markDirty();
                Session session = SESSIONS.get(player.getUUID());
                if (session != null) {
                    session.forceRefresh();
                    NetworkHandler.sendToPlayer(player, new PacketAreaDebugSnapshot(true, session.radius, List.of()));
                }
            }
        });
        bus.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel) {
                markDirty();
            }
        });
        bus.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel) {
                markDirty();
            }
        });
    }

    public static int enable(ServerPlayer player, double radius) {
        double clampedRadius = clampRadius(radius);
        Session session = new Session(clampedRadius);
        session.forceRefresh();
        SESSIONS.put(player.getUUID(), session);
        sendSnapshot(player, session, collectSnapshot(player, session));
        player.sendSystemMessage(Component.literal("Area debug enabled. radius=" + formatRadius(clampedRadius)
                + ", max=" + DEFAULT_MAX_BOXES));
        return 1;
    }

    public static int disable(ServerPlayer player, boolean notify) {
        Session removed = SESSIONS.remove(player.getUUID());
        NetworkHandler.sendToPlayer(player, new PacketAreaDebugSnapshot(false, 0.0D, List.of()));
        if (notify) {
            player.sendSystemMessage(Component.literal(removed != null ? "Area debug disabled." : "Area debug is not enabled."));
        }
        return removed != null ? 1 : 0;
    }

    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            LEVEL_CACHES.clear();
            return;
        }

        long tick = level.getGameTime();
        boolean cadence = Math.floorMod(tick, IDLE_CHECK_INTERVAL_TICKS) == 0;

        for (ServerPlayer player : level.players()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session == null) continue;

            ResourceKey<Level> dimension = level.dimension();
            boolean dimensionChanged = !dimension.equals(session.lastDimension);
            boolean moved = session.lastPosition == null
                    || session.lastPosition.distanceToSqr(player.position()) >= MOVE_REFRESH_DISTANCE_SQR;
            boolean dirty = session.lastDirtyVersion != dirtyVersion;

            if (!cadence && !dimensionChanged && !moved && !dirty) {
                continue;
            }

            Snapshot snapshot = collectSnapshot(player, session);
            if (snapshot.signature != session.lastSignature) {
                sendSnapshot(player, session, snapshot);
            } else if (dimensionChanged || moved || dirty) {
                updateBaseline(player, session, snapshot);
            }
        }
    }

    public static void replaceSourceBoxes(ServerLevel level, String sourceKey, List<AreaDebugBox> boxes, long seenTick) {
        LevelCache cache = LEVEL_CACHES.computeIfAbsent(level, ignored -> new LevelCache());
        SourceCache source = cache.sources.get(sourceKey);
        long signature = sourceSignature(boxes);
        if (source != null && source.lastSeenTick == seenTick && source.signature == signature) {
            return;
        }
        if (source != null && source.signature == signature) {
            source.lastSeenTick = seenTick;
            return;
        }
        cache.sources.put(sourceKey, new SourceCache(List.copyOf(boxes), seenTick, signature));
        dirtyVersion++;
    }

    public static void removeSourceBoxes(ServerLevel level, String sourceKey) {
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache != null && cache.sources.remove(sourceKey) != null) {
            dirtyVersion++;
        }
    }

    public static void markDirty() {
        dirtyVersion++;
    }

    public static boolean hasSessions() {
        return !SESSIONS.isEmpty();
    }

    public static String levelSourceKey(ServerLevel level, String graphId) {
        return "level:" + level.dimension().location() + ":" + graphId;
    }

    public static String entitySourceKey(ServerLevel level, net.minecraft.world.entity.Entity entity, String graphId) {
        return "entity:" + level.dimension().location() + ":" + entity.getUUID() + ":" + graphId;
    }

    private static Snapshot collectSnapshot(ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache == null || cache.sources.isEmpty()) {
            return new Snapshot(List.of(), 1L);
        }

        long currentTick = level.getGameTime();
        double radiusSqr = session.radius * session.radius;
        Vec3 origin = player.position();
        List<Candidate> candidates = new ArrayList<>();
        boolean removedStaleSource = false;
        var iterator = cache.sources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SourceCache> entry = iterator.next();
            SourceCache source = entry.getValue();
            if (currentTick - source.lastSeenTick > IDLE_CHECK_INTERVAL_TICKS) {
                iterator.remove();
                removedStaleSource = true;
                continue;
            }
            for (AreaDebugBox box : source.boxes) {
                if (!isCenterChunkLoaded(level, box.center())) continue;
                double distanceSqr = box.center().distanceToSqr(origin);
                if (distanceSqr > radiusSqr) continue;
                candidates.add(new Candidate(box, distanceSqr));
            }
        }
        if (removedStaleSource) {
            dirtyVersion++;
        }

        candidates.sort((left, right) -> {
            int distanceOrder = Double.compare(left.distanceSqr, right.distanceSqr);
            if (distanceOrder != 0) return distanceOrder;
            int idOrder = left.box.id().compareTo(right.box.id());
            if (idOrder != 0) return idOrder;
            return left.box.graphId().compareTo(right.box.graphId());
        });
        int count = Math.min(DEFAULT_MAX_BOXES, candidates.size());
        List<PacketAreaDebugSnapshot.AreaBox> boxes = new ArrayList<>(count);
        long signature = 1469598103934665603L;
        for (int i = 0; i < count; i++) {
            AreaDebugBox box = candidates.get(i).box;
            PacketAreaDebugSnapshot.AreaBox packetBox = toPacketBox(box);
            boxes.add(packetBox);
            signature = mix(signature, packetBox);
        }
        signature = signature * 31L + count;
        signature = signature * 31L + Double.doubleToLongBits(session.radius);
        return new Snapshot(boxes, signature);
    }

    private static boolean isCenterChunkLoaded(ServerLevel level, Vec3 center) {
        BlockPos pos = BlockPos.containing(center);
        return level.isLoaded(pos);
    }

    private static PacketAreaDebugSnapshot.AreaBox toPacketBox(AreaDebugBox box) {
        Vec3 center = box.center();
        Vec3 size = box.size();
        Vec3 rotation = box.rotation();
        return new PacketAreaDebugSnapshot.AreaBox(
                box.id(),
                box.graphId(),
                box.shape(),
                center.x,
                center.y,
                center.z,
                size.x,
                size.y,
                size.z,
                rotation.x,
                rotation.y,
                rotation.z
        );
    }

    private static void sendSnapshot(ServerPlayer player, Session session, Snapshot snapshot) {
        NetworkHandler.sendToPlayer(player, new PacketAreaDebugSnapshot(true, session.radius, snapshot.boxes));
        updateBaseline(player, session, snapshot);
    }

    private static void updateBaseline(ServerPlayer player, Session session, Snapshot snapshot) {
        session.lastPosition = player.position();
        session.lastDimension = player.serverLevel().dimension();
        session.lastDirtyVersion = dirtyVersion;
        session.lastSignature = snapshot.signature;
    }

    private static long sourceSignature(List<AreaDebugBox> boxes) {
        long signature = 1469598103934665603L;
        for (AreaDebugBox box : boxes) {
            signature = mix(signature, box.id().hashCode());
            signature = mix(signature, box.graphId().hashCode());
            signature = mix(signature, box.shape().hashCode());
            signature = mix(signature, Double.doubleToLongBits(box.center().x));
            signature = mix(signature, Double.doubleToLongBits(box.center().y));
            signature = mix(signature, Double.doubleToLongBits(box.center().z));
            signature = mix(signature, Double.doubleToLongBits(box.size().x));
            signature = mix(signature, Double.doubleToLongBits(box.size().y));
            signature = mix(signature, Double.doubleToLongBits(box.size().z));
            signature = mix(signature, Double.doubleToLongBits(box.rotation().x));
            signature = mix(signature, Double.doubleToLongBits(box.rotation().y));
            signature = mix(signature, Double.doubleToLongBits(box.rotation().z));
        }
        return signature * 31L + boxes.size();
    }

    private static long mix(long signature, PacketAreaDebugSnapshot.AreaBox box) {
        signature = mix(signature, box.id().hashCode());
        signature = mix(signature, box.graphId().hashCode());
        signature = mix(signature, box.shape().hashCode());
        signature = mix(signature, Double.doubleToLongBits(box.centerX()));
        signature = mix(signature, Double.doubleToLongBits(box.centerY()));
        signature = mix(signature, Double.doubleToLongBits(box.centerZ()));
        signature = mix(signature, Double.doubleToLongBits(box.sizeX()));
        signature = mix(signature, Double.doubleToLongBits(box.sizeY()));
        signature = mix(signature, Double.doubleToLongBits(box.sizeZ()));
        signature = mix(signature, Double.doubleToLongBits(box.rotationX()));
        signature = mix(signature, Double.doubleToLongBits(box.rotationY()));
        return mix(signature, Double.doubleToLongBits(box.rotationZ()));
    }

    private static long mix(long signature, long value) {
        return (signature ^ value) * 1099511628211L;
    }

    private static double clampRadius(double radius) {
        if (!Double.isFinite(radius)) {
            return DEFAULT_RADIUS;
        }
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    private static String formatRadius(double radius) {
        if (radius == Math.rint(radius)) {
            return Integer.toString((int) radius);
        }
        return Double.toString(radius);
    }

    private record Candidate(AreaDebugBox box, double distanceSqr) {
    }

    private record Snapshot(List<PacketAreaDebugSnapshot.AreaBox> boxes, long signature) {
    }

    private static final class LevelCache {
        private final Map<String, SourceCache> sources = new HashMap<>();
    }

    private static final class SourceCache {
        private final List<AreaDebugBox> boxes;
        private long lastSeenTick;
        private final long signature;

        private SourceCache(List<AreaDebugBox> boxes, long lastSeenTick, long signature) {
            this.boxes = boxes;
            this.lastSeenTick = lastSeenTick;
            this.signature = signature;
        }
    }

    private static final class Session {
        private final double radius;
        private Vec3 lastPosition;
        private ResourceKey<Level> lastDimension;
        private long lastDirtyVersion = Long.MIN_VALUE;
        private long lastSignature = Long.MIN_VALUE;

        private Session(double radius) {
            this.radius = radius;
        }

        private void forceRefresh() {
            lastPosition = null;
            lastDimension = null;
            lastDirtyVersion = Long.MIN_VALUE;
            lastSignature = Long.MIN_VALUE;
        }
    }
}
