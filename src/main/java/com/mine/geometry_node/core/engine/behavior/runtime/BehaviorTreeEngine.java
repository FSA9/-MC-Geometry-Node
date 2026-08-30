package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugSnapshot;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugAccess;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.scheduling.DueTickScheduler;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

/** Server-authoritative instance repository and fair per-world scheduler. */
public final class BehaviorTreeEngine {
    private static final int MAX_RETAINED_TERMINAL_SNAPSHOTS = 128;
    private static final int MAX_ASSET_RELOADS_PER_SERVER_TICK = 64;

    private final BehaviorTreeEvaluator evaluator;
    private final BehaviorRuntimeBudget budget;
    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    public BehaviorTreeEngine(BehaviorNodeExecutorRegistry executors, BehaviorRuntimeBudget budget) {
        this.evaluator = new BehaviorTreeEvaluator(Objects.requireNonNull(executors, "executors"));
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public BehaviorTreeProcess start(ServerLevel level, Mob owner, String graphId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        String canonicalGraphId = GraphAssetId.require(graphId);
        BehaviorTreePlan plan = resolvePlan(canonicalGraphId);
        if (plan == null) throw new IllegalArgumentException("Behavior tree is unavailable: " + canonicalGraphId);
        return start(level, owner, plan, true, seed(owner.getUUID(), plan.assetId()));
    }

    private BehaviorTreeProcess start(ServerLevel level, Mob owner, BehaviorTreePlan plan,
                                       boolean managedAsset, long randomSeed) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(plan, "plan");
        if (owner.level() != level || owner.isRemoved() || !owner.isAlive()) {
            throw new IllegalArgumentException("Behavior owner must be alive in the target server level");
        }
        requireExecutable(plan);
        ServerState server = state(level.getServer());
        if (server.instances.size() >= budget.targetLoadedInstances()) {
            throw new IllegalStateException("Behavior instance population limit exceeded");
        }
        if (server.ownerInstances.containsKey(owner.getUUID())) {
            throw new IllegalStateException("Owner already has an active behavior tree: " + owner.getUUID());
        }
        WorldState world = server.world(level.dimension());
        if (world.scheduler.activeCount() >= budget.maxQueuedWakeupsPerWorld()) {
            throw new IllegalStateException("Behavior wakeup queue limit exceeded in "
                    + level.dimension().identifier());
        }

        BehaviorTreeProcess instance = new BehaviorTreeProcess(UUID.randomUUID(), plan,
                new EntityHost(owner), budget, randomSeed);
        instance.markRunning();
        InstanceEntry entry = new InstanceEntry(instance, managedAsset, owner.getUUID());
        server.instances.put(instance.instanceId(), entry);
        server.ownerInstances.put(owner.getUUID(), instance.instanceId());
        server.assetInstances.computeIfAbsent(instance.graphId(), ignored -> new LinkedHashSet<>())
                .add(instance.instanceId());
        schedule(server, entry, safeIncrement(level.getGameTime()));
        return instance;
    }

    @Nullable
    public BehaviorTreeProcess get(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        return entry != null ? entry.instance : null;
    }

