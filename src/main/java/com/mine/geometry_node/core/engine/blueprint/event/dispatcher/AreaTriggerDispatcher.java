package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaTargetType;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceScope;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceSelector;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaEvent;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
import java.util.function.Predicate;

/** Polls live Area resources and dispatches listeners without creating Areas implicitly. */
public final class AreaTriggerDispatcher {
    private static final int STALE_STATE_TICKS = 20 * 60;
    private static final int STALE_CLEANUP_INTERVAL = 20 * 10;

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();
    private final Map<BlueprintPlan, List<CompiledListener>> configCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    public AreaTriggerDispatcher() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_area_listener_state",
                this::removeGraphResources);
    }

    public void tickLevel(ServerLevel hostLevel) {
        ServerState state = servers.computeIfAbsent(hostLevel.getServer(), ignored -> new ServerState());
        long currentTick = hostLevel.getGameTime();
        cleanupStaleStates(state, currentTick);

        LevelGraphAttachment attachment = LevelGraphAttachment.get(hostLevel);
        GraphResourceScope scope = new GraphResourceScope.LevelScope(hostLevel.dimension());
        Set<StateKey> seenStates = new HashSet<>();
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();
        for (String graphId : BlueprintRuntime.INSTANCE.getGlobalGraphsForEvent(hostLevel, OnAreaEvent.TYPE_ID)) {
            tickGraph(state, hostLevel, null, graphId, BlueprintRuntime.INSTANCE.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, stateResource(scope, graphId),
                    currentTick, seenStates, queryCache);
        }
        pruneScope(state, scope, seenStates);
    }

    public void tickEntity(ServerLevel hostLevel, Entity owner, EntityGraphAttachment attachment, long currentTick) {
        if (owner == null || owner.isRemoved() || attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        ServerState state = servers.computeIfAbsent(hostLevel.getServer(), ignored -> new ServerState());
        cleanupStaleStates(state, currentTick);

        GraphResourceScope scope = new GraphResourceScope.EntityScope(hostLevel.dimension(), owner.getUUID());
        Set<StateKey> seenStates = new HashSet<>();
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();
        for (String graphId : BlueprintRuntime.INSTANCE.getEntityGraphsForEvent(owner, OnAreaEvent.TYPE_ID)) {
            tickGraph(state, hostLevel, owner, graphId, BlueprintRuntime.INSTANCE.getGraphIndex(graphId),
                    attachment::getProcess, attachment::addProcess, stateResource(scope, graphId),
                    currentTick, seenStates, queryCache);
        }
        pruneScope(state, scope, seenStates);
    }

    private void tickGraph(ServerState serverState, ServerLevel hostLevel, @Nullable Entity owner,
                           String graphId, @Nullable BlueprintPlan plan,
                           Function<String, BlueprintProcess> processFinder,
                           Consumer<BlueprintProcess> mountAction,
                           GraphResourceId stateResource, long currentTick,
                           Set<StateKey> seenStates,
                           Map<QueryCacheKey, AreaQueryResult> queryCache) {
        if (plan == null) return;

        Map<ListenerKey, ListenerGroup> groups = new LinkedHashMap<>();
        for (CompiledListener listener : getCompiledListeners(plan)) {
            ListenerGroup group = groups.computeIfAbsent(listener.key(), ListenerGroup::new);
            group.nodes.computeIfAbsent(listener.phase(), ignored -> new ArrayList<>()).add(listener.nodeId());
        }

        for (ListenerGroup group : groups.values()) {
            StateKey stateKey = new StateKey(stateResource, plan, group.key);
            seenStates.add(stateKey);
            ListenerState listenerState = serverState.states.computeIfAbsent(stateKey,
                    ignored -> new ListenerState());
            listenerState.lastSeenTick = currentTick;
            if (!shouldTick(currentTick, group.key.interval(), group.key.offset())) continue;

            ServerLevel areaLevel = RegistryDataManager.resolveDimension(hostLevel.getServer(),
                    group.key.dimensionId());
            if (areaLevel == null || group.key.sourceId().isBlank()) {
                listenerState.reset();
                continue;
            }
            ResolvedAreaSource source = resolveSource(hostLevel.getServer(), areaLevel, group.key);
            if (source == null) {
                listenerState.reset();
                continue;
            }
            if (listenerState.areaGeneration != source.areaResource().generation()
                    || listenerState.forceFieldGeneration != source.forceFieldGeneration()) {
                listenerState.inside = Set.of();
                listenerState.areaGeneration = source.areaResource().generation();
                listenerState.forceFieldGeneration = source.forceFieldGeneration();
            }

            AreaQueryResult result = findEntities(areaLevel, source.areaResource(), source.area(),
                    group.key.targetType(), source.excludedEntityId(), queryCache);
            Set<UUID> previous = listenerState.inside;
            Set<UUID> current = result.hitsById().keySet();
            dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group, AreaPhase.ENTER,
                    difference(current, previous), source, result, processFinder, mountAction);
            dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group, AreaPhase.STAY,
                    intersection(current, previous), source, result, processFinder, mountAction);
            dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group, AreaPhase.EXIT,
                    difference(previous, current), source, result, processFinder, mountAction);
            listenerState.inside = new LinkedHashSet<>(current);
        }
    }

    private List<CompiledListener> getCompiledListeners(BlueprintPlan plan) {
        synchronized (configCache) {
            return configCache.computeIfAbsent(plan, AreaTriggerDispatcher::compileListeners);
        }
    }

    private static List<CompiledListener> compileListeners(BlueprintPlan plan) {
        List<Integer> nodeIds = plan.findNodesByType(OnAreaEvent.TYPE_ID);
        if (nodeIds.isEmpty()) return List.of();
        List<CompiledListener> listeners = new ArrayList<>(nodeIds.size());
        for (int nodeId : nodeIds) {
            String dimension = plan.getNodeStaticInput(nodeId, StandardPorts.DIMENSION.getId(), String.class,
                    RegistryDataManager.DEFAULT_DIMENSION);
            AreaSource source = AreaSource.fromId(plan.getNodeStaticInput(nodeId,
                    OnAreaEvent.SOURCE_PORT, String.class, OnAreaEvent.SOURCE_AREA));
            String sourceId = source == AreaSource.FORCE_FIELD
                    ? plan.getNodeStaticInput(nodeId, StandardPorts.FORCE_FIELD_ID.getId(), String.class, "")
                    : plan.getNodeStaticInput(nodeId, StandardPorts.AREA_ID.getId(), String.class, "");
            AreaTargetType target = AreaTargetType.fromId(plan.getNodeStaticInput(nodeId,
                    OnAreaEvent.TARGET_PORT, String.class, AreaTargetType.ALL.id()));
            int interval = Math.max(1, plan.getNodeStaticInput(nodeId,
                    OnAreaEvent.INTERVAL_TICK_PORT, Integer.class, 1));
            int offset = Math.floorMod(plan.getNodeStaticInput(nodeId,
                    OnAreaEvent.OFFSET_TICK_PORT, Integer.class, 0), interval);
            ListenerKey key = new ListenerKey(dimension == null ? "" : dimension.trim(), source,
                    sourceId == null ? "" : sourceId.trim(), target, interval, offset);
            AreaPhase phase = AreaPhase.fromId(plan.getNodeStaticInput(nodeId,
                    OnAreaEvent.PHASE_PORT, String.class, OnAreaEvent.PHASE_ENTER));
            listeners.add(new CompiledListener(nodeId, key, phase));
        }
        return List.copyOf(listeners);
    }

    private void dispatchPhase(ServerLevel hostLevel, ServerLevel areaLevel, @Nullable Entity owner,
                               String graphId, BlueprintPlan plan, ListenerGroup group, AreaPhase phase,
                               Set<UUID> entityIds, ResolvedAreaSource source, AreaQueryResult result,
                               Function<String, BlueprintProcess> processFinder,
                               Consumer<BlueprintProcess> mountAction) {
        List<Integer> nodes = group.nodes.get(phase);
        if (nodes == null || nodes.isEmpty() || entityIds.isEmpty()) return;

        AreaResource resource = result.resource();
        AreaResource.Resolved area = result.area();
        int insideCount = result.hitsById().size();
        double radius = switch (area.shape()) {
            case SPHERE -> Math.max(area.size().x, Math.max(area.size().y, area.size().z)) * 0.5D;
            case CYLINDER -> Math.max(area.size().x, area.size().z) * 0.5D;
            case BOX -> 0.0D;
        };
        double height = area.shape() == AreaShape.CYLINDER
                ? area.size().y : 0.0D;

        for (UUID entityId : entityIds) {
            AreaEntityQuery.Hit hit = result.hitsById().get(entityId);
            Entity trigger = hit != null ? hit.entity() : areaLevel.getEntity(entityId);
            if (trigger == null || trigger.isRemoved()) continue;
            Entity eventEntity = owner != null ? owner : trigger;
            Map<String, Object> eventData = EventPayload.of(
                    StandardPorts.ENTITY.getId(), eventEntity,
                    StandardPorts.TRIGGER_ENTITY.getId(), trigger,
                    StandardPorts.HIT_POS.getId(), hit != null ? hit.hitPos() : trigger.position(),
                    StandardPorts.VECTOR.getId(), hit != null ? hit.velocity() : trigger.getDeltaMovement(),
                    StandardPorts.TYPE.getId(), phase.id,
                    StandardPorts.SHAPE.getId(), area.shape().id(),
                    StandardPorts.CENTER.getId(), area.center(),
                    StandardPorts.SIZE_3.getId(), area.size(),
                    StandardPorts.RADIUS.getId(), (float) radius,
                    StandardPorts.HEIGHT.getId(), (float) height,
                    StandardPorts.ROTATION.getId(), area.rotation(),
                    StandardPorts.AREA_ID.getId(), resource.address().id(),
                    StandardPorts.FORCE_FIELD_ID.getId(), source.forceFieldId(),
                    OnAreaEvent.SOURCE_PORT, group.key.source().id,
                    StandardPorts.DIMENSION.getId(), resource.address().dimension().identifier().toString(),
                    OnAreaEvent.INSIDE_COUNT_PORT, insideCount,
                    OnAreaEvent.TARGET_PORT, group.key.targetType().id()
            ).values();
            for (int nodeId : nodes) {
                // The process keeps its host level; the selected dimension only controls Area lookup and querying.
                BlueprintRuntime.INSTANCE.executeEventNode(hostLevel, owner, graphId, plan, nodeId,
                        eventData, processFinder, mountAction);
            }
        }
    }

    private static AreaQueryResult findEntities(ServerLevel level, AreaResource resource,
                                                AreaResource.Resolved area, AreaTargetType target,
                                                @Nullable UUID excludedEntityId,
                                                Map<QueryCacheKey, AreaQueryResult> cache) {
        QueryCacheKey key = QueryCacheKey.of(resource, area, target, excludedEntityId);
        return cache.computeIfAbsent(key, ignored -> {
            Map<UUID, AreaEntityQuery.Hit> hits = new LinkedHashMap<>();
            for (AreaEntityQuery.Hit hit : AreaEntityQuery.findHits(level, area.shape(), area.center(),
                    area.size(), area.rotation(), target, entity -> !entity.isSpectator()
                            && (excludedEntityId == null || !excludedEntityId.equals(entity.getUUID())))) {
                hits.put(hit.entity().getUUID(), hit);
            }
            return new AreaQueryResult(resource, area, hits);
        });
    }

    @Nullable
    private static ResolvedAreaSource resolveSource(MinecraftServer server, ServerLevel areaLevel,
                                                    ListenerKey key) {
        ForceFieldResource forceField = null;
        AreaAddress areaAddress;
        if (key.source() == AreaSource.FORCE_FIELD) {
            ForceFieldAddress forceAddress = ForceFieldAddress.tryCreate(
                    areaLevel.dimension(), key.sourceId());
            if (forceAddress == null) return null;
            forceField = ForceFieldResourceStore.INSTANCE.get(server, forceAddress);
            if (forceField == null) return null;
            areaAddress = forceField.area();
        } else {
            areaAddress = AreaAddress.tryCreate(areaLevel.dimension(), key.sourceId());
        }
        if (areaAddress == null) return null;
        AreaResource areaResource = AreaResourceStore.INSTANCE.get(server, areaAddress);
        AreaResource.Resolved area = areaResource != null ? areaResource.resolve(areaLevel) : null;
        if (areaResource == null || area == null) return null;
        return new ResolvedAreaSource(areaResource, area,
                forceField != null ? forceField.address().id() : "",
                forceField != null ? forceField.generation() : Long.MIN_VALUE,
                forceField != null ? areaResource.anchorEntityId() : null);
    }

    private static boolean shouldTick(long tick, int interval, int offset) {
        return interval == 1 || Math.floorMod(tick, interval) == offset;
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

    private static GraphResourceId stateResource(GraphResourceScope scope, String graphId) {
        return new GraphResourceId(GraphResourceTypeRegistry.AREA_STATE, scope,
                GraphBindingKey.blueprint(graphId), GraphResourceSelector.Graph.INSTANCE, null, null);
    }

    private static void pruneScope(ServerState state, GraphResourceScope scope, Set<StateKey> seen) {
        state.states.entrySet().removeIf(entry -> entry.getKey().resourceId().scope().equals(scope)
                && !seen.contains(entry.getKey()));
    }

    private static void cleanupStaleStates(ServerState state, long tick) {
        if (state.lastCleanupTick == tick || Math.floorMod(tick, STALE_CLEANUP_INTERVAL) != 0) return;
        state.lastCleanupTick = tick;
        state.states.entrySet().removeIf(entry -> tick - entry.getValue().lastSeenTick > STALE_STATE_TICKS);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private void removeGraphResources(MinecraftServer server, Predicate<GraphResourceId> predicate) {
        ServerState state = servers.get(server);
        if (state != null) state.states.keySet().removeIf(key -> predicate.test(key.resourceId()));
    }

    private enum AreaPhase {
        ENTER(OnAreaEvent.PHASE_ENTER),
        STAY(OnAreaEvent.PHASE_STAY),
        EXIT(OnAreaEvent.PHASE_EXIT);

        private final String id;

        AreaPhase(String id) {
            this.id = id;
        }

        private static AreaPhase fromId(@Nullable String id) {
            for (AreaPhase phase : values()) {
                if (phase.id.equals(id)) return phase;
            }
            return ENTER;
        }
    }

    private enum AreaSource {
        AREA(OnAreaEvent.SOURCE_AREA),
        FORCE_FIELD(OnAreaEvent.SOURCE_FORCE_FIELD);

        private final String id;

        AreaSource(String id) {
            this.id = id;
        }

        private static AreaSource fromId(@Nullable String id) {
            return OnAreaEvent.SOURCE_FORCE_FIELD.equals(id) ? FORCE_FIELD : AREA;
        }
    }

    private record ListenerKey(String dimensionId, AreaSource source, String sourceId,
                               AreaTargetType targetType,
                               int interval, int offset) {
    }

    private record CompiledListener(int nodeId, ListenerKey key, AreaPhase phase) {
    }

    private record StateKey(GraphResourceId resourceId, BlueprintPlan plan, ListenerKey listener) {
    }

    private record AreaQueryResult(AreaResource resource, AreaResource.Resolved area,
                                   Map<UUID, AreaEntityQuery.Hit> hitsById) {
    }

    private record ResolvedAreaSource(AreaResource areaResource, AreaResource.Resolved area,
                                      String forceFieldId, long forceFieldGeneration,
                                      @Nullable UUID excludedEntityId) {
    }

    private record QueryCacheKey(AreaAddress address, long generation, AreaTargetType targetType,
                                 @Nullable UUID excludedEntityId,
                                 double centerX, double centerY, double centerZ) {
        private static QueryCacheKey of(AreaResource resource, AreaResource.Resolved area,
                                        AreaTargetType target, @Nullable UUID excludedEntityId) {
            return new QueryCacheKey(resource.address(), resource.generation(), target, excludedEntityId,
                    area.center().x, area.center().y, area.center().z);
        }
    }

    private static final class ListenerGroup {
        private final ListenerKey key;
        private final EnumMap<AreaPhase, List<Integer>> nodes = new EnumMap<>(AreaPhase.class);

        private ListenerGroup(ListenerKey key) {
            this.key = key;
        }
    }

    private static final class ListenerState {
        private Set<UUID> inside = Set.of();
        private long areaGeneration = Long.MIN_VALUE;
        private long forceFieldGeneration = Long.MIN_VALUE;
        private long lastSeenTick;

        private void reset() {
            inside = Set.of();
            areaGeneration = Long.MIN_VALUE;
            forceFieldGeneration = Long.MIN_VALUE;
        }
    }

    private static final class ServerState {
        private final Map<StateKey, ListenerState> states = new HashMap<>();
        private long lastCleanupTick = Long.MIN_VALUE;
    }
}
