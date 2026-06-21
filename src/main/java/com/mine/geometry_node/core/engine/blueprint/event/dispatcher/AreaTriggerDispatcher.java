package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.debug.AreaDebugBox;
import com.mine.geometry_node.core.engine.blueprint.debug.AreaDebugSessionManager;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcess;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaAnchor;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaShape;
import com.mine.geometry_node.core.node.nodes.events.area.AreaTriggerEvent;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AreaTriggerDispatcher {
    private static final int STALE_STATE_TICKS = 20 * 60;
    private static final int STALE_CLEANUP_INTERVAL = 20 * 10;
    private static final Map<StateKey, AreaState> STATES = new HashMap<>();
    private static long lastCleanupTick = Long.MIN_VALUE;

    private AreaTriggerDispatcher() {
    }

    public static void tickLevel(ServerLevel level) {
        long currentTick = level.getGameTime();
        cleanupStaleStates(currentTick);

        LevelGraphAttachment attachment = LevelGraphAttachment.get(level);
        ScopeKey scope = ScopeKey.global(level.dimension().identifier());
        Set<StateKey> seenStates = new HashSet<>();

        for (String graphId : GraphEngine.getGlobalGraphsForEvent(level, AreaTriggerEvent.TYPE_ID)) {
            String sourceKey = AreaDebugSessionManager.levelSourceKey(level, graphId);
            tickGraph(level, null, graphId, GraphEngine.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, sourceKey, currentTick, seenStates);
        }

        pruneScope(scope, seenStates);
    }

    public static void tickEntity(ServerLevel level, Entity owner, EntityGraphAttachment attachment, long currentTick) {
        if (owner == null || owner.isRemoved() || attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        cleanupStaleStates(currentTick);

        ScopeKey scope = ScopeKey.entity(level.dimension().identifier(), owner.getUUID());
        Set<StateKey> seenStates = new HashSet<>();

        for (String graphId : GraphEngine.getEntityGraphsForEvent(owner, AreaTriggerEvent.TYPE_ID)) {
            String sourceKey = AreaDebugSessionManager.entitySourceKey(level, owner, graphId);
            tickGraph(level, owner, graphId, GraphEngine.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, sourceKey, currentTick, seenStates);
        }

        pruneScope(scope, seenStates);
    }

    private static void tickGraph(ServerLevel level,
                                  @Nullable Entity target,
                                  String graphId,
                                  @Nullable RuntimeGraphIndex index,
                                  Function<String, GraphProcess> processFinder,
                                  Consumer<GraphProcess> mountAction,
                                  ScopeKey scope,
                                  String sourceKey,
                                  long currentTick,
                                  Set<StateKey> seenStates) {
        if (index == null) {
            AreaDebugSessionManager.removeSourceBoxes(level, sourceKey);
            return;
        }

        Map<AreaConfigKey, AreaGroup> groups = new LinkedHashMap<>();
        collectNodes(index, currentTick, groups);
        List<AreaDebugBox> debugBoxes = AreaDebugSessionManager.hasSessions()
                ? new ArrayList<>(groups.size())
                : null;

        for (AreaGroup group : groups.values()) {
            ResolvedArea resolved = null;
            if (debugBoxes != null) {
                resolved = group.config.resolve(target);
                debugBoxes.add(toDebugBox(sourceKey, graphId, group, resolved));
            }
            StateKey stateKey = new StateKey(scope, graphId, group.configKey);
            seenStates.add(stateKey);

            AreaState state = STATES.computeIfAbsent(stateKey, ignored -> new AreaState());
            state.lastSeenTick = currentTick;
            if (!group.scheduled) continue;

            if (resolved == null) {
                resolved = group.config.resolve(target);
            }
            AreaQueryResult result = findEntities(level, resolved);
            Set<UUID> previous = state.inside;
            Set<UUID> current = result.entitiesById.keySet();

            dispatchPhase(level, target, graphId, index, group, AreaPhase.ENTER,
                    difference(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.STAY,
                    intersection(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.EXIT,
                    difference(previous, current), result, processFinder, mountAction);

            state.inside = new LinkedHashSet<>(current);
        }
        if (debugBoxes != null) {
            AreaDebugSessionManager.replaceSourceBoxes(level, sourceKey, debugBoxes, currentTick);
        }
    }

    private static void collectNodes(RuntimeGraphIndex index,
                                     long currentTick,
                                     Map<AreaConfigKey, AreaGroup> groups) {
        for (int nodeId : index.findNodesByType(AreaTriggerEvent.TYPE_ID)) {
            AreaConfig config = readConfig(index, nodeId);

            AreaPhase phase = readPhase(index, nodeId);
            AreaGroup group = groups.computeIfAbsent(config.key(), key -> new AreaGroup(key, config));
            group.nodes.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(nodeId);
            if (group.debugNodeId == null) {
                group.debugNodeId = index.getIdToString(nodeId);
            }
            group.scheduled = group.scheduled || shouldTick(currentTick, config.interval, config.offset);
        }
    }

    private static AreaPhase readPhase(RuntimeGraphIndex index, int nodeId) {
        String rawPhase = index.getNodeStaticInput(nodeId, AreaTriggerEvent.PHASE_PORT, String.class, AreaTriggerEvent.PHASE_ENTER);
        return AreaPhase.fromPayloadName(rawPhase);
    }

    private static AreaDebugBox toDebugBox(String sourceKey, String graphId, AreaGroup group, ResolvedArea resolved) {
        String localId = group.debugNodeId != null ? group.debugNodeId : group.configKey.toString();
        return new AreaDebugBox(sourceKey + ":" + localId, graphId,
                group.config.shape.id(),
                resolved.center, group.config.size, group.config.rotation);
    }

    private static AreaConfig readConfig(RuntimeGraphIndex index, int nodeId) {
        AreaAnchor anchor = AreaAnchor.fromId(index.getNodeStaticInput(nodeId, AreaTriggerEvent.ANCHOR_PORT, String.class, AreaAnchor.WORLD.id()));
        AreaShape shape = AreaShape.fromId(index.getNodeStaticInput(nodeId, AreaTriggerEvent.SHAPE_PORT, String.class, AreaShape.BOX.id()));
        Vec3 center = readVec3(index.getNodeStaticInput(nodeId, StandardPorts.CENTER.getId()), Vec3.ZERO);
        Vec3 size = readSize(index, nodeId, shape);
        Vec3 rotation = shape == AreaShape.SPHERE
                ? Vec3.ZERO
                : readVec3(index.getNodeStaticInput(nodeId, StandardPorts.ROTATION.getId()), Vec3.ZERO);
        int interval = Math.max(1, index.getNodeStaticInput(nodeId, StandardPorts.INTERVAL.getId(), Integer.class, 1));
        int offset = Math.floorMod(index.getNodeStaticInput(nodeId, StandardPorts.OFFSET.getId(), Integer.class, 0), interval);
        return new AreaConfig(anchor, shape, center, size, rotation, interval, offset);
    }

    private static Vec3 readSize(RuntimeGraphIndex index, int nodeId, AreaShape shape) {
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

    private static void dispatchPhase(ServerLevel level,
                                      @Nullable Entity target,
                                      String graphId,
                                      RuntimeGraphIndex index,
                                      AreaGroup group,
                                      AreaPhase phase,
                                      Set<UUID> entityIds,
                                      AreaQueryResult result,
                                      Function<String, GraphProcess> processFinder,
                                      Consumer<GraphProcess> mountAction) {
        List<Integer> nodes = group.nodes.get(phase);
        if (nodes == null || nodes.isEmpty() || entityIds.isEmpty()) return;

        int insideCount = result.entitiesById.size();
        for (UUID entityId : entityIds) {
            Entity triggerEntity = result.entitiesById.get(entityId);
            if (triggerEntity == null) {
                triggerEntity = level.getEntity(entityId);
            }
            if (triggerEntity == null || triggerEntity.isRemoved()) continue;

            Entity ownerEntity = target != null ? target : triggerEntity;

            Map<String, Object> baseData = GraphEventData.of(
                    StandardPorts.ENTITY.getId(), ownerEntity,
                    StandardPorts.TRIGGER_ENTITY.getId(), triggerEntity,
                    StandardPorts.TARGET_ENTITY.getId(), triggerEntity,
                    StandardPorts.XYZ.getId(), triggerEntity.position(),
                    StandardPorts.CENTER.getId(), result.area.center,
                    StandardPorts.SIZE_3.getId(), group.config.size,
                    StandardPorts.RADIUS.getId(), (float) group.config.radius(),
                    AreaTriggerEvent.HEIGHT_PORT, (float) group.config.height(),
                    StandardPorts.ROTATION.getId(), group.config.rotation,
                    StandardPorts.TYPE.getId(), phase.payloadName,
                    AreaTriggerEvent.INSIDE_COUNT_PORT, insideCount
            );

            for (int nodeId : nodes) {
                Map<String, Object> eventData = new LinkedHashMap<>(baseData);
                eventData.put(AreaTriggerEvent.TRIGGER_ID_PORT, graphId + ":" + index.getIdToString(nodeId));
                GraphEngine.executeEventNode(level, target, graphId, index, nodeId, eventData, processFinder, mountAction);
            }
        }
    }

    private static AreaQueryResult findEntities(ServerLevel level, ResolvedArea area) {
        Map<UUID, Entity> hitEntities = new LinkedHashMap<>();
        for (Entity entity : AreaEntityQuery.find(level, area.shape, area.center, area.size, area.rotation, e -> !e.isSpectator())) {
            hitEntities.put(entity.getUUID(), entity);
        }

        return new AreaQueryResult(area, hitEntities);
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

    private static void pruneScope(ScopeKey scope, Set<StateKey> seenStates) {
        STATES.entrySet().removeIf(entry -> entry.getKey().scope.equals(scope) && !seenStates.contains(entry.getKey()));
    }

    private static void cleanupStaleStates(long currentTick) {
        if (currentTick == lastCleanupTick) return;
        if (Math.floorMod(currentTick, STALE_CLEANUP_INTERVAL) != 0) return;
        lastCleanupTick = currentTick;
        STATES.entrySet().removeIf(entry -> currentTick - entry.getValue().lastSeenTick > STALE_STATE_TICKS);
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

    private record AreaQueryResult(ResolvedArea area, Map<UUID, Entity> entitiesById) {
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
                                 double centerX, double centerY, double centerZ,
                                 double sizeX, double sizeY, double sizeZ,
                                 double rotationX, double rotationY, double rotationZ,
                                 int interval, int offset) {
    }

    private record AreaConfig(AreaAnchor anchor,
                              AreaShape shape,
                              Vec3 center,
                              Vec3 size,
                              Vec3 rotation,
                              int interval,
                              int offset) {
        AreaConfigKey key() {
            return new AreaConfigKey(
                    anchor,
                    shape,
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
}
