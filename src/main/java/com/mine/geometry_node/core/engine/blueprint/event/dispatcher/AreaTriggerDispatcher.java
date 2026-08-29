package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaAnchor;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaShape;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaTargetType;
import com.mine.geometry_node.core.node.nodes.events.area.AreaTriggerEvent;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AreaTriggerDispatcher {
    private static final int STALE_STATE_TICKS = 20 * 60;
    private static final int STALE_CLEANUP_INTERVAL = 20 * 10;
    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();
    private final Map<BlueprintPlan, List<CompiledAreaNode>> configCache = Collections.synchronizedMap(new WeakHashMap<>());

    public AreaTriggerDispatcher() {
    }

    public void tickLevel(ServerLevel level) {
        ServerState state = servers.computeIfAbsent(level.getServer(), ignored -> new ServerState());
        long currentTick = level.getGameTime();
        cleanupStaleStates(state, currentTick);

        LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
        ScopeKey scope = ScopeKey.global(level.dimension().identifier());
        Set<StateKey> seenStates = new HashSet<>();
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();

        for (String graphId : BlueprintRuntime.INSTANCE.getGlobalGraphsForEvent(level, AreaTriggerEvent.TYPE_ID)) {
            String sourceKey = DebugRendererSessionManager.levelSourceKey(level, graphId);
            tickGraph(state, level, null, graphId, BlueprintRuntime.INSTANCE.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, sourceKey, currentTick, seenStates, queryCache);
        }

        pruneScope(state, scope, seenStates);
    }

    public void tickEntity(ServerLevel level, Entity owner, EntityGraphAttachment attachment, long currentTick) {
        if (owner == null || owner.isRemoved() || attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        ServerState state = servers.computeIfAbsent(level.getServer(), ignored -> new ServerState());
        cleanupStaleStates(state, currentTick);

        ScopeKey scope = ScopeKey.entity(level.dimension().identifier(), owner.getUUID());
        Set<StateKey> seenStates = new HashSet<>();
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();

        for (String graphId : BlueprintRuntime.INSTANCE.getEntityGraphsForEvent(owner, AreaTriggerEvent.TYPE_ID)) {
            String sourceKey = DebugRendererSessionManager.entitySourceKey(level, owner, graphId);
            tickGraph(state, level, owner, graphId, BlueprintRuntime.INSTANCE.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, sourceKey, currentTick, seenStates, queryCache);
        }

        pruneScope(state, scope, seenStates);
    }

    private void tickGraph(ServerState serverState,
                                  ServerLevel level,
                                  @Nullable Entity target,
                                  String graphId,
                                  @Nullable BlueprintPlan index,
                                  Function<String, BlueprintProcess> processFinder,
                                  Consumer<BlueprintProcess> mountAction,
                                  ScopeKey scope,
                                  String sourceKey,
                                  long currentTick,
                                  Set<StateKey> seenStates,
                                  Map<QueryCacheKey, AreaQueryResult> queryCache) {
        if (index == null) {
            DebugRendererSessionManager.removeSourceShapes(level, sourceKey);
            return;
        }

        Map<AreaConfigKey, AreaGroup> groups = new LinkedHashMap<>();
        collectNodes(index, currentTick, groups);
        List<DebugRenderShape> debugShapes = DebugRendererSessionManager.hasAreaSessions()
                ? new ArrayList<>(groups.size())
                : null;

        for (AreaGroup group : groups.values()) {
            ResolvedArea resolved = null;
            if (debugShapes != null) {
                resolved = group.config.resolve(target);
                debugShapes.add(toDebugShape(sourceKey, graphId, group, resolved));
            }
            StateKey stateKey = new StateKey(scope, graphId, group.configKey);
            seenStates.add(stateKey);

            AreaState state = serverState.states.computeIfAbsent(stateKey, ignored -> new AreaState());
            state.lastSeenTick = currentTick;
            if (!group.scheduled) continue;

            if (resolved == null) {
                resolved = group.config.resolve(target);
            }
            AreaQueryResult result = findEntities(level, resolved, group.config.targetType, queryCache);
            Set<UUID> previous = state.inside;
            Set<UUID> current = result.hitsById.keySet();

            dispatchPhase(level, target, graphId, index, group, AreaPhase.ENTER,
                    difference(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.STAY,
                    intersection(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.EXIT,
                    difference(previous, current), result, processFinder, mountAction);

            state.inside = new LinkedHashSet<>(current);
        }
        if (debugShapes != null) {
            DebugRendererSessionManager.replaceSourceShapes(level, sourceKey, debugShapes, currentTick);
        }
    }

    private void collectNodes(BlueprintPlan index,
                                     long currentTick,
                                     Map<AreaConfigKey, AreaGroup> groups) {
        for (CompiledAreaNode node : getCompiledNodes(index)) {
            AreaConfig config = node.config;
            AreaPhase phase = node.phase;
            AreaGroup group = groups.computeIfAbsent(config.key(), key -> new AreaGroup(key, config));
            group.nodes.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(node.nodeId);
            if (group.debugNodeId == null) {
                group.debugNodeId = node.nodeIdString;
            }
            group.scheduled = group.scheduled || shouldTick(currentTick, config.interval, config.offset);
        }
    }

    private List<CompiledAreaNode> getCompiledNodes(BlueprintPlan index) {
        synchronized (configCache) {
            return configCache.computeIfAbsent(index, AreaTriggerDispatcher::compileNodes);
        }
    }

    private static List<CompiledAreaNode> compileNodes(BlueprintPlan index) {
        List<Integer> nodeIds = index.findNodesByType(AreaTriggerEvent.TYPE_ID);
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        List<CompiledAreaNode> nodes = new ArrayList<>(nodeIds.size());
        for (int nodeId : nodeIds) {
            nodes.add(new CompiledAreaNode(
                    nodeId,
                    index.getIdToString(nodeId),
                    readConfig(index, nodeId),
                    readPhase(index, nodeId)
            ));
        }
        return List.copyOf(nodes);
    }

    private static AreaPhase readPhase(BlueprintPlan index, int nodeId) {
        String rawPhase = index.getNodeStaticInput(nodeId, AreaTriggerEvent.PHASE_PORT, String.class, AreaTriggerEvent.PHASE_ENTER);
        return AreaPhase.fromPayloadName(rawPhase);
    }

    private static DebugRenderShape toDebugShape(String sourceKey, String graphId, AreaGroup group, ResolvedArea resolved) {
        String localId = group.debugNodeId != null ? group.debugNodeId : group.configKey.toString();
        return new DebugRenderShape(sourceKey + ":" + localId, graphId,
                group.config.shape.id(),
                resolved.center, group.config.size, group.config.rotation,
                DebugRenderChannel.AREA.color());
    }

    private static AreaConfig readConfig(BlueprintPlan index, int nodeId) {
        AreaAnchor anchor = AreaAnchor.fromId(index.getNodeStaticInput(nodeId, AreaTriggerEvent.ANCHOR_PORT, String.class, AreaAnchor.WORLD.id()));
        AreaShape shape = AreaShape.fromId(index.getNodeStaticInput(nodeId, AreaTriggerEvent.SHAPE_PORT, String.class, AreaShape.BOX.id()));
        AreaTargetType targetType = AreaTargetType.fromId(index.getNodeStaticInput(nodeId, AreaTriggerEvent.TARGET_PORT, String.class, AreaTargetType.ALL.id()));
        Vec3 center = readVec3(index.getNodeStaticInput(nodeId, StandardPorts.CENTER.getId()), Vec3.ZERO);
        Vec3 size = readSize(index, nodeId, shape);
        Vec3 rotation = shape == AreaShape.SPHERE
                ? Vec3.ZERO
                : readVec3(index.getNodeStaticInput(nodeId, StandardPorts.ROTATION.getId()), Vec3.ZERO);
        int interval = Math.max(1, index.getNodeStaticInput(nodeId, AreaTriggerEvent.INTERVAL_TICK_PORT, Integer.class, 1));
        int offset = Math.floorMod(index.getNodeStaticInput(nodeId, AreaTriggerEvent.OFFSET_TICK_PORT, Integer.class, 0), interval);
        return new AreaConfig(anchor, shape, targetType, center, size, rotation, interval, offset);
    }

    private static Vec3 readSize(BlueprintPlan index, int nodeId, AreaShape shape) {
        return switch (shape) {
            case SPHERE -> {
                double radius = readPositiveDouble(index.getNodeStaticInput(nodeId, StandardPorts.RADIUS.getId()), AreaTriggerEvent.DEFAULT_RADIUS);
                double diameter = radius * 2.0D;
                yield new Vec3(diameter, diameter, diameter);
            }
            case CYLINDER -> {
                double radius = readPositiveDouble(index.getNodeStaticInput(nodeId, StandardPorts.RADIUS.getId()), AreaTriggerEvent.DEFAULT_RADIUS);
                double height = readPositiveDouble(index.getNodeStaticInput(nodeId, AreaTriggerEvent.HEIGHT_PORT), AreaTriggerEvent.DEFAULT_HEIGHT);
                double diameter = radius * 2.0D;
                yield new Vec3(diameter, height, diameter);
            }
            case BOX -> AreaEntityQuery.sanitizeSize(readVec3(index.getNodeStaticInput(nodeId, StandardPorts.SIZE_3.getId()), new Vec3(1, 1, 1)));
        };
    }

    private static boolean shouldTick(long currentTick, int interval, int offset) {
        return interval == 1 || Math.floorMod(currentTick, interval) == offset;
    }

    private void dispatchPhase(ServerLevel level,
                                      @Nullable Entity target,
                                      String graphId,
                                      BlueprintPlan index,
                                      AreaGroup group,
                                      AreaPhase phase,
                                      Set<UUID> entityIds,
                                      AreaQueryResult result,
                                      Function<String, BlueprintProcess> processFinder,
                                      Consumer<BlueprintProcess> mountAction) {
        List<Integer> nodes = group.nodes.get(phase);
        if (nodes == null || nodes.isEmpty() || entityIds.isEmpty()) return;

        int insideCount = result.hitsById.size();
        for (UUID entityId : entityIds) {
            AreaEntityQuery.Hit hit = result.hitsById.get(entityId);
            Entity triggerEntity = hit != null ? hit.entity() : null;
            if (triggerEntity == null) {
                triggerEntity = level.getEntity(entityId);
            }
            if (triggerEntity == null || triggerEntity.isRemoved()) continue;

            Entity ownerEntity = target != null ? target : triggerEntity;

            Map<String, Object> baseData = GraphEventData.of(
                    StandardPorts.ENTITY.getId(), ownerEntity,
                    StandardPorts.TRIGGER_ENTITY.getId(), triggerEntity,
                    StandardPorts.HIT_POS.getId(), hit != null ? hit.hitPos() : triggerEntity.position(),
                    StandardPorts.VECTOR.getId(), hit != null ? hit.velocity() : triggerEntity.getDeltaMovement(),
                    StandardPorts.CENTER.getId(), result.area.center,
                    StandardPorts.SIZE_3.getId(), group.config.size,
                    StandardPorts.RADIUS.getId(), (float) group.config.radius(),
                    AreaTriggerEvent.HEIGHT_PORT, (float) group.config.height(),
                    StandardPorts.ROTATION.getId(), group.config.rotation,
                    StandardPorts.TYPE.getId(), phase.payloadName,
                    AreaTriggerEvent.INSIDE_COUNT_PORT, insideCount,
                    AreaTriggerEvent.TARGET_PORT, group.config.targetType.id()
            );

            for (int nodeId : nodes) {
                Map<String, Object> eventData = new LinkedHashMap<>(baseData);
                eventData.put(AreaTriggerEvent.TRIGGER_ID_PORT, graphId + ":" + index.getIdToString(nodeId));
                BlueprintRuntime.INSTANCE.executeEventNode(level, target, graphId, index, nodeId, eventData, processFinder, mountAction);
            }
        }
    }

    private static AreaQueryResult findEntities(ServerLevel level,
                                                ResolvedArea area,
                                                AreaTargetType targetType,
                                                Map<QueryCacheKey, AreaQueryResult> queryCache) {
        QueryCacheKey key = QueryCacheKey.of(area, targetType);
        return queryCache.computeIfAbsent(key, ignored -> queryEntities(level, area, targetType));
    }

    private static AreaQueryResult queryEntities(ServerLevel level, ResolvedArea area, AreaTargetType targetType) {
        Map<UUID, AreaEntityQuery.Hit> hits = new LinkedHashMap<>();
        for (AreaEntityQuery.Hit hit : AreaEntityQuery.findHits(level, area.shape, area.center, area.size, area.rotation, targetType, e -> !e.isSpectator())) {
            hits.put(hit.entity().getUUID(), hit);
        }

        return new AreaQueryResult(area, hits);
    }

    private static Set<UUID> difference(Set<UUID> left, Set<UUID> right) {
        Set<UUID> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static Set<UUID> intersection(Set<UUID> left, Set<UUID> right) {
        Set<UUID> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static Vec3 readVec3(@Nullable Object raw, Vec3 fallback) {
        if (raw instanceof Vec3 vec) {
            return vec;
        }
        if (raw instanceof List<?> list && list.size() >= 3
                && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y
                && list.get(2) instanceof Number z) {
            return new Vec3(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        if (raw instanceof Map<?, ?> map) {
            Double x = readDouble(map.get("x"));
            Double y = readDouble(map.get("y"));
            Double z = readDouble(map.get("z"));
            if (x != null && y != null && z != null) {
                return new Vec3(x, y, z);
            }
        }
        return fallback;
    }

    @Nullable
    private static Double readDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static double readPositiveDouble(@Nullable Object raw, double fallback) {
        Double value = readDouble(raw);
        if (value == null || !Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.001D, Math.abs(value));
    }

    private static void pruneScope(ServerState state, ScopeKey scope, Set<StateKey> seenStates) {
        state.states.entrySet().removeIf(entry -> entry.getKey().scope.equals(scope) && !seenStates.contains(entry.getKey()));
    }

    private static void cleanupStaleStates(ServerState state, long currentTick) {
        if (currentTick == state.lastCleanupTick) return;
        if (Math.floorMod(currentTick, STALE_CLEANUP_INTERVAL) != 0) return;
        state.lastCleanupTick = currentTick;
        state.states.entrySet().removeIf(entry -> currentTick - entry.getValue().lastSeenTick > STALE_STATE_TICKS);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private enum AreaPhase {
        ENTER("enter"),
        STAY("stay"),
        EXIT("exit");

        private final String payloadName;

        AreaPhase(String payloadName) {
            this.payloadName = payloadName;
        }

        private static AreaPhase fromPayloadName(@Nullable String payloadName) {
            if (payloadName != null) {
                for (AreaPhase phase : values()) {
                    if (phase.payloadName.equals(payloadName)) {
                        return phase;
                    }
                }
            }
            return ENTER;
        }
    }

    private record AreaQueryResult(ResolvedArea area, Map<UUID, AreaEntityQuery.Hit> hitsById) {
    }

    private record CompiledAreaNode(int nodeId, String nodeIdString, AreaConfig config, AreaPhase phase) {
    }

    private record ScopeKey(String kind, Identifier dimension, @Nullable UUID ownerId) {
        static ScopeKey global(Identifier dimension) {
            return new ScopeKey("level", dimension, null);
        }

        static ScopeKey entity(Identifier dimension, UUID ownerId) {
            return new ScopeKey("entity", dimension, ownerId);
        }
    }

    private record StateKey(ScopeKey scope, String graphId, AreaConfigKey configKey) {
    }

    private record AreaConfigKey(AreaAnchor anchor,
                                 AreaShape shape,
                                 AreaTargetType targetType,
                                 double centerX, double centerY, double centerZ,
                                 double sizeX, double sizeY, double sizeZ,
                                 double rotationX, double rotationY, double rotationZ,
                                 int interval, int offset) {
    }

    private record AreaConfig(AreaAnchor anchor,
                              AreaShape shape,
                              AreaTargetType targetType,
                              Vec3 center,
                              Vec3 size,
                              Vec3 rotation,
                              int interval,
                              int offset) {
        AreaConfigKey key() {
            return new AreaConfigKey(
                    anchor,
                    shape,
                    targetType,
                    center.x, center.y, center.z,
                    size.x, size.y, size.z,
                    rotation.x, rotation.y, rotation.z,
                    interval, offset
            );
        }

        ResolvedArea resolve(@Nullable Entity owner) {
            Vec3 resolvedCenter = anchor == AreaAnchor.OWNER && owner != null && !owner.isRemoved()
                    ? owner.position().add(center)
                    : center;
            return new ResolvedArea(shape, resolvedCenter, size, rotation);
        }

        double radius() {
            return switch (shape) {
                case SPHERE -> Math.max(size.x, Math.max(size.y, size.z)) * 0.5D;
                case CYLINDER -> Math.max(size.x, size.z) * 0.5D;
                case BOX -> 0.0D;
            };
        }

        double height() {
            return shape == AreaShape.CYLINDER ? size.y : 0.0D;
        }
    }

    private record ResolvedArea(AreaShape shape, Vec3 center, Vec3 size, Vec3 rotation) {
    }

    private record QueryCacheKey(AreaShape shape,
                                 AreaTargetType targetType,
                                 double centerX, double centerY, double centerZ,
                                 double sizeX, double sizeY, double sizeZ,
                                 double rotationX, double rotationY, double rotationZ) {
        static QueryCacheKey of(ResolvedArea area, AreaTargetType targetType) {
            return new QueryCacheKey(
                    area.shape,
                    targetType,
                    area.center.x, area.center.y, area.center.z,
                    area.size.x, area.size.y, area.size.z,
                    area.rotation.x, area.rotation.y, area.rotation.z
            );
        }
    }

    private static final class AreaGroup {
        private final AreaConfigKey configKey;
        private final AreaConfig config;
        private final EnumMap<AreaPhase, List<Integer>> nodes = new EnumMap<>(AreaPhase.class);
        private boolean scheduled;
        @Nullable
        private String debugNodeId;

        private AreaGroup(AreaConfigKey configKey, AreaConfig config) {
            this.configKey = configKey;
            this.config = config;
        }
    }

    private static final class AreaState {
        private Set<UUID> inside = Set.of();
        private long lastSeenTick;
    }

    private static final class ServerState {
        private final Map<StateKey, AreaState> states = new HashMap<>();
        private long lastCleanupTick = Long.MIN_VALUE;
    }
}
