package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
    private static final int REQUESTED_TARGET_RETENTION_TICKS = 100;
    private static final int FOLLOW_TARGET_RETENTION_TICKS = 20;

    private static final Map<MinecraftServer, DebugServerState> SERVERS = new IdentityHashMap<>();
    private static final List<Consumer<ServerPlayer>> SCHEMATIC_CHANNEL_HYDRATORS = new ArrayList<>();
    private static final DebugSnapshotAssembler SNAPSHOT_ASSEMBLER = new DebugSnapshotAssembler();
    private static boolean registered;

    private DebugRendererSessionManager() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        GraphResourceLifecycleManager.INSTANCE.registerStore("graph_debug", DebugRendererSessionManager::removeGraphResources);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DebugServerState state = getState(player.level().getServer());
                if (state != null) {
                    state.sessions.remove(player.getUUID());
                    clearRequestedPathTargetsIfUnused(state);
                    discardStateIfEmpty(player.level().getServer(), state);
                }
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DebugServerState state = getState(player.level().getServer());
                if (state == null) return;
                markDirty(state);
                Session session = state.sessions.get(player.getUUID());
                if (session != null) {
                    session.forceRefresh();
                    hydrateSchematicChannel(player, session);
                    refreshPlayer(player, session);
                }
            }
        });
        bus.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                markDirty(level.getServer());
            }
        });
        bus.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                markDirty(level.getServer());
            }
        });
    }

    /** Releases state owned by the stopping server while retaining registered hydrators. */
    public static void shutdown(MinecraftServer server) {
        if (server != null) SERVERS.remove(server);
    }

    public static void levelUnloaded(ServerLevel level) {
        if (level == null) return;
        DebugServerState state = getState(level.getServer());
        if (state == null) return;
        boolean changed = state.sources.removeLevel(level);
        changed |= state.observations.remove(level) != null;
        ResourceKey<Level> dimension = level.dimension();
        changed |= state.pathState.removeDimension(dimension);
        if (changed) markDirty(state);
        discardStateIfEmpty(level.getServer(), state);
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
        MinecraftServer server = player.level().getServer();
        DebugServerState state = state(server);
        boolean changed = state.sessions.remove(player.getUUID()) != null;
        clearRequestedPathTargetsIfUnused(state);
        discardStateIfEmpty(server, state);
        sendDisabledSnapshot(player);
        if (notify) {
            player.sendSystemMessage(Component.literal(changed
                    ? "All debug channels disabled."
                    : "Debug is not enabled."));
        }
        return changed ? 1 : 0;
    }

    public static int disableArea(ServerPlayer player, boolean notify) {
        Session session = session(player);
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
        Session session = session(player);
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
        Session session = session(player);
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
        Session session = session(player);
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
        DebugServerState state = getState(player.level().getServer());
        Session session = state != null ? state.sessions.get(player.getUUID()) : null;
        boolean changed = session != null && session.pathfindingEnabled;
        if (session != null) {
            session.pathfindingEnabled = false;
            finishDisableOrRefresh(player, session);
        } else {
            sendDisabledSnapshot(player);
        }
        if (state != null) {
            clearRequestedPathTargetsIfUnused(state);
            discardStateIfEmpty(player.level().getServer(), state);
        }
        notifyDisabled(player, notify, changed, "Pathfinding");
        return changed ? 1 : 0;
    }

    /**
     * Behavior-tree-only producer hook at present. The shared pathfinding channel also renders
     * vanilla navigation state, so the channel itself is not behavior-tree-specific.
     */
    public static void recordRequestedPathTarget(Mob mob, Vec3 position) {
        if (mob == null || position == null || !(mob.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state == null || !hasPathfindingSessions(state)) return;
        state.pathState.recordRequested(mob, position,
                level.getGameTime() + REQUESTED_TARGET_RETENTION_TICKS);
    }

    /** Clears the requested target written through the behavior-tree producer hook above. */
    public static void clearRequestedPathTarget(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state != null && state.pathState.clearRequested(mob.getUUID())) {
            invalidatePathfinding(state, level);
            markDirty(state);
        }
    }

    /** Behavior-tree producer hook for an active entity-to-entity Follow relationship. */
    public static void recordFollowTarget(Mob follower, Entity target) {
        if (follower == null || target == null || !(follower.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state == null || !hasPathfindingSessions(state)) return;
        state.pathState.recordFollow(follower, target,
                level.getGameTime() + FOLLOW_TARGET_RETENTION_TICKS);
    }

    /** Removes the active Follow relationship immediately when its action exits. */
    public static void clearFollowTarget(Mob follower) {
        if (follower == null || !(follower.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state != null && state.pathState.clearFollow(follower.getUUID())) {
            invalidatePathfinding(state, level);
            markDirty(state);
        }
    }

    /** Records the frozen waypoint route owned by an active Patrol action. */
    public static void recordPatrolRoute(Mob mob, List<Vec3> waypoints,
                                         int completedCount, boolean loop) {
        if (mob == null || waypoints == null || waypoints.isEmpty()
                || !(mob.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state == null || !hasPathfindingSessions(state)) return;
        int completed = Math.max(0, Math.min(completedCount, waypoints.size()));
        if (state.pathState.recordPatrol(mob, waypoints, completed, loop)) {
            invalidatePathfinding(state, level);
            markDirty(state);
        }
    }

    /** Removes the frozen route when Patrol stops, fails, or is preempted. */
    public static void clearPatrolRoute(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return;
        DebugServerState state = getState(level.getServer());
        if (state != null && state.pathState.clearPatrol(mob.getUUID())) {
            invalidatePathfinding(state, level);
            markDirty(state);
        }
    }

    private static Session enableChannel(ServerPlayer player, double radius) {
        Session session = state(player.level().getServer()).sessions.computeIfAbsent(
                player.getUUID(), ignored -> new Session());
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
        DebugServerState state = getState(level.getServer());
        if (state == null || state.sessions.isEmpty()) return;

        long tick = level.getGameTime();
        boolean cadence = Math.floorMod(tick, IDLE_CHECK_INTERVAL_TICKS) == 0;
        boolean pathfindingCadence = Math.floorMod(tick, PATHFINDING_REFRESH_INTERVAL_TICKS) == 0;
        boolean hasExpiredSources = state.sources.hasTransientExpired(level, tick);
        if (hasExpiredSources && state.sources.pruneExpired(level, tick)) markDirty(state);

        boolean hasInteractionObservers = hasObservers(state, level, true);
        boolean hasPathObservers = hasObservers(state, level, false);
        DebugLevelObservationCache observationCache = state.observations.get(level);
        boolean pathfindingInvalidated = observationCache != null
                && observationCache.needsPathfindingRefresh();
        refreshObservations(state, level,
                hasInteractionObservers,
                hasPathObservers && (pathfindingCadence || pathfindingInvalidated),
                false);

        for (ServerPlayer player : level.players()) {
            Session session = state.sessions.get(player.getUUID());
            if (session == null) continue;

            ResourceKey<Level> dimension = level.dimension();
            boolean dimensionChanged = !dimension.equals(session.lastDimension);
            boolean moved = session.lastPosition == null
                    || session.lastPosition.distanceToSqr(player.position()) >= MOVE_REFRESH_DISTANCE_SQR;
            boolean dirty = session.lastDirtyVersion != state.dirtyVersion;
            boolean refresh = cadence || dimensionChanged || moved || dirty || hasExpiredSources
                    || session.pathfindingEnabled && pathfindingCadence;
            if (!session.interactionEnabled && !refresh) continue;

            DebugSnapshotAssembler.Snapshot snapshot = collectSnapshot(state, player, session);
            if (snapshot.signature() != session.lastSignature) {
                sendSnapshot(player, session, snapshot);
            }
            updateBaseline(state, player, session, snapshot);
        }
    }

    public static void replaceSourceShapes(ServerLevel level,
                                           DebugSourceId sourceId,
                                           List<DebugRenderShape> shapes,
                                           long seenTick) {
        replaceSourceShapes(level, sourceId, shapes, seenTick, -1L);
    }

    public static void replacePersistentSourceShapes(ServerLevel level,
                                                     DebugSourceId sourceId,
                                                     List<DebugRenderShape> shapes,
                                                     long seenTick) {
        replaceSourceShapes(level, sourceId, shapes, seenTick, Long.MAX_VALUE);
    }

    public static void showTransientQueryArea(ServerLevel level,
                                              GraphResourceId resourceId,
                                              String shape,
                                              Vec3 center,
                                              Vec3 size,
                                              Vec3 rotation) {
        if (level == null || center == null || size == null
                || !hasAreaSessions(level.getServer())) return;

        String safeShape = shape == null || shape.isBlank() ? "box" : shape;
        Vec3 safeRotation = rotation != null ? rotation : Vec3.ZERO;
        DebugSourceId sourceId = DebugSourceId.graph(DebugRenderChannel.AREA, resourceId);
        long currentTick = level.getGameTime();
        DebugRenderShape renderShape = new DebugRenderShape(
                DebugSourceIdCodec.element(sourceId, "query"), resourceId.binding().graphId(),
                safeShape, center, size, safeRotation, DebugRenderChannel.AREA.color());
        replaceSourceShapes(level, sourceId, List.of(renderShape), currentTick,
                currentTick + TRANSIENT_QUERY_DURATION_TICKS);
    }

    private static void replaceSourceShapes(ServerLevel level,
                                            DebugSourceId sourceId,
                                            List<DebugRenderShape> shapes,
                                            long seenTick,
                                            long expiresAt) {
        List<GeometryDebugElement> meshes = new ArrayList<>(shapes.size());
        for (DebugRenderShape shape : shapes) {
            if (shape != null) {
                meshes.add(GeometryDebugMeshFactory.buildShapeMesh(shape));
            }
        }
        replaceSourceMeshes(level, sourceId, meshes, seenTick, expiresAt);
    }

    public static void removeSourceShapes(ServerLevel level, DebugSourceId sourceId) {
        removeSource(level, sourceId);
    }

    public static void replaceSourceGeometry(ServerLevel level,
                                             DebugSourceId sourceId,
                                             List<GeometryDebugElement> meshes) {
        List<GeometryDebugElement> whiteMeshes = new ArrayList<>(meshes.size());
        for (GeometryDebugElement mesh : meshes) {
            if (mesh != null) {
                whiteMeshes.add(withColor(mesh, DebugRenderChannel.GEOMETRY.color()));
            }
        }
        replaceSourceMeshes(level, sourceId, whiteMeshes, level.getGameTime(), Long.MAX_VALUE);
    }

    public static void removeSourceGeometry(ServerLevel level, DebugSourceId sourceId) {
        removeSource(level, sourceId);
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
                                            DebugSourceId sourceId,
                                            List<GeometryDebugElement> meshes,
                                            long seenTick,
                                            long expiresAt) {
        DebugServerState state = state(level.getServer());
        long signature = sourceSignature(meshes);
        if (state.sources.replace(level, sourceId, meshes, seenTick, expiresAt, signature)) markDirty(state);
    }

    private static void removeSource(ServerLevel level, DebugSourceId sourceId) {
        DebugServerState state = getState(level.getServer());
        if (state == null) return;
        if (state.sources.remove(level, sourceId)) {
            markDirty(state);
            discardStateIfEmpty(level.getServer(), state);
        }
    }

    private static void removeGraphResources(MinecraftServer server, GraphResourceRelease release) {
        DebugServerState state = getState(server);
        if (state == null) return;
        if (state.sources.removeGraphResources(release)) markDirty(state);
        discardStateIfEmpty(server, state);
    }

    public static void markDirty(MinecraftServer server) {
        DebugServerState state = server != null ? getState(server) : null;
        if (state != null) markDirty(state);
    }

    private static void hydrateSchematicChannel(ServerPlayer player, Session session) {
        if (player == null || session == null || !session.schematicEnabled) return;
        for (Consumer<ServerPlayer> hydrator : SCHEMATIC_CHANNEL_HYDRATORS) {
            hydrator.accept(player);
        }
    }

    public static boolean hasAreaSessions(MinecraftServer server) {
        DebugServerState state = getState(server);
        if (state == null) return false;
        for (Session session : state.sessions.values()) {
            if (session.areaEnabled) return true;
        }
        return false;
    }

    private static boolean hasPathfindingSessions(DebugServerState state) {
        for (Session session : state.sessions.values()) {
            if (session.pathfindingEnabled) return true;
        }
        return false;
    }

    private static boolean hasObservers(DebugServerState state, ServerLevel level, boolean interaction) {
        for (ServerPlayer player : level.players()) {
            Session session = state.sessions.get(player.getUUID());
            if (session != null && (interaction ? session.interactionEnabled : session.pathfindingEnabled)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshObservations(DebugServerState state,
                                            ServerLevel level,
                                            boolean refreshInteraction,
                                            boolean refreshPathfinding,
                                            boolean force) {
        if (!refreshInteraction && !refreshPathfinding) return;
        List<DebugObserverArea> interactionObservers = new ArrayList<>();
        List<DebugObserverArea> pathObservers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            Session session = state.sessions.get(player.getUUID());
            if (session == null) continue;
            if (session.interactionEnabled) {
                interactionObservers.add(new DebugObserverArea(player.position(), session.radius));
            }
            if (session.pathfindingEnabled) {
                pathObservers.add(new DebugObserverArea(player.position(), session.radius));
            }
        }
        DebugLevelObservationCache cache = state.observations.computeIfAbsent(
                level, ignored -> new DebugLevelObservationCache());
        cache.refresh(level, interactionObservers, pathObservers, state.pathState,
                refreshInteraction, refreshPathfinding, force, MAX_PATHFINDING_ENTITIES);
    }

    private static void invalidatePathfinding(DebugServerState state, ServerLevel level) {
        DebugLevelObservationCache cache = state.observations.get(level);
        if (cache != null) cache.invalidatePathfinding();
    }

    private static void clearRequestedPathTargetsIfUnused(DebugServerState state) {
        boolean interactionInUse = state.sessions.values().stream().anyMatch(session -> session.interactionEnabled);
        if (!interactionInUse) {
            state.observations.values().forEach(DebugLevelObservationCache::clearInteractions);
        }
        if (!hasPathfindingSessions(state)) {
            state.pathState.clear();
            state.observations.values().forEach(DebugLevelObservationCache::clearPathfinding);
        }
    }

    private static DebugSnapshotAssembler.Snapshot collectSnapshot(DebugServerState state,
                                                                    ServerPlayer player,
                                                                    Session session) {
        if (!session.hasAnyChannel()) return new DebugSnapshotAssembler.Snapshot(List.of(), 1L);
        ServerLevel level = player.level();
        if (state.sources.pruneExpired(level, level.getGameTime())) markDirty(state);
        return SNAPSHOT_ASSEMBLER.assemble(
                level, player.position(), session.radius, session.channelMask(),
                DEFAULT_MAX_MESHES, MAX_PATHFINDING_ENTITIES,
                state.sources, state.observations.get(level));
    }

    private static void sendSnapshot(ServerPlayer player, Session session, DebugSnapshotAssembler.Snapshot snapshot) {
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(
                session.hasAnyChannel(), session.radius,
                session.hasAnyChannel() ? snapshot.meshes() : List.of()));
        session.lastSignature = snapshot.signature();
    }

    private static void refreshPlayer(ServerPlayer player, Session session) {
        DebugServerState state = state(player.level().getServer());
        refreshObservations(state, player.level(), true, true, true);
        DebugSnapshotAssembler.Snapshot snapshot = collectSnapshot(state, player, session);
        sendSnapshot(player, session, snapshot);
        updateBaseline(state, player, session, snapshot);
    }

    private static void finishDisableOrRefresh(ServerPlayer player, Session session) {
        if (!session.hasAnyChannel()) {
            MinecraftServer server = player.level().getServer();
            DebugServerState state = state(server);
            state.sessions.remove(player.getUUID());
            clearRequestedPathTargetsIfUnused(state);
            discardStateIfEmpty(server, state);
            sendDisabledSnapshot(player);
            return;
        }
        session.forceRefresh();
        refreshPlayer(player, session);
    }

    private static void sendDisabledSnapshot(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player, new PacketGeometryDebugSnapshot(false, 0.0D, List.of()));
    }

    private static void updateBaseline(DebugServerState state, ServerPlayer player,
                                       Session session, DebugSnapshotAssembler.Snapshot snapshot) {
        session.lastPosition = player.position();
        session.lastDimension = player.level().dimension();
        session.lastDirtyVersion = state.dirtyVersion;
        session.lastSignature = snapshot.signature();
    }

    private static Session session(ServerPlayer player) {
        DebugServerState state = getState(player.level().getServer());
        return state != null ? state.sessions.get(player.getUUID()) : null;
    }

    private static DebugServerState state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new DebugServerState());
    }

    private static DebugServerState getState(MinecraftServer server) {
        return SERVERS.get(server);
    }

    private static void markDirty(DebugServerState state) {
        state.dirtyVersion++;
    }

    private static void discardStateIfEmpty(MinecraftServer server, DebugServerState state) {
        if (state.isEmpty()) SERVERS.remove(server, state);
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

    private static final class DebugServerState {
        private final Map<UUID, Session> sessions = new HashMap<>();
        private final PathDebugStateStore pathState = new PathDebugStateStore();
        private final DebugSourceStore sources = new DebugSourceStore(IDLE_CHECK_INTERVAL_TICKS);
        private final Map<ServerLevel, DebugLevelObservationCache> observations = new IdentityHashMap<>();
        private long dirtyVersion;

        private boolean isEmpty() {
            return sessions.isEmpty() && pathState.isEmpty() && sources.isEmpty();
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

        private int channelMask() {
            int mask = 0;
            if (areaEnabled) mask |= DebugSnapshotAssembler.channelBit(DebugRenderChannel.AREA);
            if (schematicEnabled) mask |= DebugSnapshotAssembler.channelBit(DebugRenderChannel.SCHEMATIC);
            if (geometryEnabled) mask |= DebugSnapshotAssembler.channelBit(DebugRenderChannel.GEOMETRY);
            if (interactionEnabled) mask |= DebugSnapshotAssembler.channelBit(DebugRenderChannel.INTERACTION);
            if (pathfindingEnabled) mask |= DebugSnapshotAssembler.channelBit(DebugRenderChannel.PATHFINDING);
            return mask;
        }

        private void forceRefresh() {
            lastPosition = null;
            lastDimension = null;
            lastDirtyVersion = Long.MIN_VALUE;
            lastSignature = Long.MIN_VALUE;
        }
    }
}
