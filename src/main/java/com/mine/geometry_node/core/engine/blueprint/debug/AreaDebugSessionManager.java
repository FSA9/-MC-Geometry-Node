package com.mine.geometry_node.core.engine.blueprint.debug;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketAreaDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
import java.util.function.Consumer;

public final class AreaDebugSessionManager {
    public static final double DEFAULT_RADIUS = 256.0D;
    public static final int DEFAULT_MAX_BOXES = 100;
    public static final int DEFAULT_MAX_MESHES = 100;
    public static final int TRANSIENT_QUERY_DURATION_TICKS = 60;

    private static final String AREA_SOURCE_PREFIX = "area:";
    private static final String GEOMETRY_SOURCE_PREFIX = "geometry:";
    private static final String SCHEMATIC_SOURCE_PREFIX = "schematic:";
    private static final double MIN_RADIUS = 1.0D;
    private static final double MAX_RADIUS = 2048.0D;
    private static final double MOVE_REFRESH_DISTANCE = 32.0D;
    private static final double MOVE_REFRESH_DISTANCE_SQR = MOVE_REFRESH_DISTANCE * MOVE_REFRESH_DISTANCE;
    private static final int IDLE_CHECK_INTERVAL_TICKS = 20;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<ServerLevel, LevelCache> LEVEL_CACHES = new IdentityHashMap<>();
    private static final List<Consumer<ServerPlayer>> SCHEMATIC_CHANNEL_HYDRATORS = new ArrayList<>();
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
                    hydrateSchematicChannel(player, session);
                    refreshPlayer(player, session);
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
        return enableArea(player, radius);
    }

    public static int enableArea(ServerPlayer player, double radius) {
        double clampedRadius = clampRadius(radius);
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.radius = clampedRadius;
        session.areaBoxesEnabled = true;
        session.forceRefresh();
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal("Area debug enabled. radius=" + formatRadius(clampedRadius)
                + ", max=" + DEFAULT_MAX_BOXES));
        return 1;
    }

    public static int disable(ServerPlayer player, boolean notify) {
        return disableArea(player, notify);
    }

    public static int disableArea(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.areaBoxesEnabled;
        if (session != null) {
            session.areaBoxesEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshots(player);
        }
        if (notify) {
            player.sendSystemMessage(Component.literal(changed ? "Area debug disabled." : "Area debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static int enableSchematic(ServerPlayer player, double radius) {
        double clampedRadius = clampRadius(radius);
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.radius = clampedRadius;
        session.schematicBoxesEnabled = true;
        session.forceRefresh();
        hydrateSchematicChannel(player, session);
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal("Schematic debug enabled. radius=" + formatRadius(clampedRadius)
                + ", max=" + DEFAULT_MAX_BOXES));
        return 1;
    }

    public static int disableSchematic(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.schematicBoxesEnabled;
        if (session != null) {
            session.schematicBoxesEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshots(player);
        }
        if (notify) {
            player.sendSystemMessage(Component.literal(changed ? "Schematic debug disabled." : "Schematic debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static int enableGeometry(ServerPlayer player, double radius) {
        double clampedRadius = clampRadius(radius);
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.radius = clampedRadius;
        session.geometryEnabled = true;
        session.forceRefresh();
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal("Geometry debug enabled. radius=" + formatRadius(clampedRadius)
                + ", max=" + DEFAULT_MAX_MESHES));
        return 1;
    }

    public static int disableGeometry(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.geometryEnabled;
        if (session != null) {
            session.geometryEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshots(player);
        }
        if (notify) {
            player.sendSystemMessage(Component.literal(changed ? "Geometry debug disabled." : "Geometry debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static int enableInteraction(ServerPlayer player, double radius) {
        double clampedRadius = clampRadius(radius);
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.radius = clampedRadius;
        session.interactionBoxesEnabled = true;
        session.forceRefresh();
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal("Interaction debug enabled. radius=" + formatRadius(clampedRadius)
                + ", max=" + DEFAULT_MAX_BOXES));
        return 1;
    }

    public static int disableInteraction(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.interactionBoxesEnabled;
        if (session != null) {
            session.interactionBoxesEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshots(player);
        }
        if (notify) {
            player.sendSystemMessage(Component.literal(changed ? "Interaction debug disabled." : "Interaction debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static void registerSchematicChannelHydrator(Consumer<ServerPlayer> hydrator) {
        if (hydrator != null) {
            SCHEMATIC_CHANNEL_HYDRATORS.add(hydrator);
        }
    }

    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }

        long tick = level.getGameTime();
        boolean cadence = Math.floorMod(tick, IDLE_CHECK_INTERVAL_TICKS) == 0;
        LevelCache levelCache = LEVEL_CACHES.get(level);
        boolean hasExpiredSources = levelCache != null
                && levelCache.sources.values().stream().anyMatch(source -> source.isTransientExpired(tick));

        for (ServerPlayer player : level.players()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session == null) continue;

            ResourceKey<Level> dimension = level.dimension();
            boolean dimensionChanged = !dimension.equals(session.lastDimension);
            boolean moved = session.lastPosition == null
                    || session.lastPosition.distanceToSqr(player.position()) >= MOVE_REFRESH_DISTANCE_SQR;
            boolean dirty = session.lastDirtyVersion != dirtyVersion;
            boolean regularRefresh = cadence || dimensionChanged || moved || dirty || hasExpiredSources;

            if (!session.interactionBoxesEnabled && !regularRefresh) {
                continue;
            }

            AreaSnapshot areaSnapshot = collectAreaSnapshot(player, session);
            if (areaSnapshot.signature != session.lastAreaSignature) {
                sendAreaSnapshot(player, session, areaSnapshot);
            }
            if (!regularRefresh) continue;

            GeometrySnapshot geometrySnapshot = collectGeometrySnapshot(player, session);
            if (geometrySnapshot.signature != session.lastGeometrySignature) {
                sendGeometrySnapshot(player, session, geometrySnapshot);
            }
            updateBaseline(player, session, areaSnapshot, geometrySnapshot);
        }
    }

    public static void replaceSourceBoxes(ServerLevel level, String sourceKey, List<AreaDebugBox> boxes, long seenTick) {
        replaceSourceBoxes(level, sourceKey, boxes, seenTick, -1L);
    }

    public static void replacePersistentSourceBoxes(ServerLevel level, String sourceKey, List<AreaDebugBox> boxes, long seenTick) {
        replaceSourceBoxes(level, sourceKey, boxes, seenTick, Long.MAX_VALUE);
    }

    public static void showTransientQueryArea(ServerLevel level,
                                              String graphId,
                                              String nodeId,
                                              String shape,
                                              Vec3 center,
                                              Vec3 size,
                                              Vec3 rotation) {
        if (level == null || center == null || size == null || !hasAreaBoxSessions()) {
            return;
        }
        String safeGraphId = graphId == null || graphId.isBlank() ? "unknown" : graphId;
        String safeNodeId = nodeId == null || nodeId.isBlank() ? "unknown" : nodeId;
        String safeShape = shape == null || shape.isBlank() ? "box" : shape;
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;
        int queryHash = java.util.Objects.hash(safeShape, center, size, safeRotation);
        String sourceKey = AREA_SOURCE_PREFIX + "query:" + level.dimension().identifier() + ":"
                + safeGraphId + ":" + safeNodeId + ":" + Integer.toUnsignedString(queryHash, 16);
        long currentTick = level.getGameTime();
        AreaDebugBox box = new AreaDebugBox(sourceKey, safeGraphId, safeShape, center, size, safeRotation);
        replaceSourceBoxes(level, sourceKey, List.of(box), currentTick,
                currentTick + TRANSIENT_QUERY_DURATION_TICKS);
    }

    private static void replaceSourceBoxes(ServerLevel level, String sourceKey, List<AreaDebugBox> boxes, long seenTick, long expiresAt) {
        LevelCache cache = LEVEL_CACHES.computeIfAbsent(level, ignored -> new LevelCache());
        SourceCache source = cache.sources.get(sourceKey);
        long signature = sourceSignature(boxes);
        if (source != null && source.lastSeenTick == seenTick && source.signature == signature && source.expiresAt == expiresAt) {
            return;
        }
        if (source != null && source.signature == signature && source.expiresAt == expiresAt) {
            source.lastSeenTick = seenTick;
            return;
        }
        cache.sources.put(sourceKey, new SourceCache(List.copyOf(boxes), seenTick, signature, expiresAt));
        dirtyVersion++;
    }

    public static void removeSourceBoxes(ServerLevel level, String sourceKey) {
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache != null && cache.sources.remove(sourceKey) != null) {
            dirtyVersion++;
        }
    }

    public static void replaceSourceGeometry(ServerLevel level,
                                             String sourceKey,
                                             List<GeometryDebugMesh> meshes) {
        LevelCache cache = LEVEL_CACHES.computeIfAbsent(level, ignored -> new LevelCache());
        if (cache.geometrySources.replace(sourceKey, meshes)) {
            dirtyVersion++;
        }
    }

    public static void removeSourceGeometry(ServerLevel level, String sourceKey) {
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache != null && cache.geometrySources.remove(sourceKey)) {
            dirtyVersion++;
        }
    }

    public static void markDirty() {
        dirtyVersion++;
    }

    private static void hydrateSchematicChannel(ServerPlayer player, Session session) {
        if (player == null || session == null || !session.schematicBoxesEnabled) {
            return;
        }
        for (Consumer<ServerPlayer> hydrator : SCHEMATIC_CHANNEL_HYDRATORS) {
            hydrator.accept(player);
        }
    }

    public static boolean hasAreaBoxSessions() {
        for (Session session : SESSIONS.values()) {
            if (session.areaBoxesEnabled) {
                return true;
            }
        }
        return false;
    }

    public static String levelSourceKey(ServerLevel level, String graphId) {
        return AREA_SOURCE_PREFIX + "level:" + level.dimension().identifier() + ":" + graphId;
    }

    public static String entitySourceKey(ServerLevel level, net.minecraft.world.entity.Entity entity, String graphId) {
        return AREA_SOURCE_PREFIX + "entity:" + level.dimension().identifier() + ":" + entity.getUUID() + ":" + graphId;
    }

    public static String geometryMeshSourceKey(ServerLevel level, String key) {
        return GEOMETRY_SOURCE_PREFIX + "mesh:" + level.dimension().identifier() + ":" + key.trim();
    }

    public static String schematicPlacementSourceKey(ServerLevel level, String key) {
        return SCHEMATIC_SOURCE_PREFIX + "placement:" + level.dimension().identifier() + ":" + key.trim();
    }

    private static AreaSnapshot collectAreaSnapshot(ServerPlayer player, Session session) {
        ServerLevel level = player.level();
        LevelCache cache = LEVEL_CACHES.get(level);
        if (!session.hasBoxChannels()) {
            return new AreaSnapshot(List.of(), 1L);
        }

        long currentTick = level.getGameTime();
        double radiusSqr = session.radius * session.radius;
        Vec3 origin = player.position();
        List<Candidate> candidates = new ArrayList<>();
        boolean removedStaleSource = false;
        if (cache != null && !cache.sources.isEmpty()) {
            var iterator = cache.sources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, SourceCache> entry = iterator.next();
                SourceCache source = entry.getValue();
                if (source.isExpired(currentTick)) {
                    iterator.remove();
                    removedStaleSource = true;
                    continue;
                }
                if (!session.isBoxSourceVisible(entry.getKey())) {
                    continue;
                }
                for (AreaDebugBox box : source.boxes) {
                    if (!isCenterChunkLoaded(level, box.center())) continue;
                    double distanceSqr = box.center().distanceToSqr(origin);
                    if (distanceSqr > radiusSqr) continue;
                    candidates.add(new Candidate(box, distanceSqr));
                }
            }
        }
        if (session.interactionBoxesEnabled) {
            collectInteractionCandidates(level, origin, session.radius, radiusSqr, candidates);
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
        return new AreaSnapshot(boxes, signature);
    }

    private static void collectInteractionCandidates(ServerLevel level,
                                                     Vec3 origin,
                                                     double radius,
                                                     double radiusSqr,
                                                     List<Candidate> candidates) {
        AABB queryBounds = AABB.ofSize(origin, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (Interaction interaction : level.getEntitiesOfClass(Interaction.class, queryBounds)) {
            if (interaction.isRemoved()) continue;
            AABB bounds = interaction.getBoundingBox();
            Vec3 center = bounds.getCenter();
            double distanceSqr = center.distanceToSqr(origin);
            if (distanceSqr > radiusSqr) continue;
            candidates.add(new Candidate(new AreaDebugBox(
                    interaction.getStringUUID(),
                    "interaction",
                    "box",
                    center,
                    new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()),
                    Vec3.ZERO
            ), distanceSqr));
        }
    }

    private static GeometrySnapshot collectGeometrySnapshot(ServerPlayer player, Session session) {
        if (!session.geometryEnabled) {
            return new GeometrySnapshot(List.of(), 1L);
        }
        ServerLevel level = player.level();
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache == null || cache.geometrySources.isEmpty()) {
            return new GeometrySnapshot(List.of(), 1L);
        }

        double radiusSqr = session.radius * session.radius;
        Vec3 origin = player.position();
        List<GeometryCandidate> candidates = new ArrayList<>();
        for (GeometryDebugMeshStore.Source source : cache.geometrySources.sources()) {
            for (GeometryDebugMesh mesh : source.meshes()) {
                if (!isCenterChunkLoaded(level, mesh.center())) continue;
                double distanceSqr = mesh.center().distanceToSqr(origin);
                if (distanceSqr > radiusSqr) continue;
                candidates.add(new GeometryCandidate(mesh, distanceSqr));
            }
        }

        candidates.sort((left, right) -> {
            int distanceOrder = Double.compare(left.distanceSqr, right.distanceSqr);
            if (distanceOrder != 0) return distanceOrder;
            int idOrder = left.mesh.id().compareTo(right.mesh.id());
            if (idOrder != 0) return idOrder;
            return left.mesh.graphId().compareTo(right.mesh.graphId());
        });
        int count = Math.min(DEFAULT_MAX_MESHES, candidates.size());
        List<PacketGeometryDebugSnapshot.Mesh> meshes = new ArrayList<>(count);
        long signature = 1469598103934665603L;
        for (int i = 0; i < count; i++) {
            GeometryDebugMesh mesh = candidates.get(i).mesh;
            PacketGeometryDebugSnapshot.Mesh packetMesh = toPacketMesh(mesh);
            meshes.add(packetMesh);
            signature = mix(signature, packetMesh);
        }
        signature = signature * 31L + count;
        signature = signature * 31L + Double.doubleToLongBits(session.radius);
        return new GeometrySnapshot(meshes, signature);
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

    private static PacketGeometryDebugSnapshot.Mesh toPacketMesh(GeometryDebugMesh mesh) {
        Vec3 center = mesh.center();
        return new PacketGeometryDebugSnapshot.Mesh(
                mesh.id(),
                mesh.graphId(),
                center.x,
                center.y,
                center.z,
                mesh.vertices(),
                mesh.edges(),
                mesh.faces()
        );
    }

    private static void sendAreaSnapshot(ServerPlayer player, Session session, AreaSnapshot snapshot) {
        boolean enabled = session.hasBoxChannels();
        NetworkHandler.sendToPlayer(player, new PacketAreaDebugSnapshot(enabled, session.radius, enabled ? snapshot.boxes : List.of()));
        session.lastAreaSignature = snapshot.signature;
    }

    private static void sendGeometrySnapshot(ServerPlayer player, Session session, GeometrySnapshot snapshot) {
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(session.geometryEnabled, session.radius,
                session.geometryEnabled ? snapshot.meshes : List.of()));
        session.lastGeometrySignature = snapshot.signature;
    }

    private static void refreshPlayer(ServerPlayer player, Session session) {
        AreaSnapshot areaSnapshot = collectAreaSnapshot(player, session);
        GeometrySnapshot geometrySnapshot = collectGeometrySnapshot(player, session);
        sendAreaSnapshot(player, session, areaSnapshot);
        sendGeometrySnapshot(player, session, geometrySnapshot);
        updateBaseline(player, session, areaSnapshot, geometrySnapshot);
    }

    private static void finishDisableOrRefresh(ServerPlayer player, Session session) {
        if (!session.hasAnyChannel()) {
            SESSIONS.remove(player.getUUID());
            sendDisabledSnapshots(player);
            return;
        }
        session.forceRefresh();
        refreshPlayer(player, session);
    }

    private static void sendDisabledSnapshots(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player, new PacketAreaDebugSnapshot(false, 0.0D, List.of()));
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(false, 0.0D, List.of()));
    }

    private static void updateBaseline(ServerPlayer player,
                                       Session session,
                                       AreaSnapshot areaSnapshot,
                                       GeometrySnapshot geometrySnapshot) {
        session.lastPosition = player.position();
        session.lastDimension = player.level().dimension();
        session.lastDirtyVersion = dirtyVersion;
        session.lastAreaSignature = areaSnapshot.signature;
        session.lastGeometrySignature = geometrySnapshot.signature;
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

    private static long mix(long signature, PacketGeometryDebugSnapshot.Mesh mesh) {
        signature = mix(signature, mesh.id().hashCode());
        signature = mix(signature, mesh.graphId().hashCode());
        signature = mix(signature, Double.doubleToLongBits(mesh.centerX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerZ()));
        signature = mix(signature, mesh.vertexCount());
        signature = mix(signature, mesh.edgeCount());
        signature = mix(signature, mesh.faceCount());
        for (float value : mesh.vertices()) {
            signature = mix(signature, Float.floatToIntBits(value));
        }
        for (int value : mesh.edges()) {
            signature = mix(signature, value);
        }
        for (int value : mesh.faces()) {
            signature = mix(signature, value);
        }
        return signature;
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

    private record GeometryCandidate(GeometryDebugMesh mesh, double distanceSqr) {
    }

    private record AreaSnapshot(List<PacketAreaDebugSnapshot.AreaBox> boxes, long signature) {
    }

    private record GeometrySnapshot(List<PacketGeometryDebugSnapshot.Mesh> meshes, long signature) {
    }

    private static final class LevelCache {
        private final Map<String, SourceCache> sources = new HashMap<>();
        private final GeometryDebugMeshStore geometrySources = new GeometryDebugMeshStore();
    }

    private static final class SourceCache {
        private final List<AreaDebugBox> boxes;
        private long lastSeenTick;
        private final long signature;
        private final long expiresAt;

        private SourceCache(List<AreaDebugBox> boxes, long lastSeenTick, long signature, long expiresAt) {
            this.boxes = boxes;
            this.lastSeenTick = lastSeenTick;
            this.signature = signature;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long currentTick) {
            if (expiresAt == Long.MAX_VALUE) {
                return false;
            }
            if (expiresAt > 0L) {
                return currentTick >= expiresAt;
            }
            return currentTick - lastSeenTick > IDLE_CHECK_INTERVAL_TICKS;
        }

        private boolean isTransientExpired(long currentTick) {
            return expiresAt > 0L && expiresAt != Long.MAX_VALUE && currentTick >= expiresAt;
        }
    }

    private static final class Session {
        private double radius = DEFAULT_RADIUS;
        private boolean areaBoxesEnabled;
        private boolean schematicBoxesEnabled;
        private boolean geometryEnabled;
        private boolean interactionBoxesEnabled;
        private Vec3 lastPosition;
        private ResourceKey<Level> lastDimension;
        private long lastDirtyVersion = Long.MIN_VALUE;
        private long lastAreaSignature = Long.MIN_VALUE;
        private long lastGeometrySignature = Long.MIN_VALUE;

        private boolean hasBoxChannels() {
            return areaBoxesEnabled || schematicBoxesEnabled || interactionBoxesEnabled;
        }

        private boolean hasAnyChannel() {
            return hasBoxChannels() || geometryEnabled;
        }

        private boolean isBoxSourceVisible(String sourceKey) {
            if (sourceKey.startsWith(AREA_SOURCE_PREFIX)) {
                return areaBoxesEnabled;
            }
            if (sourceKey.startsWith(SCHEMATIC_SOURCE_PREFIX)) {
                return schematicBoxesEnabled;
            }
            return false;
        }

        private void forceRefresh() {
            lastPosition = null;
            lastDimension = null;
            lastDirtyVersion = Long.MIN_VALUE;
            lastAreaSignature = Long.MIN_VALUE;
            lastGeometrySignature = Long.MIN_VALUE;
        }
    }
}