    @Nullable
    public BehaviorTreeProcess getForOwner(Entity owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) return null;
        ServerState state = servers.get(level.getServer());
        if (state == null) return null;
        UUID instanceId = state.ownerInstances.get(owner.getUUID());
        InstanceEntry entry = instanceId != null ? state.instances.get(instanceId) : null;
        return entry != null ? entry.instance : null;
    }

    /** Read-only server-thread query. A missing instance produces no side effects. */
    @Nullable
    public BehaviorTreeDebugSnapshot debugSnapshot(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        if (state == null) return null;
        InstanceEntry entry = state.instances.get(instanceId);
        return entry != null ? BehaviorTreeDebugSnapshot.capture(entry.instance)
                : state.terminalSnapshots.get(instanceId);
    }

    /** Lightweight server-thread access query that never captures nodes, blackboard or history. */
    @Nullable
    public BehaviorTreeDebugAccess debugAccess(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        if (state == null) return null;
        InstanceEntry entry = state.instances.get(instanceId);
        if (entry != null) return entry.access(true);
        return state.terminalAccess.get(instanceId);
    }

    /** Read-only server-thread query by stable owner UUID. */
    @Nullable
    public BehaviorTreeDebugSnapshot debugSnapshotForOwner(MinecraftServer server, UUID ownerId) {
        ServerState state = servers.get(server);
        UUID instanceId = state != null ? state.ownerInstances.get(ownerId) : null;
        if (instanceId == null && state != null) instanceId = state.lastTerminalByOwner.get(ownerId);
        return instanceId != null ? debugSnapshot(server, instanceId) : null;
    }

    /** Read-only indexed query; ordering is stable for the lifetime of the active entries. */
    public List<BehaviorTreeDebugSnapshot> debugSnapshotsForAsset(MinecraftServer server, String assetId) {
        ServerState state = servers.get(server);
        if (state == null || assetId == null) return List.of();
        Set<UUID> instanceIds = state.assetInstances.get(assetId);
        if (instanceIds == null || instanceIds.isEmpty()) return List.of();
        List<BehaviorTreeDebugSnapshot> result = new ArrayList<>(instanceIds.size());
        for (UUID instanceId : instanceIds) {
            InstanceEntry entry = state.instances.get(instanceId);
            if (entry != null) result.add(BehaviorTreeDebugSnapshot.capture(entry.instance));
        }
        return List.copyOf(result);
    }

    /** Lightweight status query that does not allocate a full debug snapshot. */
    @Nullable
    public BehaviorTerminationReason lastStopReasonForOwner(MinecraftServer server, UUID ownerId) {
        ServerState state = servers.get(server);
        if (state == null) return null;
        UUID activeId = state.ownerInstances.get(ownerId);
        InstanceEntry active = activeId != null ? state.instances.get(activeId) : null;
        if (active != null) return active.instance.stopReason();
        UUID terminalId = state.lastTerminalByOwner.get(ownerId);
        if (terminalId != null) {
            BehaviorTreeDebugSnapshot terminal = state.terminalSnapshots.get(terminalId);
            if (terminal != null) return terminal.stopReason();
        }
        return state.lastStopReasons.get(ownerId);
    }

    public boolean suspend(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        if (entry == null || entry.instance.state() != BehaviorInstanceState.RUNNING) return false;
        state.world(entry.levelKey()).scheduler.cancel(instanceId);
        evaluator.suspend(entry.instance);
        Entity owner = entry.instance.host().owner();
        if (owner instanceof Mob mob) mob.getNavigation().stop();
        return true;
    }

    public boolean resume(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        if (entry == null || entry.instance.state() != BehaviorInstanceState.SUSPENDED) return false;
        entry.instance.markResumed();
        schedule(state, entry, safeIncrement(entry.instance.host().gameTick()));
        return true;
    }

    public boolean wake(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        if (entry == null || entry.instance.state() != BehaviorInstanceState.RUNNING) return false;
        entry.instance.requestWakeup(entry.instance.host().gameTick());
        schedule(state, entry, entry.instance.nextWakeTick());
        return true;
    }

    public boolean stop(MinecraftServer server, UUID instanceId, BehaviorTerminationReason reason) {
        Objects.requireNonNull(reason, "reason");
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        if (entry == null) return false;
        stopAndRemove(state, entry, reason);
        return true;
    }

    public void tickLevel(ServerLevel level) {
        ServerState server = servers.get(level.getServer());
        if (server == null) return;
        long tick = level.getGameTime();
        server.beginServerTick(tick);
        server.processAssetReloadsOnce(tick, this);
        BehaviorNativeAiController.maintainPersistentControls(level);
        WorldState world = server.worlds.get(level.dimension());
        if (world == null) return;

        long started = System.nanoTime();
        int evaluated = 0;
        while (evaluated < budget.maxDueInstancesPerTick()
                && System.nanoTime() - started < budget.worldNanosPerTick()
                && server.spentNanos < budget.globalNanosPerTick()) {
            DueTickScheduler.Scheduled<UUID, InstanceEntry> scheduled = world.scheduler.pollDue(tick);
            if (scheduled == null) break;
            InstanceEntry entry = server.instances.get(scheduled.key());
            if (entry == null || entry != scheduled.value()) continue;
            BehaviorTreeProcess instance = entry.instance;
            if (instance.state() != BehaviorInstanceState.RUNNING) continue;
            if (!instance.host().isValid()) {
                stopAndRemove(server, entry, BehaviorTerminationReason.OWNER_INVALID);
                continue;
            }
            ServerLevel currentLevel = instance.host().level();
            if (currentLevel == null || !entry.levelKey().equals(currentLevel.dimension())) {
                stopAndRemove(server, entry, BehaviorTerminationReason.DIMENSION_CHANGED);
                continue;
            }
            long evaluationStarted = System.nanoTime();
            BehaviorTreeEvaluator.EvaluationOutcome outcome = evaluator.evaluate(instance);
            long elapsed = Math.max(0L, System.nanoTime() - evaluationStarted);
            server.spentNanos = saturatingAdd(server.spentNanos, elapsed);
            evaluated++;
            if (!outcome.succeeded() || instance.state() != BehaviorInstanceState.RUNNING) {
                removeIndexes(server, entry);
                continue;
            }
            schedule(server, entry, instance.nextWakeTick());
        }
    }

    public void ownerUnavailable(Entity owner, BehaviorTerminationReason reason) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) return;
        ServerState server = servers.get(level.getServer());
        if (server == null) return;
        UUID instanceId = server.ownerInstances.get(owner.getUUID());
        InstanceEntry entry = instanceId != null ? server.instances.get(instanceId) : null;
        if (entry != null) stopAndRemove(server, entry, reason);
    }

    public void graphAssetsChanged(@Nullable MinecraftServer minecraftServer,
                                   Set<String> affectedAssetIds) {
        if (affectedAssetIds == null || affectedAssetIds.isEmpty()) return;
        if (minecraftServer != null) {
            ServerState server = servers.get(minecraftServer);
            if (server != null) server.enqueueAssetReloads(affectedAssetIds);
            return;
        }
        for (ServerState server : List.copyOf(servers.values())) {
            server.enqueueAssetReloads(affectedAssetIds);
        }
    }

    public void shutdown(MinecraftServer minecraftServer) {
        ServerState server = servers.remove(minecraftServer);
        if (server == null) return;
        for (InstanceEntry entry : List.copyOf(server.instances.values())) {
            evaluator.stop(entry.instance, BehaviorTerminationReason.SERVER_STOPPING);
        }
        server.clear();
    }

    public int activeCount(MinecraftServer server) {
        ServerState state = servers.get(server);
        return state != null ? state.instances.size() : 0;
    }

    private void replaceManagedInstance(ServerState server, InstanceEntry oldEntry,
                                        @Nullable CompiledGraph newArtifact) {
        BehaviorTreePlan newPlan = newArtifact instanceof BehaviorTreePlan plan ? plan : null;
        Entity owner = oldEntry.instance.host().owner();
        evaluator.stop(oldEntry.instance, BehaviorTerminationReason.ASSET_REPLACED);
        removeIndexes(server, oldEntry);
        if (!(owner instanceof Mob mob) || newPlan == null || !(mob.level() instanceof ServerLevel level)
                || mob.isRemoved() || !mob.isAlive()) return;
        start(level, mob, newPlan, true, seed(mob.getUUID(), newPlan.assetId()));
    }

    private void stopAndRemove(ServerState server, InstanceEntry entry,
                               BehaviorTerminationReason reason) {
        evaluator.stop(entry.instance, reason);
        removeIndexes(server, entry);
    }

    private void removeIndexes(ServerState server, InstanceEntry entry) {
        UUID instanceId = entry.instance.instanceId();
        GraphResourceLifecycleManager.INSTANCE.releaseProcess(server.server, instanceId);
        if (!entry.instance.state().isActive()) {
            server.retainStopReason(entry.ownerId, entry.instance.stopReason());
            if (entry.instance.debugTracingEnabled()) {
                try {
                    entry.refreshOwnerAccess();
                    server.retainTerminalSnapshot(entry.ownerId,
                            BehaviorTreeDebugSnapshot.capture(entry.instance), entry.access(false));
                } catch (RuntimeException exception) {
                    GeometryNode.LOGGER.warn(
                            "[BehaviorRuntime] Failed to capture terminal snapshot for {}; indexes will still be removed",
                            instanceId, exception);
                }
            }
        }
        server.instances.remove(instanceId, entry);
        server.ownerInstances.remove(entry.ownerId, instanceId);
        Set<UUID> assetInstanceIds = server.assetInstances.get(entry.instance.graphId());
        if (assetInstanceIds != null) {
            assetInstanceIds.remove(instanceId);
            if (assetInstanceIds.isEmpty()) {
                server.assetInstances.remove(entry.instance.graphId(), assetInstanceIds);
            }
        }
        WorldState world = server.worlds.get(entry.levelKey());
        if (world != null) {
            world.scheduler.cancel(instanceId);
            if (world.scheduler.isEmpty()) server.worlds.remove(entry.levelKey(), world);
        }
    }

    private void schedule(ServerState server, InstanceEntry entry, long dueTick) {
        WorldState world = server.world(entry.levelKey());
        if (dueTick == Long.MAX_VALUE) {
            world.scheduler.cancel(entry.instance.instanceId());
            if (world.scheduler.isEmpty()) server.worlds.remove(entry.levelKey(), world);
            return;
        }
        if (!world.scheduler.contains(entry.instance.instanceId())
                && world.scheduler.activeCount() >= budget.maxQueuedWakeupsPerWorld()) {
            stopAndRemove(server, entry, BehaviorTerminationReason.BUDGET_EXHAUSTED);
            return;
        }
        world.scheduler.scheduleReplacing(entry.instance.instanceId(), entry, dueTick);
    }

    private void requireExecutable(BehaviorTreePlan plan) {
        if (plan.getRootNode() < 0) throw new IllegalArgumentException("Behavior plan has no root");
    }

    @Nullable
    private static BehaviorTreePlan resolvePlan(String graphId) {
        CompiledGraph graph = currentPlan(graphId);
        return graph instanceof BehaviorTreePlan plan ? plan : null;
    }

    @Nullable
    private static CompiledGraph currentPlan(String graphId) {
        return GraphAssetLifecycleIndex.INSTANCE.getArtifact(graphId, GraphKind.BEHAVIOR_TREE);
    }

    private ServerState state(MinecraftServer server) {
        return servers.computeIfAbsent(server, ServerState::new);
    }

    private static long seed(UUID ownerId, String graphId) {
        return ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits()
                ^ (graphId != null ? graphId.hashCode() : 0);
    }

    private static long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private static long saturatingAdd(long first, long second) {
        long result = first + second;
        return result < first ? Long.MAX_VALUE : result;
    }

    private static final class ServerState {
        private final MinecraftServer server;
        private final Map<UUID, InstanceEntry> instances = new HashMap<>();
        private final Map<UUID, UUID> ownerInstances = new HashMap<>();
        private final Map<String, LinkedHashSet<UUID>> assetInstances = new HashMap<>();
        private final LinkedHashMap<UUID, BehaviorTreeDebugSnapshot> terminalSnapshots = new LinkedHashMap<>();
        private final Map<UUID, BehaviorTreeDebugAccess> terminalAccess = new HashMap<>();
        private final Map<UUID, UUID> lastTerminalByOwner = new HashMap<>();
        private final LinkedHashMap<UUID, BehaviorTerminationReason> lastStopReasons = new LinkedHashMap<>();
        private final LinkedHashSet<UUID> pendingAssetReloads = new LinkedHashSet<>();
        private final Map<ResourceKey<Level>, WorldState> worlds = new HashMap<>();
        private long tick = Long.MIN_VALUE;
        private long assetReloadTick = Long.MIN_VALUE;
        private long spentNanos;

        private ServerState(MinecraftServer server) {
            this.server = server;
        }

        private WorldState world(ResourceKey<Level> levelKey) {
            return worlds.computeIfAbsent(levelKey, ignored -> new WorldState());
        }

        private void beginServerTick(long currentTick) {
            if (tick == currentTick) return;
            tick = currentTick;
            spentNanos = 0L;
        }

        private void enqueueAssetReloads(Set<String> affectedAssetIds) {
            for (String assetId : affectedAssetIds) {
                Set<UUID> ids = assetInstances.get(assetId);
                if (ids != null) pendingAssetReloads.addAll(ids);
            }
        }

        private void processAssetReloadsOnce(long currentTick, BehaviorTreeEngine service) {
            if (assetReloadTick == currentTick) return;
            assetReloadTick = currentTick;
            int processed = 0;
            Iterator<UUID> iterator = pendingAssetReloads.iterator();
            while (iterator.hasNext() && processed < MAX_ASSET_RELOADS_PER_SERVER_TICK) {
                UUID instanceId = iterator.next();
                iterator.remove();
                InstanceEntry entry = instances.get(instanceId);
                if (entry != null && entry.managedAsset) {
                    try {
                        service.replaceManagedInstance(this, entry, currentPlan(entry.instance.graphId()));
                    } catch (RuntimeException exception) {
                        com.mine.geometry_node.GeometryNode.LOGGER.error(
                                "Unable to restart behavior asset {} for owner {}",
                                entry.instance.graphId(), entry.ownerId, exception);
                    }
                }
                processed++;
            }
        }

        private void retainTerminalSnapshot(UUID ownerId, BehaviorTreeDebugSnapshot snapshot,
                                            BehaviorTreeDebugAccess access) {
            terminalSnapshots.put(snapshot.instanceId(), snapshot);
            terminalAccess.put(snapshot.instanceId(), access);
            lastTerminalByOwner.put(ownerId, snapshot.instanceId());
            while (terminalSnapshots.size() > MAX_RETAINED_TERMINAL_SNAPSHOTS) {
                Iterator<Map.Entry<UUID, BehaviorTreeDebugSnapshot>> iterator =
                        terminalSnapshots.entrySet().iterator();
                Map.Entry<UUID, BehaviorTreeDebugSnapshot> oldest = iterator.next();
                iterator.remove();
                terminalAccess.remove(oldest.getKey());
                UUID oldOwnerId = oldest.getValue().ownerId();
                if (oldOwnerId != null) {
                    lastTerminalByOwner.remove(oldOwnerId, oldest.getKey());
                } else {
                    lastTerminalByOwner.values().removeIf(oldest.getKey()::equals);
                }
            }
        }

        private void retainStopReason(UUID ownerId, @Nullable BehaviorTerminationReason reason) {
            if (reason == null) return;
            lastStopReasons.remove(ownerId);
            lastStopReasons.put(ownerId, reason);
            while (lastStopReasons.size() > MAX_RETAINED_TERMINAL_SNAPSHOTS) {
                Iterator<UUID> iterator = lastStopReasons.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        private void clear() {
            instances.clear();
            ownerInstances.clear();
            assetInstances.clear();
            terminalSnapshots.clear();
            terminalAccess.clear();
            lastTerminalByOwner.clear();
            lastStopReasons.clear();
            pendingAssetReloads.clear();
            worlds.values().forEach(world -> world.scheduler.clear());
            worlds.clear();
        }
    }

    private static final class WorldState {
        private final DueTickScheduler<UUID, InstanceEntry> scheduler = new DueTickScheduler<>();
    }

    private static final class InstanceEntry {
        private final BehaviorTreeProcess instance;
        private final boolean managedAsset;
        private final ResourceKey<Level> levelKey;
        private final UUID ownerId;
        private String lastOwnerDimension;
        private double lastOwnerX;
        private double lastOwnerY;
        private double lastOwnerZ;
        private boolean positionKnown;

        private InstanceEntry(BehaviorTreeProcess instance, boolean managedAsset, UUID ownerId) {
            this.instance = instance;
            this.managedAsset = managedAsset;
            this.ownerId = ownerId;
            ServerLevel level = instance.host().level();
            this.levelKey = level != null ? level.dimension() : Level.OVERWORLD;
            this.lastOwnerDimension = this.levelKey.identifier().toString();
            refreshOwnerAccess();
        }

        private ResourceKey<Level> levelKey() {
            return levelKey;
        }

        private boolean refreshOwnerAccess() {
            Entity owner = instance.host().owner();
            if (owner == null) return false;
            if (owner.level() instanceof ServerLevel level) {
                lastOwnerDimension = level.dimension().identifier().toString();
            }
            lastOwnerX = owner.getX();
            lastOwnerY = owner.getY();
            lastOwnerZ = owner.getZ();
            positionKnown = true;
            return true;
        }

        private BehaviorTreeDebugAccess access(boolean active) {
            boolean confirmedPosition = active ? refreshOwnerAccess() : positionKnown;
            return new BehaviorTreeDebugAccess(instance.instanceId(), ownerId, lastOwnerDimension,
                    lastOwnerX, lastOwnerY, lastOwnerZ, confirmedPosition, active);
        }
    }

    private static final class EntityHost implements BehaviorRuntimeHost {
        private final UUID ownerId;
        private final WeakReference<Mob> owner;
        private final BehaviorNativeAiController nativeAi;

        private EntityHost(Mob owner) {
            this.ownerId = owner.getUUID();
            this.owner = new WeakReference<>(owner);
            this.nativeAi = new BehaviorNativeAiController(owner);
        }

        @Override public String identity() { return ownerId.toString(); }

        @Override
        public boolean isValid() {
            Mob value = owner.get();
            return value != null && !value.isRemoved() && value.isAlive()
                    && value.level() instanceof ServerLevel;
        }

        @Override public long gameTick() {
            ServerLevel level = level();
            return level != null ? level.getGameTime() : 0L;
        }

        @Override public long nanoTime() { return System.nanoTime(); }
        @Override public ServerLevel level() {
            Mob value = owner.get();
            return value != null && value.level() instanceof ServerLevel level ? level : null;
        }
        @Override public Mob owner() { return owner.get(); }

        @Override
        public boolean acquireResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
            Mob mob = owner.get();
            return mob != null && nativeAi.acquire(resources);
        }

        @Override
        public void releaseResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
            nativeAi.release(resources);
        }

        @Override
        public LivingEntity setAttackTarget(@Nullable LivingEntity target) {
            return nativeAi.setAttackTarget(target);
        }

        @Override
        public void releasePersistentControls() {
            nativeAi.releasePersistentControls();
        }
    }
}
