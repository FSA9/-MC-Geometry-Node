package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugType;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
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

public final class DebugRendererSessionManager {
    public static final double DEFAULT_RADIUS = 256.0D;
    public static final int DEFAULT_MAX_MESHES = 100;
    public static final int TRANSIENT_QUERY_DURATION_TICKS = 60;

    private static final double MIN_RADIUS = 1.0D;
    private static final double MAX_RADIUS = 2048.0D;
    private static final double MOVE_REFRESH_DISTANCE = 32.0D;
    private static final double MOVE_REFRESH_DISTANCE_SQR = MOVE_REFRESH_DISTANCE * MOVE_REFRESH_DISTANCE;
    private static final int IDLE_CHECK_INTERVAL_TICKS = 20;
    private static final int PATHFINDING_REFRESH_INTERVAL_TICKS = 5;
    private static final int MAX_PATHFINDING_ENTITIES = 32;
    private static final int MAX_PATH_NODES = 128;
    private static final int REQUESTED_TARGET_RETENTION_TICKS = 100;
    private static final int PATH_COLOR = DebugRenderChannel.PATHFINDING.color();
    private static final int NEXT_NODE_COLOR = 0xFFFFC247;
    private static final int FINAL_TARGET_COLOR = 0xFF4FD17A;
    private static final int REQUESTED_TARGET_COLOR = 0xFF4A8DFF;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, RequestedPathTarget> REQUESTED_PATH_TARGETS = new HashMap<>();
    private static final Map<ServerLevel, LevelCache> LEVEL_CACHES = new IdentityHashMap<>();
    private static final List<Consumer<ServerPlayer>> SCHEMATIC_CHANNEL_HYDRATORS = new ArrayList<>();
    private static boolean registered;
    private static long dirtyVersion;

    private DebugRendererSessionManager() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SESSIONS.remove(player.getUUID());
                clearRequestedPathTargetsIfUnused();
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

    public static int enableArea(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.areaEnabled = true;
        finishEnable(player, session, "Area");
        return 1;
    }

