package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative instance repository and fair per-world scheduler. */
public final class BehaviorRuntimeService {
    private final BehaviorTreeEvaluator evaluator;
    private final BehaviorNodeExecutorRegistry executors;
    private final BehaviorRuntimeBudget budget;
    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    public BehaviorRuntimeService(BehaviorNodeExecutorRegistry executors, BehaviorRuntimeBudget budget) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.evaluator = new BehaviorTreeEvaluator(executors);
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, String graphId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        if (graphId == null || graphId.isBlank()) {
            throw new IllegalArgumentException("Behavior graph id cannot be empty");
        }
        BehaviorTreePlan plan = resolvePlan(graphId);
        if (plan == null) throw new IllegalArgumentException("Behavior tree is unavailable: " + graphId);
        return start(level, owner, plan, true, seed(owner.getUUID(), plan.assetId()));
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, BehaviorTreePlan plan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(plan, "plan");
        return start(level, owner, plan, false, seed(owner.getUUID(), plan.assetId()));
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, BehaviorTreePlan plan, long randomSeed) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(plan, "plan");
        return start(level, owner, plan, false, randomSeed);
    }

    private BehaviorTreeInstance start(ServerLevel level, Mob owner, BehaviorTreePlan plan,
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
        if (world.scheduler.size() >= budget.maxQueuedWakeupsPerWorld()) {
            throw new IllegalStateException("Behavior wakeup queue limit exceeded in "
                    + level.dimension().identifier());
        }

        BehaviorTreeInstance instance = new BehaviorTreeInstance(UUID.randomUUID(), plan,
                new EntityHost(owner), budget, randomSeed);
        instance.markRunning();
        InstanceEntry entry = new InstanceEntry(instance, managedAsset);
        server.instances.put(instance.instanceId(), entry);
        server.ownerInstances.put(owner.getUUID(), instance.instanceId());
        schedule(server, entry, safeIncrement(level.getGameTime()));
        return instance;
    }

    @Nullable
    public BehaviorTreeInstance get(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        return entry != null ? entry.instance : null;
    }

    @Nullable
    public BehaviorTreeInstance getForOwner(Entity owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) return null;
        ServerState state = servers.get(level.getServer());
        if (state == null) return null;
        UUID instanceId = state.ownerInstances.get(owner.getUUID());
        InstanceEntry entry = instanceId != null ? state.instances.get(instanceId) : null;
        return entry != null ? entry.instance : null;
    }

    public boolean suspend(MinecraftServer server, UUID instanceId) {
        ServerState state = servers.get(server);
        InstanceEntry entry = state != null ? state.instances.get(instanceId) : null;
        if (entry == null || entry.instance.state() != BehaviorInstanceState.RUNNING) return false;
        entry.instance.markSuspended();
        state.world(entry.levelKey()).scheduler.cancel(instanceId);
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
        WorldState world = server.worlds.get(level.dimension());
        if (world == null) return;

        long started = System.nanoTime();
        int evaluated = 0;
        while (evaluated < budget.maxDueInstancesPerTick()
                && System.nanoTime() - started < budget.worldNanosPerTick()
                && server.spentNanos < budget.globalNanosPerTick()) {
            ScheduledEntry scheduled = world.scheduler.pollDue(tick);
            if (scheduled == null) break;
            InstanceEntry entry = server.instances.get(scheduled.instanceId);
            if (entry == null || entry != scheduled.entry) continue;
            BehaviorTreeInstance instance = entry.instance;
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
            if (entry.managedAsset && currentPlan(instance.graphId()) != instance.plan()) {
                replaceManagedInstance(server, entry);
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

    public void graphReloaded(MinecraftServer minecraftServer, String graphId,
                              @Nullable CompiledGraph newArtifact) {
        ServerState server = servers.get(minecraftServer);
        if (server == null) return;
        List<InstanceEntry> affected = server.instances.values().stream()
                .filter(entry -> entry.managedAsset && entry.instance.graphId().equals(graphId))
                .toList();
        for (InstanceEntry entry : affected) replaceManagedInstance(server, entry, newArtifact);
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

    private void replaceManagedInstance(ServerState server, InstanceEntry oldEntry) {
        replaceManagedInstance(server, oldEntry, currentPlan(oldEntry.instance.graphId()));
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
        server.instances.remove(instanceId, entry);
        Entity owner = entry.instance.host().owner();
        if (owner != null) server.ownerInstances.remove(owner.getUUID(), instanceId);
        WorldState world = server.worlds.get(entry.levelKey());
        if (world != null) {
            world.scheduler.cancel(instanceId);
            if (world.scheduler.isEmpty()) server.worlds.remove(entry.levelKey(), world);
        }
    }

    private void schedule(ServerState server, InstanceEntry entry, long dueTick) {
        WorldState world = server.world(entry.levelKey());
        if (!world.scheduler.contains(entry.instance.instanceId())
                && world.scheduler.size() >= budget.maxQueuedWakeupsPerWorld()) {
            stopAndRemove(server, entry, BehaviorTerminationReason.BUDGET_EXHAUSTED);
            return;
        }
        world.scheduler.schedule(entry, dueTick);
    }

    private void requireExecutable(BehaviorTreePlan plan) {
        if (plan.getRootNode() < 0) throw new IllegalArgumentException("Behavior plan has no root");
        for (int nodeIndex = 0; nodeIndex < plan.getNodeCount(); nodeIndex++) {
            NodeCapabilities capabilities = plan.getNodeCapabilities(nodeIndex);
            if (capabilities.context() == NodeCapabilities.Context.BEHAVIOR_EXECUTION
                    && !executors.has(plan.getNodeType(nodeIndex))) {
                throw new IllegalStateException("Missing behavior executor: " + plan.getNodeType(nodeIndex));
            }
        }
    }

    @Nullable
    private static BehaviorTreePlan resolvePlan(String graphId) {
        CompiledGraph graph = currentPlan(graphId);
        return graph instanceof BehaviorTreePlan plan ? plan : null;
    }

    @Nullable
    private static CompiledGraph currentPlan(String graphId) {
        CompiledGraph dynamic = DynamicGraphManager.getArtifact(graphId, GraphKind.BEHAVIOR_TREE);
        return dynamic != null ? dynamic : GraphResourceManager.getInstance()
                .getArtifact(graphId, GraphKind.BEHAVIOR_TREE);
    }

    private ServerState state(MinecraftServer server) {
        return servers.computeIfAbsent(server, ignored -> new ServerState());
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
        private final Map<UUID, InstanceEntry> instances = new HashMap<>();
        private final Map<UUID, UUID> ownerInstances = new HashMap<>();
        private final Map<ResourceKey<Level>, WorldState> worlds = new HashMap<>();
        private long tick = Long.MIN_VALUE;
        private long spentNanos;

        private WorldState world(ResourceKey<Level> levelKey) {
            return worlds.computeIfAbsent(levelKey, ignored -> new WorldState());
        }

        private void beginServerTick(long currentTick) {
            if (tick == currentTick) return;
            tick = currentTick;
            spentNanos = 0L;
        }

        private void clear() {
            instances.clear();
            ownerInstances.clear();
            worlds.values().forEach(world -> world.scheduler.clear());
            worlds.clear();
        }
    }

    private static final class WorldState {
        private final FairScheduler scheduler = new FairScheduler();
    }

    private static final class InstanceEntry {
        private final BehaviorTreeInstance instance;
        private final boolean managedAsset;
        private final ResourceKey<Level> levelKey;

        private InstanceEntry(BehaviorTreeInstance instance, boolean managedAsset) {
            this.instance = instance;
            this.managedAsset = managedAsset;
            ServerLevel level = instance.host().level();
            this.levelKey = level != null ? level.dimension() : Level.OVERWORLD;
        }

        private ResourceKey<Level> levelKey() {
            return levelKey;
        }
    }

    private static final class FairScheduler {
        private static final Comparator<ScheduledEntry> ORDER = Comparator
                .comparingLong((ScheduledEntry value) -> value.dueTick)
                .thenComparingLong(value -> value.sequence);
        private final PriorityQueue<ScheduledEntry> queue = new PriorityQueue<>(ORDER);
        private final Map<UUID, ScheduledEntry> active = new HashMap<>();
        private long sequence;

        private void schedule(InstanceEntry entry, long dueTick) {
            ScheduledEntry scheduled = new ScheduledEntry(entry.instance.instanceId(), entry,
                    dueTick, ++sequence);
            active.put(scheduled.instanceId, scheduled);
            queue.offer(scheduled);
            compactIfNeeded();
        }

        @Nullable
        private ScheduledEntry pollDue(long tick) {
            discardStale();
            ScheduledEntry scheduled = queue.peek();
            if (scheduled == null || scheduled.dueTick > tick) return null;
            queue.poll();
            active.remove(scheduled.instanceId, scheduled);
            return scheduled;
        }

        private boolean cancel(UUID instanceId) {
            boolean changed = active.remove(instanceId) != null;
            if (changed) compactIfNeeded();
            return changed;
        }

        private boolean contains(UUID instanceId) { return active.containsKey(instanceId); }
        private int size() { return active.size(); }
        private boolean isEmpty() { return active.isEmpty(); }

        private void clear() {
            active.clear();
            queue.clear();
        }

        private void discardStale() {
            while (!queue.isEmpty() && active.get(queue.peek().instanceId) != queue.peek()) queue.poll();
        }

        private void compactIfNeeded() {
            if (queue.size() <= Math.max(64, active.size() * 4 + 16)) return;
            queue.clear();
            queue.addAll(active.values());
        }
    }

    private record ScheduledEntry(UUID instanceId, InstanceEntry entry, long dueTick, long sequence) {
    }

    private static final class EntityHost implements BehaviorRuntimeHost {
        private final UUID ownerId;
        private final WeakReference<Mob> owner;

        private EntityHost(Mob owner) {
            this.ownerId = owner.getUUID();
            this.owner = new WeakReference<>(owner);
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
    }
}
