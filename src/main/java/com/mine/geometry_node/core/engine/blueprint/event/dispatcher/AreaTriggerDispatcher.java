package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.GlobalGraphStorage;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcess;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.blueprint.spatial.RotatedBoxEntityQuery;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.node.nodes.events.area.BaseAreaTriggerEvent;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaEnter;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaExit;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaStay;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
    private static final int DEBUG_COLOR = 0x66FF3333;
    private static final int VISUAL_RADIUS = 128;
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
        ScopeKey scope = ScopeKey.global(level.dimension().location());
        Set<StateKey> seenStates = new HashSet<>();

        GlobalGraphStorage storage = GlobalGraphStorage.get(level.getServer().overworld());
        for (String graphId : storage.getGraphs()) {
            tickGraph(level, null, graphId, GraphEngine.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, currentTick, seenStates);
        }

        pruneScope(scope, seenStates);
    }

    public static void tickEntity(ServerLevel level, Entity owner, EntityGraphAttachment attachment, long currentTick) {
        if (owner == null || owner.isRemoved() || attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        cleanupStaleStates(currentTick);

        ScopeKey scope = ScopeKey.entity(level.dimension().location(), owner.getUUID());
        Set<StateKey> seenStates = new HashSet<>();

        for (String graphId : attachment.getBoundGraphs()) {
            tickGraph(level, owner, graphId, GraphEngine.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, scope, currentTick, seenStates);
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
                                  long currentTick,
                                  Set<StateKey> seenStates) {
        if (index == null) return;

        Map<AreaConfigKey, AreaGroup> groups = new LinkedHashMap<>();
        collectNodes(index, OnAreaEnter.TYPE_ID, AreaPhase.ENTER, currentTick, groups);
        collectNodes(index, OnAreaStay.TYPE_ID, AreaPhase.STAY, currentTick, groups);
        collectNodes(index, OnAreaExit.TYPE_ID, AreaPhase.EXIT, currentTick, groups);

        for (AreaGroup group : groups.values()) {
            StateKey stateKey = new StateKey(scope, graphId, group.configKey);
            seenStates.add(stateKey);

            AreaState state = STATES.computeIfAbsent(stateKey, ignored -> new AreaState());
            state.lastSeenTick = currentTick;
            if (!group.scheduled) continue;

            AreaQueryResult result = findEntities(level, group.config);
            Set<UUID> previous = state.inside;
            Set<UUID> current = result.entitiesById.keySet();

            dispatchPhase(level, target, graphId, index, group, AreaPhase.ENTER,
                    difference(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.STAY,
                    intersection(current, previous), result, processFinder, mountAction);
            dispatchPhase(level, target, graphId, index, group, AreaPhase.EXIT,
                    difference(previous, current), result, processFinder, mountAction);

            state.inside = new LinkedHashSet<>(current);
            if (group.debug) {
                broadcastDebugBox(level, group.config, Math.max(1, group.config.interval));
            }
        }
    }

    private static void collectNodes(RuntimeGraphIndex index,
                                     String eventType,
                                     AreaPhase phase,
                                     long currentTick,
                                     Map<AreaConfigKey, AreaGroup> groups) {
        for (int nodeId : index.findNodesByType(eventType)) {
            AreaConfig config = readConfig(index, nodeId);
            if (!config.enabled) continue;

            AreaGroup group = groups.computeIfAbsent(config.key(), key -> new AreaGroup(key, config));
            group.nodes.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(nodeId);
            group.debug = group.debug || config.debug;
            group.scheduled = group.scheduled || shouldTick(currentTick, config.interval, config.offset);
        }
    }

    private static AreaConfig readConfig(RuntimeGraphIndex index, int nodeId) {
        Vec3 center = readVec3(index.getNodeStaticInput(nodeId, StandardPorts.CENTER.getId()), Vec3.ZERO);
        Vec3 size = RotatedBoxEntityQuery.sanitizeSize(readVec3(index.getNodeStaticInput(nodeId, StandardPorts.SIZE_3.getId()), new Vec3(1, 1, 1)));
        Vec3 rotation = readVec3(index.getNodeStaticInput(nodeId, StandardPorts.ROTATION.getId()), Vec3.ZERO);
        int interval = Math.max(1, index.getNodeStaticInput(nodeId, StandardPorts.INTERVAL.getId(), Integer.class, 1));
        int offset = Math.floorMod(index.getNodeStaticInput(nodeId, StandardPorts.OFFSET.getId(), Integer.class, 0), interval);
        boolean enabled = readBoolean(index.getNodeStaticInput(nodeId, BaseAreaTriggerEvent.ENABLED_PORT), true);
        boolean debug = readBoolean(index.getNodeStaticInput(nodeId, StandardPorts.DEBUG.getId()), false);
        return new AreaConfig(center, size, rotation, interval, offset, enabled, debug);
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
            Entity entity = result.entitiesById.get(entityId);
            if (entity == null) {
                entity = level.getEntity(entityId);
            }
            if (entity == null || entity.isRemoved()) continue;

            Map<String, Object> baseData = GraphEventData.of(
                    StandardPorts.ENTITY.getId(), entity,
                    StandardPorts.TRIGGER_ENTITY.getId(), entity,
                    StandardPorts.TARGET_ENTITY.getId(), entity,
                    StandardPorts.XYZ.getId(), entity.position(),
                    StandardPorts.CENTER.getId(), group.config.center,
                    StandardPorts.SIZE_3.getId(), group.config.size,
                    StandardPorts.ROTATION.getId(), group.config.rotation,
                    StandardPorts.TYPE.getId(), phase.payloadName,
                    BaseAreaTriggerEvent.INSIDE_COUNT_PORT, insideCount
            );

            for (int nodeId : nodes) {
                Map<String, Object> eventData = new LinkedHashMap<>(baseData);
                eventData.put(BaseAreaTriggerEvent.TRIGGER_ID_PORT, graphId + ":" + index.getIdToString(nodeId));
                GraphEngine.executeEventNode(level, target, graphId, index, nodeId, eventData, processFinder, mountAction);
            }
        }
    }

    private static AreaQueryResult findEntities(ServerLevel level, AreaConfig config) {
        Map<UUID, Entity> hitEntities = new LinkedHashMap<>();
        for (Entity entity : RotatedBoxEntityQuery.find(level, config.center, config.size, config.rotation, e -> !e.isSpectator())) {
            hitEntities.put(entity.getUUID(), entity);
        }

        return new AreaQueryResult(hitEntities);
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

    private static boolean readBoolean(@Nullable Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String string) {
            if ("true".equalsIgnoreCase(string)) return true;
            if ("false".equalsIgnoreCase(string)) return false;
        }
        return fallback;
    }

    private static void broadcastDebugBox(ServerLevel level, AreaConfig config, int durationTicks) {
        CompoundTag extraData = new CompoundTag();
        extraData.putDouble("startX", config.center.x);
        extraData.putDouble("startY", config.center.y);
        extraData.putDouble("startZ", config.center.z);
        extraData.putDouble("sizeX", config.size.x);
        extraData.putDouble("sizeY", config.size.y);
        extraData.putDouble("sizeZ", config.size.z);
        extraData.putDouble("rotX", config.rotation.x);
        extraData.putDouble("rotY", config.rotation.y);
        extraData.putDouble("rotZ", config.rotation.z);

        GraphEngineServices.INSTANCE.visualSink().broadcast(new GraphEngineServices.VisualEffect(
                level,
                "debug_box",
                DEBUG_COLOR,
                durationTicks,
                Map.of(),
                Map.of(),
                extraData,
                config.center,
                VISUAL_RADIUS
        ));
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
    }

    private record AreaQueryResult(Map<UUID, Entity> entitiesById) {
    }

    private record ScopeKey(String kind, ResourceLocation dimension, @Nullable UUID ownerId) {
        static ScopeKey global(ResourceLocation dimension) {
            return new ScopeKey("level", dimension, null);
        }

        static ScopeKey entity(ResourceLocation dimension, UUID ownerId) {
            return new ScopeKey("entity", dimension, ownerId);
        }
    }

    private record StateKey(ScopeKey scope, String graphId, AreaConfigKey configKey) {
    }

    private record AreaConfigKey(double centerX, double centerY, double centerZ,
                                 double sizeX, double sizeY, double sizeZ,
                                 double rotationX, double rotationY, double rotationZ,
                                 int interval, int offset) {
    }

    private record AreaConfig(Vec3 center,
                              Vec3 size,
                              Vec3 rotation,
                              int interval,
                              int offset,
                              boolean enabled,
                              boolean debug) {
        AreaConfigKey key() {
            return new AreaConfigKey(
                    center.x, center.y, center.z,
                    size.x, size.y, size.z,
                    rotation.x, rotation.y, rotation.z,
                    interval, offset
            );
        }
    }

    private static final class AreaGroup {
        private final AreaConfigKey configKey;
        private final AreaConfig config;
        private final EnumMap<AreaPhase, List<Integer>> nodes = new EnumMap<>(AreaPhase.class);
        private boolean scheduled;
        private boolean debug;

        private AreaGroup(AreaConfigKey configKey, AreaConfig config) {
            this.configKey = configKey;
            this.config = config;
            this.debug = config.debug;
        }
    }

    private static final class AreaState {
        private Set<UUID> inside = Set.of();
        private long lastSeenTick;
    }
}