    public static int enableAll(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.areaEnabled = true;
        session.schematicEnabled = true;
        session.geometryEnabled = true;
        session.interactionEnabled = true;
        session.pathfindingEnabled = true;
        hydrateSchematicChannel(player, session);
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal("All debug channels enabled. radius="
                + formatRadius(session.radius) + ", max=" + DEFAULT_MAX_MESHES));
        return 1;
    }

    public static int disableAll(ServerPlayer player, boolean notify) {
        boolean changed = SESSIONS.remove(player.getUUID()) != null;
        clearRequestedPathTargetsIfUnused();
        sendDisabledSnapshot(player);
        if (notify) {
            player.sendSystemMessage(Component.literal(changed
                    ? "All debug channels disabled."
                    : "Debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static int disableArea(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.areaEnabled;
        if (session != null) {
            session.areaEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        notifyDisabled(player, notify, changed, "Area");
        return changed ? 1 : 0;
    }

    public static int enableSchematic(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.schematicEnabled = true;
        hydrateSchematicChannel(player, session);
        finishEnable(player, session, "Schematic");
        return 1;
    }

    public static int disableSchematic(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.schematicEnabled;
        if (session != null) {
            session.schematicEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        notifyDisabled(player, notify, changed, "Schematic");
        return changed ? 1 : 0;
    }

    public static int enableGeometry(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.geometryEnabled = true;
        finishEnable(player, session, "Geometry");
        return 1;
    }

    public static int disableGeometry(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.geometryEnabled;
        if (session != null) {
            session.geometryEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        notifyDisabled(player, notify, changed, "Geometry");
        return changed ? 1 : 0;
    }

    public static int enableInteraction(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.interactionEnabled = true;
        finishEnable(player, session, "Interaction");
        return 1;
    }

    public static int disableInteraction(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.interactionEnabled;
        if (session != null) {
            session.interactionEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        notifyDisabled(player, notify, changed, "Interaction");
        return changed ? 1 : 0;
    }

    public static int enablePathfinding(ServerPlayer player, double radius) {
        Session session = enableChannel(player, radius);
        session.pathfindingEnabled = true;
        finishEnable(player, session, "Pathfinding");
        return 1;
    }

    public static int disablePathfinding(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        boolean changed = session != null && session.pathfindingEnabled;
        if (session != null) {
            session.pathfindingEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        clearRequestedPathTargetsIfUnused();
        notifyDisabled(player, notify, changed, "Pathfinding");
        return changed ? 1 : 0;
    }

    public static void recordRequestedPathTarget(Mob mob, Vec3 position) {
        if (mob == null || position == null || !hasPathfindingSessions()) return;
        REQUESTED_PATH_TARGETS.put(mob.getUUID(), new RequestedPathTarget(
                mob.level().dimension(), position,
                mob.level().getGameTime() + REQUESTED_TARGET_RETENTION_TICKS
        ));
    }

    public static void clearRequestedPathTarget(Mob mob) {
        if (mob != null && REQUESTED_PATH_TARGETS.remove(mob.getUUID()) != null) markDirty();
    }

    private static Session enableChannel(ServerPlayer player, double radius) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.radius = clampRadius(radius);
        session.forceRefresh();
        return session;
    }

    private static void finishEnable(ServerPlayer player, Session session, String channelName) {
        refreshPlayer(player, session);
        player.sendSystemMessage(Component.literal(channelName + " debug enabled. radius="
                + formatRadius(session.radius) + ", max=" + DEFAULT_MAX_MESHES));
    }

    private static void notifyDisabled(ServerPlayer player, boolean notify, boolean changed, String channelName) {
        if (notify) {
            player.sendSystemMessage(Component.literal(changed
                    ? channelName + " debug disabled."
                    : channelName + " debug is not enabled."));
        }
    }

    public static void registerSchematicChannelHydrator(Consumer<ServerPlayer> hydrator) {
        if (hydrator != null) {
            SCHEMATIC_CHANNEL_HYDRATORS.add(hydrator);
        }
    }

    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty()) return;

        long tick = level.getGameTime();
        boolean cadence = Math.floorMod(tick, IDLE_CHECK_INTERVAL_TICKS) == 0;
        boolean pathfindingCadence = Math.floorMod(tick, PATHFINDING_REFRESH_INTERVAL_TICKS) == 0;
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
            boolean refresh = cadence || dimensionChanged || moved || dirty || hasExpiredSources
                    || session.pathfindingEnabled && pathfindingCadence;
            if (!session.interactionEnabled && !refresh) continue;

            MeshSnapshot snapshot = collectSnapshot(player, session);
            if (snapshot.signature != session.lastSignature) {
                sendSnapshot(player, session, snapshot);
            }
            updateBaseline(player, session, snapshot);
        }
    }

    public static void replaceSourceShapes(ServerLevel level,
                                           String sourceKey,
                                           List<DebugRenderShape> shapes,
                                           long seenTick) {
        replaceSourceShapes(level, sourceKey, shapes, seenTick, -1L);
    }

    public static void replacePersistentSourceShapes(ServerLevel level,
                                                     String sourceKey,
                                                     List<DebugRenderShape> shapes,
                                                     long seenTick) {
        replaceSourceShapes(level, sourceKey, shapes, seenTick, Long.MAX_VALUE);
    }

    public static void showTransientQueryArea(ServerLevel level,
                                              String graphId,
                                              String nodeId,
                                              String shape,
                                              Vec3 center,
                                              Vec3 size,
                                              Vec3 rotation) {
        if (level == null || center == null || size == null || !hasAreaSessions()) return;

        String safeGraphId = graphId == null || graphId.isBlank() ? "unknown" : graphId;
        String safeNodeId = nodeId == null || nodeId.isBlank() ? "unknown" : nodeId;
        String safeShape = shape == null || shape.isBlank() ? "box" : shape;
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;
        int queryHash = java.util.Objects.hash(safeShape, center, size, safeRotation);
        String sourceKey = DebugRenderChannel.AREA.sourcePrefix() + "query:" + level.dimension().identifier() + ":"
                + safeGraphId + ":" + safeNodeId + ":" + Integer.toUnsignedString(queryHash, 16);
        long currentTick = level.getGameTime();
        DebugRenderShape renderShape = new DebugRenderShape(
                sourceKey, safeGraphId, safeShape, center, size, safeRotation, DebugRenderChannel.AREA.color());
        replaceSourceShapes(level, sourceKey, List.of(renderShape), currentTick,
                currentTick + TRANSIENT_QUERY_DURATION_TICKS);
    }

    private static void replaceSourceShapes(ServerLevel level,
                                            String sourceKey,
                                            List<DebugRenderShape> shapes,
                                            long seenTick,
                                            long expiresAt) {
        List<GeometryDebugElement> meshes = new ArrayList<>(shapes.size());
        for (DebugRenderShape shape : shapes) {
            if (shape != null) {
                meshes.add(GeometryDebugMeshFactory.buildShapeMesh(shape));
            }
        }
        replaceSourceMeshes(level, sourceKey, meshes, seenTick, expiresAt);
    }

    public static void removeSourceShapes(ServerLevel level, String sourceKey) {
        removeSource(level, sourceKey);
    }

    public static void replaceSourceGeometry(ServerLevel level,
                                             String sourceKey,
                                             List<GeometryDebugElement> meshes) {
        List<GeometryDebugElement> whiteMeshes = new ArrayList<>(meshes.size());
        for (GeometryDebugElement mesh : meshes) {
            if (mesh != null) {
                whiteMeshes.add(withColor(mesh, DebugRenderChannel.GEOMETRY.color()));
            }
        }
        replaceSourceMeshes(level, sourceKey, whiteMeshes, level.getGameTime(), Long.MAX_VALUE);
    }

    public static void removeSourceGeometry(ServerLevel level, String sourceKey) {
        removeSource(level, sourceKey);
    }

    private static GeometryDebugElement withColor(GeometryDebugElement mesh, int color) {
        if (mesh.color() == color) return mesh;
        return new GeometryDebugElement(
                mesh.id(), mesh.graphId(), mesh.type(), color, mesh.showPoints(),
                mesh.center(), mesh.size(), mesh.rotation(),
                mesh.vertices(), mesh.edges(), mesh.faces()
        );
    }

    private static void replaceSourceMeshes(ServerLevel level,
                                            String sourceKey,
                                            List<GeometryDebugElement> meshes,
                                            long seenTick,
                                            long expiresAt) {
        LevelCache cache = LEVEL_CACHES.computeIfAbsent(level, ignored -> new LevelCache());
        SourceCache source = cache.sources.get(sourceKey);
        long signature = sourceSignature(meshes);
        if (source != null && source.lastSeenTick == seenTick
                && source.signature == signature && source.expiresAt == expiresAt) {
            return;
        }
        if (source != null && source.signature == signature && source.expiresAt == expiresAt) {
            source.lastSeenTick = seenTick;
            return;
        }
        cache.sources.put(sourceKey, new SourceCache(List.copyOf(meshes), seenTick, signature, expiresAt));
        dirtyVersion++;
    }

    private static void removeSource(ServerLevel level, String sourceKey) {
        LevelCache cache = LEVEL_CACHES.get(level);
        if (cache != null && cache.sources.remove(sourceKey) != null) {
            dirtyVersion++;
        }
    }

    public static void markDirty() {
        dirtyVersion++;
    }

    private static void hydrateSchematicChannel(ServerPlayer player, Session session) {
        if (player == null || session == null || !session.schematicEnabled) return;
        for (Consumer<ServerPlayer> hydrator : SCHEMATIC_CHANNEL_HYDRATORS) {
            hydrator.accept(player);
        }
    }

    public static boolean hasAreaSessions() {
        for (Session session : SESSIONS.values()) {
            if (session.areaEnabled) return true;
        }
        return false;
    }

    private static boolean hasPathfindingSessions() {
        for (Session session : SESSIONS.values()) {
            if (session.pathfindingEnabled) return true;
        }
        return false;
    }

    private static void clearRequestedPathTargetsIfUnused() {
        if (!hasPathfindingSessions()) REQUESTED_PATH_TARGETS.clear();
    }

    public static String levelSourceKey(ServerLevel level, String graphId) {
        return DebugRenderChannel.AREA.sourcePrefix() + "level:" + level.dimension().identifier() + ":" + graphId;
    }

    public static String entitySourceKey(ServerLevel level, net.minecraft.world.entity.Entity entity, String graphId) {
        return DebugRenderChannel.AREA.sourcePrefix() + "entity:" + level.dimension().identifier() + ":"
                + entity.getUUID() + ":" + graphId;
    }

    public static String geometryMeshSourceKey(ServerLevel level, String key) {
        return DebugRenderChannel.GEOMETRY.sourcePrefix() + "mesh:" + level.dimension().identifier() + ":" + key.trim();
    }

    public static String schematicPlacementSourceKey(ServerLevel level, String key) {
        return DebugRenderChannel.SCHEMATIC.sourcePrefix() + "placement:" + level.dimension().identifier() + ":" + key.trim();
    }

    private static MeshSnapshot collectSnapshot(ServerPlayer player, Session session) {
        if (!session.hasAnyChannel()) return new MeshSnapshot(List.of(), 1L);

        ServerLevel level = player.level();
        LevelCache cache = LEVEL_CACHES.get(level);
        long currentTick = level.getGameTime();
        double radiusSqr = session.radius * session.radius;
        Vec3 origin = player.position();
        List<Candidate> candidates = new ArrayList<>();
        boolean removedExpiredSource = false;

        if (cache != null && !cache.sources.isEmpty()) {
            var iterator = cache.sources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, SourceCache> entry = iterator.next();
                SourceCache source = entry.getValue();
                if (source.isExpired(currentTick)) {
                    iterator.remove();
                    removedExpiredSource = true;
                    continue;
                }
                if (!session.isSourceVisible(entry.getKey())) continue;
                for (GeometryDebugElement mesh : source.meshes) {
                    addCandidate(level, origin, radiusSqr, mesh, candidates);
                }
            }
        }

        if (session.interactionEnabled) {
            collectInteractionCandidates(level, origin, session.radius, radiusSqr, candidates);
        }
        if (session.pathfindingEnabled) {
            collectPathfindingCandidates(level, origin, session.radius, radiusSqr, candidates);
        }
        if (removedExpiredSource) dirtyVersion++;

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
            PacketGeometryDebugSnapshot.Mesh packetMesh = toPacketMesh(candidates.get(i).mesh);
            meshes.add(packetMesh);
            signature = mix(signature, packetMesh);
        }
        signature = signature * 31L + count;
        signature = signature * 31L + Double.doubleToLongBits(session.radius);
        return new MeshSnapshot(meshes, signature);
    }

    private static void addCandidate(ServerLevel level,
                                     Vec3 origin,
                                     double radiusSqr,
                                     GeometryDebugElement mesh,
                                     List<Candidate> candidates) {
        if (!isCenterChunkLoaded(level, mesh.center())) return;
        double distanceSqr = mesh.center().distanceToSqr(origin);
        if (distanceSqr <= radiusSqr) {
            candidates.add(new Candidate(mesh, distanceSqr));
        }
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
            if (center.distanceToSqr(origin) > radiusSqr) continue;
            DebugRenderShape shape = new DebugRenderShape(
                    DebugRenderChannel.INTERACTION.sourcePrefix() + interaction.getStringUUID(),
                    "interaction",
                    "box",
                    center,
                    new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()),
                    Vec3.ZERO,
                    DebugRenderChannel.INTERACTION.color()
            );
            GeometryDebugElement mesh = GeometryDebugMeshFactory.buildShapeMesh(shape);
            candidates.add(new Candidate(mesh, center.distanceToSqr(origin)));
        }
    }

    private static void collectPathfindingCandidates(ServerLevel level,
                                                      Vec3 origin,
                                                      double radius,
                                                      double radiusSqr,
                                                      List<Candidate> candidates) {
        AABB queryBounds = AABB.ofSize(origin, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        long currentTick = level.getGameTime();
        REQUESTED_PATH_TARGETS.entrySet().removeIf(entry -> {
            RequestedPathTarget target = entry.getValue();
            return target.dimension().equals(level.dimension()) && target.expiresAt() < currentTick;
        });
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, queryBounds, mob -> {
            Path path = mob.getNavigation().getPath();
            return !mob.isRemoved() && (path != null && !path.isDone()
                    || requestedPathTarget(mob, level, currentTick) != null);
        });
        mobs.sort((left, right) -> Double.compare(left.distanceToSqr(origin), right.distanceToSqr(origin)));

        int entityCount = Math.min(MAX_PATHFINDING_ENTITIES, mobs.size());
        for (int i = 0; i < entityCount; i++) {
            Mob mob = mobs.get(i);
            if (mob.distanceToSqr(origin) > radiusSqr) continue;
            Path path = mob.getNavigation().getPath();
            RequestedPathTarget requested = requestedPathTarget(mob, level, currentTick);
            if (path == null || path.isDone()) {
                if (requested != null) {
                    addPathMarker(mob, "requested", requested.position(),
                            REQUESTED_TARGET_COLOR, candidates, origin);
                }
                continue;
            }

            BlockPos pathEndPos = path.getNodePos(path.getNodeCount() - 1);
            addPathMesh(mob, path, candidates, origin);
            addPathMarker(mob, "next", path.getNextNodePos(), NEXT_NODE_COLOR, candidates, origin);
            if (requested != null) {
                addPathMarker(mob, "requested", requested.position(),
                        REQUESTED_TARGET_COLOR, candidates, origin);
            }
            if (requested == null || !pathEndPos.equals(BlockPos.containing(requested.position()))) {
                addPathMarker(mob, "target", pathEndPos, FINAL_TARGET_COLOR, candidates, origin);
            }
        }
    }

    private static RequestedPathTarget requestedPathTarget(Mob mob, ServerLevel level, long currentTick) {
        RequestedPathTarget target = REQUESTED_PATH_TARGETS.get(mob.getUUID());
        return target != null && target.dimension().equals(level.dimension()) && target.expiresAt() >= currentTick
                ? target : null;
    }

    private static void addPathMesh(Mob mob, Path path, List<Candidate> candidates, Vec3 origin) {
        int firstNode = path.getNextNodeIndex();
        int nodeCount = Math.min(path.getNodeCount() - firstNode, MAX_PATH_NODES);
        if (nodeCount <= 0) return;

        Vec3 center = Vec3.atCenterOf(mob.blockPosition());
        float[] vertices = new float[(nodeCount + 1) * 3];
        writeRelativeVertex(vertices, 0, center, center);
        for (int i = 0; i < nodeCount; i++) {
            writeRelativeVertex(vertices, i + 1,
                    Vec3.atCenterOf(path.getNodePos(firstNode + i)), center);
        }
        int[] edges = new int[nodeCount * 2];
        for (int i = 0; i < nodeCount; i++) {
            edges[i * 2] = i;
            edges[i * 2 + 1] = i + 1;
        }

        String id = DebugRenderChannel.PATHFINDING.sourcePrefix() + mob.getStringUUID() + ":path";
        GeometryDebugElement mesh = new GeometryDebugElement(
                id, "pathfinding", GeometryDebugType.MESH, PATH_COLOR, true,
                center, Vec3.ZERO, Vec3.ZERO, vertices, edges, new int[0]
        );
        candidates.add(new Candidate(mesh, center.distanceToSqr(origin)));
    }

    private static void addPathMarker(Mob mob,
                                      String markerName,
                                      BlockPos blockPos,
                                      int color,
                                      List<Candidate> candidates,
                                      Vec3 origin) {
        addPathMarker(mob, markerName, Vec3.atCenterOf(blockPos), color, candidates, origin);
    }

    private static void addPathMarker(Mob mob,
                                      String markerName,
                                      Vec3 position,
                                      int color,
                                      List<Candidate> candidates,
                                      Vec3 origin) {
        String id = DebugRenderChannel.PATHFINDING.sourcePrefix() + mob.getStringUUID() + ":" + markerName;
        DebugRenderShape shape = new DebugRenderShape(
                id, "pathfinding", "box", position, new Vec3(1.0D, 1.0D, 1.0D), Vec3.ZERO, color
        );
        candidates.add(new Candidate(GeometryDebugMeshFactory.buildShapeMesh(shape), position.distanceToSqr(origin)));
    }

    private static void writeRelativeVertex(float[] vertices, int index, Vec3 position, Vec3 center) {
        int offset = index * 3;
        vertices[offset] = (float) (position.x - center.x);
        vertices[offset + 1] = (float) (position.y - center.y);
        vertices[offset + 2] = (float) (position.z - center.z);
    }

    private static boolean isCenterChunkLoaded(ServerLevel level, Vec3 center) {
        return level.isLoaded(BlockPos.containing(center));
    }

    private static PacketGeometryDebugSnapshot.Mesh toPacketMesh(GeometryDebugElement mesh) {
        Vec3 center = mesh.center();
        return new PacketGeometryDebugSnapshot.Mesh(
                mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(),
                center.x, center.y, center.z,
                mesh.size().x, mesh.size().y, mesh.size().z,
                mesh.rotation().x, mesh.rotation().y, mesh.rotation().z,
                mesh.vertices(), mesh.edges(), mesh.faces()
        );
    }

    private static void sendSnapshot(ServerPlayer player, Session session, MeshSnapshot snapshot) {
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(
                session.hasAnyChannel(), session.radius,
                session.hasAnyChannel() ? snapshot.meshes : List.of()));
        session.lastSignature = snapshot.signature;
    }

    private static void refreshPlayer(ServerPlayer player, Session session) {
        MeshSnapshot snapshot = collectSnapshot(player, session);
        sendSnapshot(player, session, snapshot);
        updateBaseline(player, session, snapshot);
    }

    private static void finishDisableOrRefresh(ServerPlayer player, Session session) {
        if (!session.hasAnyChannel()) {
            SESSIONS.remove(player.getUUID());
            sendDisabledSnapshot(player);
            return;
        }
        session.forceRefresh();
        refreshPlayer(player, session);
    }

    private static void sendDisabledSnapshot(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(false, 0.0D, List.of()));
    }

    private static void updateBaseline(ServerPlayer player, Session session, MeshSnapshot snapshot) {
        session.lastPosition = player.position();
        session.lastDimension = player.level().dimension();
        session.lastDirtyVersion = dirtyVersion;
        session.lastSignature = snapshot.signature;
    }

    private static long sourceSignature(List<GeometryDebugElement> meshes) {
        long signature = 1469598103934665603L;
        for (GeometryDebugElement mesh : meshes) {
            signature = mix(signature, mesh.id().hashCode());
            signature = mix(signature, mesh.graphId().hashCode());
            signature = mix(signature, mesh.type().networkId());
            signature = mix(signature, mesh.color());
            signature = mix(signature, mesh.showPoints() ? 1L : 0L);
            signature = mix(signature, Double.doubleToLongBits(mesh.center().x));
            signature = mix(signature, Double.doubleToLongBits(mesh.center().y));
            signature = mix(signature, Double.doubleToLongBits(mesh.center().z));
            signature = mix(signature, Double.doubleToLongBits(mesh.size().x));
            signature = mix(signature, Double.doubleToLongBits(mesh.size().y));
            signature = mix(signature, Double.doubleToLongBits(mesh.size().z));
            signature = mix(signature, Double.doubleToLongBits(mesh.rotation().x));
            signature = mix(signature, Double.doubleToLongBits(mesh.rotation().y));
            signature = mix(signature, Double.doubleToLongBits(mesh.rotation().z));
            for (float value : mesh.vertices()) signature = mix(signature, Float.floatToIntBits(value));
            for (int value : mesh.edges()) signature = mix(signature, value);
            for (int value : mesh.faces()) signature = mix(signature, value);
        }
        return signature * 31L + meshes.size();
    }

    private static long mix(long signature, PacketGeometryDebugSnapshot.Mesh mesh) {
        signature = mix(signature, mesh.id().hashCode());
        signature = mix(signature, mesh.graphId().hashCode());
        signature = mix(signature, mesh.geometryType().networkId());
        signature = mix(signature, mesh.color());
        signature = mix(signature, mesh.showPoints() ? 1L : 0L);
        signature = mix(signature, Double.doubleToLongBits(mesh.centerX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.centerZ()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.sizeZ()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationX()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationY()));
        signature = mix(signature, Double.doubleToLongBits(mesh.rotationZ()));
        for (float value : mesh.vertices()) signature = mix(signature, Float.floatToIntBits(value));
        for (int value : mesh.edges()) signature = mix(signature, value);
        for (int value : mesh.faces()) signature = mix(signature, value);
        return signature;
    }

    private static long mix(long signature, long value) {
        return (signature ^ value) * 1099511628211L;
    }

    private static double clampRadius(double radius) {
        if (!Double.isFinite(radius)) return DEFAULT_RADIUS;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    private static String formatRadius(double radius) {
        if (radius == Math.rint(radius)) return Long.toString(Math.round(radius));
        return String.format(java.util.Locale.ROOT, "%.2f", radius);
    }

    private record Candidate(GeometryDebugElement mesh, double distanceSqr) {
    }

    private record MeshSnapshot(List<PacketGeometryDebugSnapshot.Mesh> meshes, long signature) {
    }

    private record RequestedPathTarget(ResourceKey<Level> dimension, Vec3 position, long expiresAt) {
    }

    private static final class LevelCache {
        private final Map<String, SourceCache> sources = new HashMap<>();
    }

    private static final class SourceCache {
        private final List<GeometryDebugElement> meshes;
        private long lastSeenTick;
        private final long signature;
        private final long expiresAt;

        private SourceCache(List<GeometryDebugElement> meshes, long lastSeenTick, long signature, long expiresAt) {
            this.meshes = meshes;
            this.lastSeenTick = lastSeenTick;
            this.signature = signature;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long currentTick) {
            if (expiresAt == Long.MAX_VALUE) return false;
            if (expiresAt > 0L) return currentTick >= expiresAt;
            return currentTick - lastSeenTick > IDLE_CHECK_INTERVAL_TICKS;
        }

        private boolean isTransientExpired(long currentTick) {
            return expiresAt > 0L && expiresAt != Long.MAX_VALUE && currentTick >= expiresAt;
        }
    }

    private static final class Session {
        private double radius = DEFAULT_RADIUS;
        private boolean areaEnabled;
        private boolean schematicEnabled;
        private boolean geometryEnabled;
        private boolean interactionEnabled;
        private boolean pathfindingEnabled;
        private Vec3 lastPosition;
        private ResourceKey<Level> lastDimension;
        private long lastDirtyVersion = Long.MIN_VALUE;
        private long lastSignature = Long.MIN_VALUE;

        private boolean hasAnyChannel() {
            return areaEnabled || schematicEnabled || geometryEnabled || interactionEnabled || pathfindingEnabled;
        }

        private boolean isSourceVisible(String sourceKey) {
            if (DebugRenderChannel.AREA.owns(sourceKey)) return areaEnabled;
            if (DebugRenderChannel.GEOMETRY.owns(sourceKey)) return geometryEnabled;
            if (DebugRenderChannel.SCHEMATIC.owns(sourceKey)) return schematicEnabled;
            if (DebugRenderChannel.PATHFINDING.owns(sourceKey)) return pathfindingEnabled;
            return false;
        }

        private void forceRefresh() {
            lastPosition = null;
            lastDimension = null;
            lastDirtyVersion = Long.MIN_VALUE;
            lastSignature = Long.MIN_VALUE;
        }
    }
}
