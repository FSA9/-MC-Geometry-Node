package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaTargetType;
import com.mine.geometry_node.core.engine.graph.value.GraphEntityReferenceResolver;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceScope;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceSelector;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaEvent;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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

/** Polls live Area resources and dispatches listeners without creating Areas implicitly. */
public final class AreaTriggerDispatcher {
    private static final int STALE_STATE_TICKS = 20 * 60;
    private static final int STALE_CLEANUP_INTERVAL = 20 * 10;

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();
    private final Map<BlueprintPlan, List<ListenerGroup>> configCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    public AreaTriggerDispatcher() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_area_listener_state",
                this::removeGraphResources);
    }

    public void tickLevel(ServerLevel hostLevel) {
        Set<String> graphIds = BlueprintRuntime.INSTANCE.getGlobalGraphsForEvent(
                hostLevel, OnAreaEvent.TYPE_ID);
        long currentTick = hostLevel.getGameTime();
        if (graphIds.isEmpty()) {
            ServerState state = servers.get(hostLevel.getServer());
            if (state != null) {
                cleanupStaleStates(state, currentTick);
                state.statesByScope.remove(scope(hostLevel));
            }
            return;
        }

        ServerState state = servers.computeIfAbsent(hostLevel.getServer(), ignored -> new ServerState());
        cleanupStaleStates(state, currentTick);
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();
        Map<SourceCacheKey, List<ResolvedAreaSource>> sourceCache = new HashMap<>();

        GraphResourceScope scope = scope(hostLevel);
        LevelGraphAttachment attachment = LevelGraphAttachment.get(hostLevel);
        Map<StateKey, ListenerState> scopeStates =
                state.statesByScope.computeIfAbsent(scope, ignored -> new HashMap<>());
        Set<StateKey> seenStates = new HashSet<>();
        for (String graphId : graphIds) {
            tickGraph(scopeStates, hostLevel, null, graphId,
                    BlueprintRuntime.INSTANCE.getGraphIndex(hostLevel.getServer(), graphId),
                    attachment::getProcess, attachment::addProcess, stateResource(scope, graphId),
                    currentTick, seenStates, sourceCache, queryCache);
        }
        pruneScope(state, scope, scopeStates, seenStates);
    }

    public void tickQueuedEntities(ServerLevel hostLevel) {
        ServerState state = servers.get(hostLevel.getServer());
        if (state == null) return;
        PendingEntityBatch pending = state.pendingEntities.remove(hostLevel.dimension());
        long currentTick = hostLevel.getGameTime();
        if (pending == null || pending.gameTime != currentTick || pending.entities.isEmpty()) return;

        cleanupStaleStates(state, currentTick);
        Map<QueryCacheKey, AreaQueryResult> queryCache = new HashMap<>();
        Map<SourceCacheKey, List<ResolvedAreaSource>> sourceCache = new HashMap<>();
        for (Entity owner : pending.entities.values()) {
            tickEntity(state, hostLevel, owner, currentTick, sourceCache, queryCache);
        }
    }

    public void queueEntity(ServerLevel hostLevel, Entity owner) {
        if (hostLevel == null || owner == null || owner.isRemoved()) return;
        ServerState state = servers.computeIfAbsent(hostLevel.getServer(), ignored -> new ServerState());
        long gameTime = hostLevel.getGameTime();
        PendingEntityBatch pending = state.pendingEntities.computeIfAbsent(
                hostLevel.dimension(), ignored -> new PendingEntityBatch(gameTime));
        if (pending.gameTime != gameTime) {
            pending.entities.clear();
            pending.gameTime = gameTime;
        }
        pending.entities.put(owner.getUUID(), owner);
    }

    public void forgetEntity(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return;
        ServerState state = servers.get(level.getServer());
        if (state == null) return;
        PendingEntityBatch pending = state.pendingEntities.get(level.dimension());
        if (pending == null) return;
        pending.entities.remove(entity.getUUID());
        if (pending.entities.isEmpty()) state.pendingEntities.remove(level.dimension(), pending);
    }

    public void forgetLevel(ServerLevel level) {
        if (level == null) return;
        ServerState state = servers.get(level.getServer());
        if (state == null) return;
        state.pendingEntities.remove(level.dimension());
        state.statesByScope.remove(scope(level));
    }

    private void tickEntity(ServerState state, ServerLevel hostLevel, Entity owner, long currentTick,
                            Map<SourceCacheKey, List<ResolvedAreaSource>> sourceCache,
                            Map<QueryCacheKey, AreaQueryResult> queryCache) {
        if (owner == null || owner.isRemoved() || owner.level() != hostLevel) return;
        EntityGraphAttachment attachment = owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
        if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;
        GraphResourceScope scope = new GraphResourceScope.EntityScope(hostLevel.dimension(), owner.getUUID());
        Map<StateKey, ListenerState> scopeStates =
                state.statesByScope.computeIfAbsent(scope, ignored -> new HashMap<>());

        Set<StateKey> seenStates = new HashSet<>();
        for (String graphId : BlueprintRuntime.INSTANCE.getEntityGraphsForEvent(owner, OnAreaEvent.TYPE_ID)) {
            tickGraph(scopeStates, hostLevel, owner, graphId,
                    BlueprintRuntime.INSTANCE.getGraphIndex(hostLevel.getServer(), graphId),
                    attachment::getProcess, attachment::addProcess, stateResource(scope, graphId),
                    currentTick, seenStates, sourceCache, queryCache);
        }
        pruneScope(state, scope, scopeStates, seenStates);
    }

    private void tickGraph(Map<StateKey, ListenerState> scopeStates,
                           ServerLevel hostLevel, @Nullable Entity owner,
                           String graphId, @Nullable BlueprintPlan plan,
                           Function<String, BlueprintProcess> processFinder,
                           Consumer<BlueprintProcess> mountAction,
                           GraphResourceId stateResource, long currentTick,
                           Set<StateKey> seenStates,
                           Map<SourceCacheKey, List<ResolvedAreaSource>> sourceCache,
                           Map<QueryCacheKey, AreaQueryResult> queryCache) {
        if (plan == null) return;

        for (ListenerGroup group : getCompiledGroups(plan)) {
            StateKey stateKey = new StateKey(stateResource, plan, group.key);
            seenStates.add(stateKey);
            ListenerState listenerState = scopeStates.computeIfAbsent(stateKey,
                    ignored -> new ListenerState());
            listenerState.lastSeenTick = currentTick;
            if (!shouldTick(currentTick, group.key.interval(), group.key.offset())) continue;

            ServerLevel areaLevel = RegistryDataManager.resolveDimension(hostLevel.getServer(),
                    group.key.dimensionId());
            if (areaLevel == null) {
                listenerState.reset();
                continue;
            }
            SourceCacheKey sourceKey = SourceCacheKey.of(areaLevel, group.key);
            List<ResolvedAreaSource> sources = sourceCache.computeIfAbsent(sourceKey,
                    ignored -> resolveSources(hostLevel.getServer(), areaLevel, group.key));
            if (sources.isEmpty()) {
                listenerState.reset();
                continue;
            }
            Set<AreaRef> seenAreas = new HashSet<>();
            for (ResolvedAreaSource source : sources) {
                AreaRef areaRef = source.areaResource().reference();
                seenAreas.add(areaRef);
                SourceListenerState sourceState = listenerState.areas.computeIfAbsent(
                        areaRef, ignored -> new SourceListenerState());
                if (sourceState.areaGeneration != source.areaResource().generation()
                        || sourceState.forceFieldGeneration != source.forceFieldGeneration()) {
                    sourceState.inside = Set.of();
                    sourceState.areaGeneration = source.areaResource().generation();
                    sourceState.forceFieldGeneration = source.forceFieldGeneration();
                }

                AreaQueryResult result = findEntities(areaLevel, source.areaResource(), source.area(),
                        group.key.targetType(), source.excludedEntityId(), queryCache);
                Set<UUID> previous = sourceState.inside;
                Set<UUID> current = result.hitsById().keySet();
                boolean alive = dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group,
                        AreaPhase.ENTER, current, previous, source, result,
                        processFinder, mountAction);
                if (alive) {
                    alive = dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group,
                            AreaPhase.STAY, current, previous, source, result,
                            processFinder, mountAction);
                }
                if (alive) {
                    alive = dispatchPhase(hostLevel, areaLevel, owner, graphId, plan, group,
                            AreaPhase.EXIT, current, previous, source, result,
                            processFinder, mountAction);
                }
                if (alive) {
                    if (!previous.equals(current)) sourceState.inside = new LinkedHashSet<>(current);
                } else {
                    seenAreas.remove(areaRef);
                }
            }
            listenerState.areas.keySet().retainAll(seenAreas);
        }
    }

    private List<ListenerGroup> getCompiledGroups(BlueprintPlan plan) {
        synchronized (configCache) {
            return configCache.computeIfAbsent(plan, AreaTriggerDispatcher::compileGroups);
        }
    }

    private static List<ListenerGroup> compileGroups(BlueprintPlan plan) {
        List<Integer> nodeIds = plan.findNodesByType(OnAreaEvent.TYPE_ID);
        if (nodeIds.isEmpty()) return List.of();
        Map<ListenerKey, EnumMap<AreaPhase, List<Integer>>> groups = new LinkedHashMap<>();
        for (int nodeId : nodeIds) {
            String dimension = plan.getStaticInput(nodeId, OnAreaEvent.SUBSCRIPTION_DIMENSION_PORT, String.class,
                    RegistryDataManager.DEFAULT_DIMENSION);
            AreaSource source = AreaSource.fromId(plan.getStaticInput(nodeId,
                    OnAreaEvent.SUBSCRIPTION_SOURCE_PORT, String.class, OnAreaEvent.SOURCE_AREA));
            AreaMatch match = source == AreaSource.AREA
                    ? AreaMatch.fromId(plan.getStaticInput(nodeId,
                            OnAreaEvent.SUBSCRIPTION_MATCH_PORT, String.class,
                            OnAreaEvent.MATCH_EXACT))
                    : AreaMatch.EXACT;
            String sourceId = source == AreaSource.FORCE_FIELD
                    ? plan.getStaticInput(nodeId, OnAreaEvent.SUBSCRIPTION_FORCE_FIELD_ID_PORT, String.class, "")
                    : match == AreaMatch.ALL ? ""
                    : plan.getStaticInput(nodeId, OnAreaEvent.SUBSCRIPTION_AREA_ID_PORT, String.class, "");
            AreaTargetType target = AreaTargetType.fromId(plan.getStaticInput(nodeId,
                    OnAreaEvent.TARGET_PORT, String.class, AreaTargetType.ALL.id()));
            int interval = Math.max(1, plan.getStaticInput(nodeId,
                    OnAreaEvent.INTERVAL_TICK_PORT, Integer.class, 1));
            int offset = Math.floorMod(plan.getStaticInput(nodeId,
                    OnAreaEvent.OFFSET_TICK_PORT, Integer.class, 0), interval);
            ListenerKey key = new ListenerKey(dimension == null ? "" : dimension.trim(), source,
                    match, sourceId == null ? "" : sourceId.trim(), target, interval, offset);
            AreaPhase phase = AreaPhase.fromId(plan.getStaticInput(nodeId,
                    OnAreaEvent.PHASE_PORT, String.class, OnAreaEvent.PHASE_ENTER));
            groups.computeIfAbsent(key, ignored -> new EnumMap<>(AreaPhase.class))
                    .computeIfAbsent(phase, ignored -> new ArrayList<>()).add(nodeId);
        }
        List<ListenerGroup> result = new ArrayList<>(groups.size());
        groups.forEach((key, nodes) -> {
            EnumMap<AreaPhase, List<Integer>> immutableNodes = new EnumMap<>(AreaPhase.class);
            nodes.forEach((phase, ids) -> immutableNodes.put(phase, List.copyOf(ids)));
            result.add(new ListenerGroup(key, Collections.unmodifiableMap(immutableNodes)));
        });
        return List.copyOf(result);
    }

    private boolean dispatchPhase(ServerLevel hostLevel, ServerLevel areaLevel, @Nullable Entity owner,
                                  String graphId, BlueprintPlan plan, ListenerGroup group, AreaPhase phase,
                                  Set<UUID> current, Set<UUID> previous,
                                  ResolvedAreaSource source, AreaQueryResult result,
                                  Function<String, BlueprintProcess> processFinder,
                                  Consumer<BlueprintProcess> mountAction) {
        List<Integer> nodes = group.nodes.get(phase);
        if (nodes == null || nodes.isEmpty()) return true;

        AreaResource resource = result.resource();
        int insideCount = result.hitsById().size();

        Set<UUID> candidates = phase == AreaPhase.EXIT ? previous : current;
        for (UUID entityId : candidates) {
            boolean wasInside = previous.contains(entityId);
            boolean isInside = current.contains(entityId);
            if (phase == AreaPhase.ENTER && wasInside) continue;
            if (phase == AreaPhase.STAY && !wasInside) continue;
            if (phase == AreaPhase.EXIT && isInside) continue;
            AreaEntityQuery.Hit hit = result.hitsById().get(entityId);
            Entity trigger = hit != null ? hit.entity()
                    : GraphEntityReferenceResolver.resolve(entityId, areaLevel);
            if (trigger == null || trigger.isRemoved()) continue;
            if (!isSourceAlive(hostLevel.getServer(), areaLevel, source)) {
                return false;
            }
            Entity eventEntity = owner != null ? owner : trigger;
            Map<String, Object> eventData = EventPayload.of(
                    StandardPorts.ENTITY.getId(), eventEntity,
                    StandardPorts.TRIGGER_ENTITY.getId(), trigger,
                    StandardPorts.HIT_POS.getId(), hit != null ? hit.hitPos() : trigger.position(),
                    StandardPorts.VECTOR.getId(), hit != null ? hit.velocity() : trigger.getDeltaMovement(),
                    StandardPorts.TYPE.getId(), phase.id,
                    StandardPorts.AREA.getId(), resource.reference(),
                    StandardPorts.FORCE_FIELD_ID.getId(), source.forceFieldId(),
                    OnAreaEvent.SOURCE_PORT, group.key.source().id,
                    OnAreaEvent.INSIDE_COUNT_PORT, insideCount,
                    OnAreaEvent.TARGET_PORT, group.key.targetType().id()
            ).values();
            for (int nodeId : nodes) {
                // The process keeps its host level; the selected dimension only controls Area lookup and querying.
                BlueprintRuntime.INSTANCE.executeEventNode(hostLevel, owner, graphId, plan, nodeId,
                        eventData, processFinder, mountAction);
                if (!isSourceAlive(hostLevel.getServer(), areaLevel, source)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSourceAlive(MinecraftServer server, ServerLevel areaLevel,
                                         ResolvedAreaSource source) {
        if (AreaResourceStore.INSTANCE.get(server, source.areaResource().reference()) == null) return false;
        if (source.forceFieldId().isBlank()) return true;
        ForceFieldAddress address = ForceFieldAddress.tryCreate(
                areaLevel.dimension(), source.forceFieldId());
        ForceFieldResource current = address != null
                ? ForceFieldResourceStore.INSTANCE.get(server, address) : null;
        return current != null && current.generation() == source.forceFieldGeneration();
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

    private static List<ResolvedAreaSource> resolveSources(MinecraftServer server, ServerLevel areaLevel,
                                                           ListenerKey key) {
        ForceFieldResource forceField = null;
        AreaAddress areaAddress;
        if (key.source() == AreaSource.FORCE_FIELD) {
            if (key.sourceId().isBlank()) return List.of();
            ForceFieldAddress forceAddress = ForceFieldAddress.tryCreate(
                    areaLevel.dimension(), key.sourceId());
            if (forceAddress == null) return List.of();
            forceField = ForceFieldResourceStore.INSTANCE.get(server, forceAddress);
            if (forceField == null) return List.of();
            areaAddress = forceField.area();
        } else {
            if (key.match() == AreaMatch.ALL) {
                List<ResolvedAreaSource> result = new ArrayList<>();
                for (AreaResource resource : AreaResourceStore.INSTANCE.snapshot(areaLevel)) {
                    AreaResource.Resolved area = resource.resolve(areaLevel);
                    if (area != null) {
                        result.add(new ResolvedAreaSource(resource, area, "",
                                Long.MIN_VALUE, null));
                    }
                }
                return List.copyOf(result);
            }
            areaAddress = AreaAddress.tryCreate(areaLevel.dimension(), key.sourceId());
        }
        if (areaAddress == null) return List.of();
        AreaResource areaResource = AreaResourceStore.INSTANCE.get(server, areaAddress);
        AreaResource.Resolved area = areaResource != null ? areaResource.resolve(areaLevel) : null;
        if (areaResource == null || area == null) return List.of();
        return List.of(new ResolvedAreaSource(areaResource, area,
                forceField != null ? forceField.address().id() : "",
                forceField != null ? forceField.generation() : Long.MIN_VALUE,
                forceField != null ? areaResource.anchorEntityId() : null));
    }

    private static boolean shouldTick(long tick, int interval, int offset) {
        return interval == 1 || Math.floorMod(tick, interval) == offset;
    }

    private static GraphResourceId stateResource(GraphResourceScope scope, String graphId) {
        return new GraphResourceId(GraphResourceTypeRegistry.AREA_STATE, scope,
                GraphBindingKey.blueprint(graphId), GraphResourceSelector.Graph.INSTANCE, null, null);
    }

    private static void pruneScope(ServerState state, GraphResourceScope scope,
                                   Map<StateKey, ListenerState> scopeStates, Set<StateKey> seen) {
        scopeStates.keySet().removeIf(key -> !seen.contains(key));
        if (scopeStates.isEmpty()) state.statesByScope.remove(scope, scopeStates);
    }

    private static void cleanupStaleStates(ServerState state, long tick) {
        if (state.lastCleanupTick == tick || Math.floorMod(tick, STALE_CLEANUP_INTERVAL) != 0) return;
        state.lastCleanupTick = tick;
        state.statesByScope.values().forEach(states ->
                states.entrySet().removeIf(entry -> tick - entry.getValue().lastSeenTick > STALE_STATE_TICKS));
        state.statesByScope.values().removeIf(Map::isEmpty);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private void removeGraphResources(MinecraftServer server, GraphResourceRelease release) {
        ServerState state = servers.get(server);
        if (state == null) return;
        if (release instanceof GraphResourceRelease.Entity entityRelease) {
            state.statesByScope.remove(new GraphResourceScope.EntityScope(
                    entityRelease.dimension(), entityRelease.entityId()));
            return;
        }
        state.statesByScope.values().forEach(states ->
                states.keySet().removeIf(key -> release.matches(key.resourceId())));
        state.statesByScope.values().removeIf(Map::isEmpty);
    }

    private static GraphResourceScope scope(ServerLevel level) {
        return new GraphResourceScope.LevelScope(level.dimension());
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

    private enum AreaMatch {
        EXACT,
        ALL;

        private static AreaMatch fromId(@Nullable String id) {
            return OnAreaEvent.MATCH_ALL.equals(id) ? ALL : EXACT;
        }
    }

    private record ListenerKey(String dimensionId, AreaSource source, AreaMatch match, String sourceId,
                               AreaTargetType targetType,
                               int interval, int offset) {
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

    private record SourceCacheKey(ResourceKey<Level> dimension, AreaSource source,
                                  AreaMatch match, String sourceId) {
        private static SourceCacheKey of(ServerLevel level, ListenerKey listener) {
            return new SourceCacheKey(level.dimension(), listener.source(),
                    listener.match(), listener.sourceId());
        }
    }

    private record ListenerGroup(ListenerKey key, Map<AreaPhase, List<Integer>> nodes) {
    }

    private static final class ListenerState {
        private final Map<AreaRef, SourceListenerState> areas = new HashMap<>();
        private long lastSeenTick;

        private void reset() {
            areas.clear();
        }
    }

    private static final class SourceListenerState {
        private Set<UUID> inside = Set.of();
        private long areaGeneration = Long.MIN_VALUE;
        private long forceFieldGeneration = Long.MIN_VALUE;
    }

    private static final class ServerState {
        private final Map<GraphResourceScope, Map<StateKey, ListenerState>> statesByScope = new HashMap<>();
        private final Map<ResourceKey<Level>, PendingEntityBatch> pendingEntities = new HashMap<>();
        private long lastCleanupTick = Long.MIN_VALUE;
    }

    private static final class PendingEntityBatch {
        private long gameTime;
        private final Map<UUID, Entity> entities = new LinkedHashMap<>();

        private PendingEntityBatch(long gameTime) {
            this.gameTime = gameTime;
        }
    }
}
